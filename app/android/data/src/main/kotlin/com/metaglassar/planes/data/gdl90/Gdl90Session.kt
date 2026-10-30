package com.metaglassar.planes.data.gdl90

import com.metaglassar.planes.core.Aircraft
import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.core.pilot.AltitudeDatum
import com.metaglassar.planes.core.pilot.NavUnits
import com.metaglassar.planes.core.pilot.Ownship
import com.metaglassar.planes.core.pilot.PositionSource
import com.metaglassar.planes.core.pilot.TrackEstimator

/** Receiver connection, as shown on the pilot screen. */
enum class ReceiverLink {
  /** Nothing received yet, or nothing for [Gdl90Session.LINK_TIMEOUT_MS]. */
  NO_DATA,
  /** Heartbeats arriving. */
  CONNECTED,
}

/** Everything pilot mode needs from the receiver, at one moment. */
data class ReceiverSnapshot(
    val link: ReceiverLink,
    /** Receiver's own GPS has a valid fix and a recent ownship position. */
    val gpsValid: Boolean,
    /** Ownship from the receiver (null without a recent valid position). */
    val ownship: Ownship?,
    /** Traffic in the common model; altitudeM is PRESSURE altitude, as GDL 90 reports it. */
    val traffic: List<Aircraft>,
    /** From the ForeFlight ID message (Stratux sends "Stratux"), if any. */
    val deviceName: String?,
    val goodFrames: Long,
    val badFrames: Long,
) {
  companion object {
    val EMPTY = ReceiverSnapshot(ReceiverLink.NO_DATA, false, null, emptyList(), null, 0, 0)
  }
}

/**
 * Turns raw GDL 90 datagrams into a [ReceiverSnapshot]. Pure and single-threaded: feed it from one
 * place (the UDP receiver or the simulator) and read [snapshot] about once a second.
 */
class Gdl90Session(private val clockMs: () -> Long = System::currentTimeMillis) {
  private val framer = Gdl90Framer()
  private var lastMessageMs: Long? = null
  private var lastHeartbeat: Pair<Gdl90Message.Heartbeat, Long>? = null
  private var lastOwnship: Pair<Gdl90Message.Report, Long>? = null
  private var lastGeoAltitude: Pair<Gdl90Message.OwnshipGeoAltitude, Long>? = null
  private var foreFlightId: Gdl90Message.ForeFlightId? = null
  private val traffic = LinkedHashMap<String, Pair<Gdl90Message.Report, Long>>()

  var unknownMessages = 0L
    private set

  fun reset() {
    lastMessageMs = null
    lastHeartbeat = null
    lastOwnship = null
    lastGeoAltitude = null
    foreFlightId = null
    traffic.clear()
  }

  /** Feeds one UDP datagram (any number of frames). */
  fun ingest(datagram: ByteArray, length: Int = datagram.size) {
    val now = clockMs()
    for (m in framer.feed(datagram, length)) {
      val msg = Gdl90Decoder.decode(m) ?: continue
      lastMessageMs = now
      when (msg) {
        is Gdl90Message.Heartbeat -> lastHeartbeat = msg to now
        is Gdl90Message.Report ->
            if (msg.isOwnship) lastOwnship = msg to now else traffic[msg.id] = msg to now
        is Gdl90Message.OwnshipGeoAltitude -> lastGeoAltitude = msg to now
        is Gdl90Message.ForeFlightId -> foreFlightId = msg
        is Gdl90Message.Other -> unknownMessages++
      }
    }
  }

  fun snapshot(): ReceiverSnapshot {
    val now = clockMs()
    traffic.entries.removeAll { now - it.value.second > TRAFFIC_TIMEOUT_MS }
    val link = if (lastMessageMs?.let { now - it <= LINK_TIMEOUT_MS } == true) ReceiverLink.CONNECTED else ReceiverLink.NO_DATA
    val ownship = ownship(now)
    val heartbeatGps = lastHeartbeat?.takeIf { now - it.second <= LINK_TIMEOUT_MS }?.first?.gpsPositionValid == true
    return ReceiverSnapshot(
        link = link,
        gpsValid = heartbeatGps && ownship != null,
        ownship = ownship,
        traffic = traffic.values.mapNotNull { (r, at) -> r.toAircraft(at) },
        deviceName = foreFlightId?.name?.ifBlank { null },
        goodFrames = framer.goodFrames,
        badFrames = framer.badFrames,
    )
  }

  private fun ownship(now: Long): Ownship? {
    val (r, at) = lastOwnship?.takeIf { now - it.second <= LINK_TIMEOUT_MS } ?: return null
    val lat = r.latDeg ?: return null
    val lon = r.lonDeg ?: return null
    val geo = lastGeoAltitude?.takeIf { now - it.second <= LINK_TIMEOUT_MS }?.first
    val gs = r.horizontalVelocityKt?.toDouble()
    // Only a TRUE track or heading is usable as our track; magnetic heading isn't.
    val trueDirection = r.trackDeg?.takeIf { r.trackType == 1 || r.trackType == 3 }
    return Ownship(
        position = GeoPoint(lat, lon),
        groundspeedKt = gs,
        trackDeg = trueDirection?.takeIf { (gs ?: 0.0) >= TrackEstimator.MIN_TRACK_SPEED_KT },
        gpsAltitudeFt = geo?.altitudeFt?.toDouble(),
        gpsAltitudeDatum = if (foreFlightId?.geoAltitudeIsMsl == true) AltitudeDatum.MSL else AltitudeDatum.ELLIPSOID,
        // Stratux fills this with GPS MSL altitude when it has no pressure sensor; still the best
        // reference to compare with traffic pressure altitude. Never displayed as an altimeter.
        pressureAltitudeFt = r.pressureAltitudeFt?.toDouble(),
        verticalRateFpm = r.verticalVelocityFpm?.toDouble(),
        icaoAddress = r.id,
        nic = r.nic,
        nacp = r.nacp,
        source = PositionSource.RECEIVER,
        timeMs = at,
    )
  }

  companion object {
    const val LINK_TIMEOUT_MS = 3_000L
    /** Traffic not updated for this long is dropped (the GDL 90 itself extrapolates briefly). */
    const val TRAFFIC_TIMEOUT_MS = 10_000L
  }
}

/** Traffic report → common Aircraft model. Null without a valid position (unknown, not 0,0). */
fun Gdl90Message.Report.toAircraft(receivedAtMs: Long): Aircraft? {
  val lat = latDeg ?: return null
  val lon = lonDeg ?: return null
  return Aircraft(
      icao24 = id,
      callsign = callsign,
      position = GeoPoint(lat, lon),
      altitudeM = pressureAltitudeFt?.let { NavUnits.feetToMetres(it.toDouble()) },
      speedMs = horizontalVelocityKt?.let { NavUnits.ms(it.toDouble()) },
      trackDeg = trackDeg,
      verticalRateMs = verticalVelocityFpm?.let { it / NavUnits.FPM_PER_MS },
      onGround = !airborne,
      lastSeenEpochSec = receivedAtMs / 1000,
  )
}
