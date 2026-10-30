package com.metaglassar.planes.data.gdl90

import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/*
 * GDL 90 decoding and encoding, verified against:
 *  - Garmin "GDL 90 Data Interface Specification", 560-1058-00 Rev A (June 5, 2007), cited below as
 *    "GDL90 §x.y". Public copy: https://www.faa.gov/sites/faa.gov/files/air_traffic/technology/adsb/archival/GDL90_Public_ICD_RevA.PDF
 *  - ForeFlight "GDL 90 Extended Specification" (ID message 0x65): https://www.foreflight.com/connect/spec/
 * Used by Stratux and Stratus 4 over Wi-Fi UDP, port 4000.
 */

object Gdl90 {
  const val DEFAULT_PORT = 4000
  const val FLAG: Int = 0x7E
  const val ESCAPE: Int = 0x7D

  // Message IDs (GDL90 §3, Table 2; IDs are decimal in the spec).
  const val ID_HEARTBEAT = 0
  const val ID_OWNSHIP = 10
  const val ID_OWNSHIP_GEO_ALTITUDE = 11
  const val ID_TRAFFIC = 20
  /** ForeFlight extension. */
  const val ID_FOREFLIGHT = 0x65

  /** Latitude/longitude resolution: 180 / 2^23 degrees (GDL90 §3.5.1.3). */
  const val LAT_LON_RESOLUTION = 180.0 / (1 shl 23)

  // region CRC (GDL90 §2.2.3: CRC-CCITT, polynomial 0x1021, initial value 0, table-driven)

  private val crcTable =
      IntArray(256) { i ->
        var crc = i shl 8
        repeat(8) { crc = ((crc shl 1) xor (if (crc and 0x8000 != 0) 0x1021 else 0)) and 0xFFFF }
        crc
      }

  /** FCS over [bytes] (message ID + data, after removing byte-stuffing). */
  fun crc(bytes: ByteArray, length: Int = bytes.size): Int {
    var crc = 0
    for (i in 0 until length) {
      crc = (crcTable[crc ushr 8] xor (crc shl 8) xor (bytes[i].toInt() and 0xFF)) and 0xFFFF
    }
    return crc
  }

  // endregion

  /**
   * Wraps a clear message (ID + data) for sending: appends the FCS least-significant byte first,
   * byte-stuffs 0x7D/0x7E as 0x7D, byte XOR 0x20, and adds the flags (GDL90 §2.2.1).
   */
  fun frame(message: ByteArray): ByteArray {
    val crc = crc(message)
    val clear = message + byteArrayOf((crc and 0xFF).toByte(), (crc ushr 8).toByte())
    val out = ByteArrayOutputStream(clear.size + 8)
    out.write(FLAG)
    for (b in clear) {
      val v = b.toInt() and 0xFF
      if (v == FLAG || v == ESCAPE) {
        out.write(ESCAPE)
        out.write(v xor 0x20)
      } else {
        out.write(v)
      }
    }
    out.write(FLAG)
    return out.toByteArray()
  }
}

/**
 * Splits a byte stream into GDL 90 messages (GDL90 §2.2.1 "steps for message reception"): collect
 * bytes between flags, undo byte-stuffing, check the FCS and drop the frame if it doesn't match.
 * Keeps state between calls, so a frame split across UDP datagrams still decodes.
 */
class Gdl90Framer {
  private val buffer = ByteArrayOutputStream(64)
  private var inFrame = false
  private var escape = false

  var goodFrames = 0L
    private set
  var badFrames = 0L
    private set

  /** Returns the clear messages (message ID + data, without FCS) that completed in [bytes]. */
  fun feed(bytes: ByteArray, length: Int = bytes.size): List<ByteArray> {
    val out = mutableListOf<ByteArray>()
    for (i in 0 until length) {
      val b = bytes[i].toInt() and 0xFF
      when {
        b == Gdl90.FLAG -> {
          if (inFrame && buffer.size() > 0) finish()?.let(out::add)
          buffer.reset()
          escape = false
          inFrame = true
        }
        !inFrame -> Unit
        escape -> {
          buffer.write(b xor 0x20)
          escape = false
        }
        b == Gdl90.ESCAPE -> escape = true
        else -> buffer.write(b)
      }
      if (buffer.size() > MAX_FRAME) { // garbage: wait for the next flag
        buffer.reset()
        inFrame = false
      }
    }
    return out
  }

  private fun finish(): ByteArray? {
    val f = buffer.toByteArray()
    if (f.size < 3) {
      badFrames++
      return null
    }
    val n = f.size - 2
    val received = (f[n].toInt() and 0xFF) or ((f[n + 1].toInt() and 0xFF) shl 8) // LSB first
    if (Gdl90.crc(f, n) != received) {
      badFrames++
      return null
    }
    goodFrames++
    return f.copyOf(n)
  }

  private companion object {
    /** Longest message is Uplink Data, 436 bytes (GDL90 §3.3). */
    const val MAX_FRAME = 600
  }
}

/** A decoded GDL 90 message. Unavailable values are null, never 0. */
sealed interface Gdl90Message {
  /** GDL90 §3.1. */
  data class Heartbeat(
      val gpsPositionValid: Boolean,
      val maintenanceRequired: Boolean,
      val gpsBatteryLow: Boolean,
      val uatInitialized: Boolean,
      val utcOk: Boolean,
      /** Seconds since 0000Z (17 bits). */
      val timestampSec: Int,
      val uplinkCount: Int,
      val basicLongCount: Int,
  ) : Gdl90Message

  /**
   * Ownship (ID 10, GDL90 §3.4) and Traffic (ID 20, §3.5) reports share one layout (§3.5.1).
   *
   * @property addressType 0 ADS-B ICAO, 1 ADS-B self-assigned, 2 TIS-B ICAO, 3 TIS-B track file, 4 surface vehicle, 5 ground beacon.
   * @property latDeg Null when there is no valid position (latitude, longitude and NIC all zero, §3.5.1.3).
   * @property pressureAltitudeFt Pressure altitude (29.92 inHg); null when 0xFFF (§3.5.1.4).
   * @property trackType 0 not valid, 1 true track, 2 magnetic heading, 3 true heading (§3.5.1.5).
   */
  data class Report(
      val isOwnship: Boolean,
      val trafficAlert: Boolean,
      val addressType: Int,
      val address: Int,
      val latDeg: Double?,
      val lonDeg: Double?,
      val pressureAltitudeFt: Int?,
      val airborne: Boolean,
      val extrapolated: Boolean,
      val trackType: Int,
      val nic: Int,
      val nacp: Int,
      val horizontalVelocityKt: Int?,
      val verticalVelocityFpm: Int?,
      val trackDeg: Double?,
      val emitterCategory: Int,
      val callsign: String?,
      val emergencyCode: Int,
  ) : Gdl90Message {
    /** "ab4549"; non-ICAO addresses get a prefix so they can never match an ICAO address. */
    val id: String
      get() {
        val hex = address.toString(16).padStart(6, '0')
        return if (addressType == 0 || addressType == 2) hex else "t$addressType-$hex"
      }
  }

  /**
   * GDL90 §3.8: geometric altitude, height above the WGS-84 ellipsoid unless the ForeFlight ID
   * message says MSL. [vfomM] is null when not available (0x7FFF).
   */
  data class OwnshipGeoAltitude(val altitudeFt: Int, val verticalWarning: Boolean, val vfomM: Int?) : Gdl90Message

  /** ForeFlight ID message (0x65, sub-ID 0). */
  data class ForeFlightId(
      val version: Int,
      val serial: String?,
      val name: String,
      val longName: String,
      /** Capabilities bit 0: geometric altitude datum is MSL (else WGS-84 ellipsoid). */
      val geoAltitudeIsMsl: Boolean,
  ) : Gdl90Message

  /** Any other message ID (uplink weather, Stratux status, AHRS…): recognised and ignored. */
  data class Other(val id: Int, val length: Int) : Gdl90Message
}

object Gdl90Decoder {
  const val HEARTBEAT_LENGTH = 7
  const val REPORT_LENGTH = 28
  const val GEO_ALTITUDE_LENGTH = 5
  const val FOREFLIGHT_ID_LENGTH = 39

  /** Decodes one clear message (from [Gdl90Framer]). Returns null for a malformed known message. */
  fun decode(m: ByteArray): Gdl90Message? {
    if (m.isEmpty()) return null
    val id = u8(m, 0)
    return when (id) {
      Gdl90.ID_HEARTBEAT -> if (m.size >= HEARTBEAT_LENGTH) heartbeat(m) else null
      Gdl90.ID_OWNSHIP, Gdl90.ID_TRAFFIC -> if (m.size >= REPORT_LENGTH) report(m, id == Gdl90.ID_OWNSHIP) else null
      Gdl90.ID_OWNSHIP_GEO_ALTITUDE -> if (m.size >= GEO_ALTITUDE_LENGTH) geoAltitude(m) else null
      Gdl90.ID_FOREFLIGHT ->
          if (m.size >= 2 && u8(m, 1) == 0) { if (m.size >= FOREFLIGHT_ID_LENGTH) foreFlightId(m) else null }
          else Gdl90Message.Other(id, m.size)
      else -> Gdl90Message.Other(id, m.size)
    }
  }

  /** GDL90 §3.1, Table 3. */
  private fun heartbeat(m: ByteArray): Gdl90Message.Heartbeat {
    val s1 = u8(m, 1)
    val s2 = u8(m, 2)
    // Time stamp: bit 16 in status byte 2 bit 7; bits 15-0 LS byte first (§3.1.3).
    val ts = ((s2 and 0x80) shl 9) or (u8(m, 4) shl 8) or u8(m, 3)
    // Message counts (§3.1.4): uplinks in bits 7..3 of byte 6; basic+long in 10 bits.
    val c1 = u8(m, 5)
    val c2 = u8(m, 6)
    return Gdl90Message.Heartbeat(
        gpsPositionValid = s1 and 0x80 != 0,
        maintenanceRequired = s1 and 0x40 != 0,
        gpsBatteryLow = s1 and 0x08 != 0,
        uatInitialized = s1 and 0x01 != 0,
        utcOk = s2 and 0x01 != 0,
        timestampSec = ts,
        uplinkCount = c1 ushr 3,
        basicLongCount = ((c1 and 0x03) shl 8) or c2,
    )
  }

  /** GDL90 §3.5.1, Figure 2: st aa aa aa ll ll ll nn nn nn dd dm ia hh hv vv tt ee cc×8 px. */
  private fun report(m: ByteArray, ownship: Boolean): Gdl90Message.Report {
    val st = u8(m, 1)
    val address = (u8(m, 2) shl 16) or (u8(m, 3) shl 8) or u8(m, 4)
    val latRaw = s24(m, 5)
    val lonRaw = s24(m, 8)
    val altRaw = (u8(m, 11) shl 4) or (u8(m, 12) ushr 4)
    val misc = u8(m, 12) and 0x0F
    val nic = u8(m, 13) ushr 4
    val nacp = u8(m, 13) and 0x0F
    val hvel = (u8(m, 14) shl 4) or (u8(m, 15) ushr 4)
    val vvelRaw = ((u8(m, 15) and 0x0F) shl 8) or u8(m, 16)
    val tt = u8(m, 17)
    val trackType = misc and 0x03
    val noPosition = latRaw == 0 && lonRaw == 0 && nic == 0 // §3.4, §3.5.1.3
    return Gdl90Message.Report(
        isOwnship = ownship,
        trafficAlert = (st ushr 4) == 1,
        addressType = st and 0x0F,
        address = address,
        latDeg = if (noPosition) null else latRaw * Gdl90.LAT_LON_RESOLUTION,
        lonDeg = if (noPosition) null else lonRaw * Gdl90.LAT_LON_RESOLUTION,
        // §3.5.1.4: 25 ft resolution, offset −1,000 ft; 0xFFF = invalid.
        pressureAltitudeFt = if (altRaw == 0xFFF) null else altRaw * 25 - 1_000,
        airborne = misc and 0x08 != 0,
        extrapolated = misc and 0x04 != 0,
        trackType = trackType,
        nic = nic,
        nacp = nacp,
        // §3.5.1.7: 1 kt; 0xFFF = no horizontal velocity.
        horizontalVelocityKt = if (hvel == 0xFFF) null else hvel,
        verticalVelocityFpm = verticalVelocity(vvelRaw),
        // §3.5.1.9: 360/256°; meaningless when the misc field says "tt not valid".
        trackDeg = if (trackType == 0) null else tt * 360.0 / 256.0,
        emitterCategory = u8(m, 18),
        callsign = String(m, 19, 8, Charsets.US_ASCII).trim().ifEmpty { null },
        emergencyCode = u8(m, 27) ushr 4,
    )
  }

  /** §3.5.1.8: 12-bit signed, 64 fpm units; 0x800 = unavailable; 0x1FF..0x7FF and 0x801..0xE01 unused. */
  private fun verticalVelocity(raw: Int): Int? {
    if (raw == 0x800 || raw in 0x1FF..0x7FF || raw in 0x801..0xE01) return null
    val signed = if (raw and 0x800 != 0) raw - 0x1000 else raw
    return signed * 64
  }

  /** GDL90 §3.8, Table 16. */
  private fun geoAltitude(m: ByteArray): Gdl90Message.OwnshipGeoAltitude {
    val alt = ((u8(m, 1) shl 8) or u8(m, 2)).toShort().toInt() * 5
    val metrics = (u8(m, 3) shl 8) or u8(m, 4)
    val vfom = metrics and 0x7FFF
    return Gdl90Message.OwnshipGeoAltitude(
        altitudeFt = alt,
        verticalWarning = metrics and 0x8000 != 0,
        vfomM = if (vfom == 0x7FFF) null else vfom,
    )
  }

  /** ForeFlight extended spec: ID message, big-endian. */
  private fun foreFlightId(m: ByteArray): Gdl90Message.ForeFlightId {
    val serialBytes = m.copyOfRange(3, 11)
    val serial = if (serialBytes.all { it == 0xFF.toByte() }) null else serialBytes.joinToString("") { "%02x".format(it) }
    val caps = (u8(m, 35) shl 24) or (u8(m, 36) shl 16) or (u8(m, 37) shl 8) or u8(m, 38)
    return Gdl90Message.ForeFlightId(
        version = u8(m, 2),
        serial = serial,
        name = text(m, 11, 8),
        longName = text(m, 19, 16),
        geoAltitudeIsMsl = caps and 0x01 != 0,
    )
  }

  private fun text(m: ByteArray, from: Int, len: Int) = String(m, from, len, Charsets.UTF_8).trimEnd('\u0000', ' ')

  private fun u8(m: ByteArray, i: Int) = m[i].toInt() and 0xFF

  private fun s24(m: ByteArray, i: Int): Int {
    val v = (u8(m, i) shl 16) or (u8(m, i + 1) shl 8) or u8(m, i + 2)
    return if (v and 0x800000 != 0) v - 0x1000000 else v
  }
}

/** Builds GDL 90 messages (clear, unframed). Used by the simulated receiver and tests; mirror of [Gdl90Decoder]. */
object Gdl90Encoder {
  fun heartbeat(gpsValid: Boolean, timestampSec: Int, utcOk: Boolean = true): ByteArray {
    val s1 = (if (gpsValid) 0x80 else 0) or 0x01 // UAT initialized is always set (§3.1.1 h)
    val s2 = (((timestampSec ushr 16) and 0x01) shl 7) or (if (utcOk) 0x01 else 0)
    return bytes(Gdl90.ID_HEARTBEAT, s1, s2, timestampSec and 0xFF, (timestampSec ushr 8) and 0xFF, 0, 0)
  }

  fun report(
      ownship: Boolean,
      address: Int,
      latDeg: Double?,
      lonDeg: Double?,
      pressureAltitudeFt: Double?,
      airborne: Boolean = true,
      trackDeg: Double? = null,
      horizontalVelocityKt: Double? = null,
      verticalVelocityFpm: Double? = null,
      callsign: String = "",
      addressType: Int = 0,
      nic: Int = 9,
      nacp: Int = 9,
      emitterCategory: Int = 1,
      trafficAlert: Boolean = false,
  ): ByteArray {
    val hasPos = latDeg != null && lonDeg != null
    val lat = if (hasPos) (latDeg!! / Gdl90.LAT_LON_RESOLUTION).roundToInt() and 0xFFFFFF else 0
    val lon = if (hasPos) (lonDeg!! / Gdl90.LAT_LON_RESOLUTION).roundToInt() and 0xFFFFFF else 0
    val alt = pressureAltitudeFt?.let { ((it + 1_000) / 25).roundToInt().coerceIn(0, 0xFFE) } ?: 0xFFF
    val trackType = if (trackDeg != null) 1 else 0
    val misc = (if (airborne) 0x08 else 0) or trackType
    val hvel = horizontalVelocityKt?.roundToInt()?.coerceIn(0, 0xFFE) ?: 0xFFF
    val vvel = verticalVelocityFpm?.let { (it / 64).roundToInt().coerceIn(-0x1FE, 0x1FE) and 0xFFF } ?: 0x800
    val tt = trackDeg?.let { (it * 256 / 360).roundToInt() and 0xFF } ?: 0
    val cs = callsign.uppercase().padEnd(8).take(8).toByteArray(Charsets.US_ASCII)
    val m = ByteArray(Gdl90Decoder.REPORT_LENGTH)
    m[0] = (if (ownship) Gdl90.ID_OWNSHIP else Gdl90.ID_TRAFFIC).toByte()
    m[1] = (((if (trafficAlert) 1 else 0) shl 4) or (addressType and 0x0F)).toByte()
    put24(m, 2, address)
    put24(m, 5, lat)
    put24(m, 8, lon)
    m[11] = (alt ushr 4).toByte()
    m[12] = (((alt and 0x0F) shl 4) or misc).toByte()
    m[13] = (((if (hasPos) nic else 0) shl 4) or nacp).toByte()
    m[14] = (hvel ushr 4).toByte()
    m[15] = (((hvel and 0x0F) shl 4) or (vvel ushr 8)).toByte()
    m[16] = (vvel and 0xFF).toByte()
    m[17] = tt.toByte()
    m[18] = emitterCategory.toByte()
    cs.copyInto(m, 19)
    m[27] = 0
    return m
  }

  fun geoAltitude(altitudeFt: Double, vfomM: Int? = 10): ByteArray {
    val alt = (altitudeFt / 5).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()) and 0xFFFF
    val metrics = vfomM?.coerceIn(0, 0x7FFE) ?: 0x7FFF
    return bytes(Gdl90.ID_OWNSHIP_GEO_ALTITUDE, alt ushr 8, alt and 0xFF, metrics ushr 8, metrics and 0xFF)
  }

  fun foreFlightId(name: String, longName: String, geoAltitudeIsMsl: Boolean): ByteArray {
    val m = ByteArray(Gdl90Decoder.FOREFLIGHT_ID_LENGTH)
    m[0] = Gdl90.ID_FOREFLIGHT.toByte()
    m[1] = 0
    m[2] = 1
    for (i in 3..10) m[i] = 0xFF.toByte() // serial: invalid
    name.toByteArray(Charsets.UTF_8).take(8).toByteArray().copyInto(m, 11)
    longName.toByteArray(Charsets.UTF_8).take(16).toByteArray().copyInto(m, 19)
    m[38] = if (geoAltitudeIsMsl) 1 else 0
    return m
  }

  private fun put24(m: ByteArray, i: Int, v: Int) {
    m[i] = (v ushr 16).toByte()
    m[i + 1] = (v ushr 8).toByte()
    m[i + 2] = v.toByte()
  }

  private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
}
