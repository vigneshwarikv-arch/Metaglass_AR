package com.metaglassar.planes.data.gdl90

import com.metaglassar.planes.core.pilot.AltitudeDatum
import com.metaglassar.planes.core.pilot.NavUnits
import com.metaglassar.planes.core.pilot.TrafficAdvisor
import com.metaglassar.planes.core.pilot.TrafficRules
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Same cases as app/ios/PlanesKit/Tests/PlanesDataTests/Gdl90Tests.swift — keep them in sync.

private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

/** GDL90 §2.2.4: a complete Heartbeat frame including flags and CRC. */
private val SPEC_HEARTBEAT_FRAME = bytes(0x7E, 0x00, 0x81, 0x41, 0xDB, 0xD0, 0x08, 0x02, 0xB3, 0x8B, 0x7E)

/** GDL90 §3.5.2, Table 12: Traffic Report for N825V over Salem, OR (message ID + 27 bytes, no CRC). */
private val SPEC_TRAFFIC_REPORT =
    bytes(
        0x14, 0x00, 0xAB, 0x45, 0x49, 0x1F, 0xEF, 0x15, 0xA8, 0x89, 0x78, 0x0F, 0x09, 0xA9,
        0x07, 0xB0, 0x01, 0x20, 0x01, 0x4E, 0x38, 0x32, 0x35, 0x56, 0x20, 0x20, 0x20, 0x00)

class Gdl90FramingTest {
  @Test
  fun `CRC matches the spec heartbeat example`() {
    val clear = SPEC_HEARTBEAT_FRAME.copyOfRange(1, 8)
    assertEquals(0x8BB3, Gdl90.crc(clear)) // sent LSB first: B3 8B
    assertArrayEquals(SPEC_HEARTBEAT_FRAME, Gdl90.frame(clear))
  }

  @Test
  fun `decodes the spec heartbeat`() {
    val msgs = Gdl90Framer().feed(SPEC_HEARTBEAT_FRAME)
    val hb = Gdl90Decoder.decode(msgs.single()) as Gdl90Message.Heartbeat
    assertTrue(hb.gpsPositionValid)
    assertTrue(hb.uatInitialized)
    assertTrue(hb.utcOk)
    assertFalse(hb.maintenanceRequired)
    assertEquals(0xD0DB, hb.timestampSec)
    assertEquals(1, hb.uplinkCount)
    assertEquals(2, hb.basicLongCount)
  }

  @Test
  fun `bad CRC is dropped`() {
    val corrupt = SPEC_HEARTBEAT_FRAME.copyOf().also { it[3] = 0x42 }
    val framer = Gdl90Framer()
    assertTrue(framer.feed(corrupt).isEmpty())
    assertEquals(1L, framer.badFrames)
    assertEquals(0L, framer.goodFrames)
  }

  @Test
  fun `byte stuffing round trip, as in the spec examples`() {
    // §2.2.1: ID 2 with second byte 0x7E is sent as 7E 02 7D 5E …
    val framed = Gdl90.frame(bytes(0x02, 0x7E, 0x7D, 0x11))
    assertArrayEquals(bytes(0x7E, 0x02, 0x7D, 0x5E, 0x7D, 0x5D, 0x11), framed.copyOfRange(0, 7))
    assertArrayEquals(bytes(0x02, 0x7E, 0x7D, 0x11), Gdl90Framer().feed(framed).single())
  }

  @Test
  fun `back-to-back frames, junk before the first flag, and a frame split across datagrams`() {
    val a = SPEC_HEARTBEAT_FRAME
    val b = Gdl90.frame(SPEC_TRAFFIC_REPORT)
    val framer = Gdl90Framer()
    assertEquals(2, framer.feed(bytes(0x01, 0x02) + a + b).size)
    assertTrue(framer.feed(b.copyOfRange(0, 10)).isEmpty())
    assertEquals(1, framer.feed(b.copyOfRange(10, b.size)).size)
  }
}

class Gdl90DecoderTest {
  @Test
  fun `decodes the spec traffic report`() {
    val r = Gdl90Decoder.decode(SPEC_TRAFFIC_REPORT) as Gdl90Message.Report
    assertFalse(r.isOwnship)
    assertFalse(r.trafficAlert)
    assertEquals(0, r.addressType)
    assertEquals("ab4549", r.id) // 52642511 octal
    assertEquals(44.90708, r.latDeg!!, 1e-4)
    assertEquals(-122.99488, r.lonDeg!!, 1e-4)
    assertEquals(5_000, r.pressureAltitudeFt)
    assertTrue(r.airborne)
    assertFalse(r.extrapolated)
    assertEquals(1, r.trackType) // true track
    assertEquals(10, r.nic)
    assertEquals(9, r.nacp)
    assertEquals(123, r.horizontalVelocityKt)
    assertEquals(64, r.verticalVelocityFpm)
    assertEquals(45.0, r.trackDeg!!, 1e-9)
    assertEquals(1, r.emitterCategory)
    assertEquals("N825V", r.callsign)
    assertEquals(0, r.emergencyCode)
  }

  @Test
  fun `encoder reproduces the spec bytes`() {
    val r = Gdl90Decoder.decode(SPEC_TRAFFIC_REPORT) as Gdl90Message.Report
    val encoded =
        Gdl90Encoder.report(
            ownship = false, address = r.address, latDeg = r.latDeg, lonDeg = r.lonDeg, pressureAltitudeFt = 5_000.0,
            trackDeg = 45.0, horizontalVelocityKt = 123.0, verticalVelocityFpm = 64.0, callsign = "N825V", nic = 10, nacp = 9)
    assertArrayEquals(SPEC_TRAFFIC_REPORT, encoded)
  }

  @Test
  fun `unavailable values are null, not zero`() {
    val m = SPEC_TRAFFIC_REPORT.copyOf()
    // No position: latitude, longitude and NIC all zero (§3.5.1.3).
    for (i in 5..10) m[i] = 0
    m[13] = 0x09 // NIC 0, NACp 9
    // Altitude 0xFFF (§3.5.1.4), misc = 0 (track not valid, on ground).
    m[11] = 0xFF.toByte()
    m[12] = 0xF0.toByte()
    // Horizontal velocity 0xFFF, vertical velocity 0x800 (§3.5.1.7-8).
    m[14] = 0xFF.toByte()
    m[15] = 0xF8.toByte()
    m[16] = 0x00
    val r = Gdl90Decoder.decode(m) as Gdl90Message.Report
    assertNull(r.latDeg)
    assertNull(r.lonDeg)
    assertNull(r.pressureAltitudeFt)
    assertNull(r.horizontalVelocityKt)
    assertNull(r.verticalVelocityFpm)
    assertNull(r.trackDeg)
    assertFalse(r.airborne)
    assertNull("traffic without a position is unknown, not at 0,0", r.toAircraft(0))
  }

  @Test
  fun `vertical velocity scaling and special values`() {
    fun vv(raw: Int): Int? {
      val m = SPEC_TRAFFIC_REPORT.copyOf()
      m[15] = ((m[15].toInt() and 0xF0) or (raw ushr 8)).toByte()
      m[16] = (raw and 0xFF).toByte()
      return (Gdl90Decoder.decode(m) as Gdl90Message.Report).verticalVelocityFpm
    }
    assertEquals(0, vv(0x000))
    assertEquals(-64, vv(0xFFF))
    assertEquals(32_576, vv(0x1FD))
    assertEquals(-32_576, vv(0xE03))
    assertNull(vv(0x800))
    assertNull(vv(0x400)) // unused range
  }

  @Test
  fun `ownship geometric altitude`() {
    val m = Gdl90Decoder.decode(bytes(0x0B, 0xFF, 0x38, 0x7F, 0xFF)) as Gdl90Message.OwnshipGeoAltitude
    assertEquals(-1_000, m.altitudeFt)
    assertNull(m.vfomM)
    assertFalse(m.verticalWarning)
    val m2 = Gdl90Decoder.decode(bytes(0x0B, 0x00, 0xC8, 0x80, 0x0A)) as Gdl90Message.OwnshipGeoAltitude
    assertEquals(1_000, m2.altitudeFt)
    assertEquals(10, m2.vfomM)
    assertTrue(m2.verticalWarning)
  }

  @Test
  fun `ForeFlight ID message`() {
    val id = Gdl90Decoder.decode(Gdl90Encoder.foreFlightId("Stratux", "Stratux v1.6", geoAltitudeIsMsl = false)) as Gdl90Message.ForeFlightId
    assertEquals(1, id.version)
    assertNull(id.serial)
    assertEquals("Stratux", id.name)
    assertEquals("Stratux v1.6", id.longName)
    assertFalse(id.geoAltitudeIsMsl)
  }

  @Test
  fun `unknown and short messages are ignored safely`() {
    assertTrue(Gdl90Decoder.decode(bytes(0xCC, 1, 2, 3)) is Gdl90Message.Other) // Stratux status
    assertTrue(Gdl90Decoder.decode(bytes(0x07) + ByteArray(435)) is Gdl90Message.Other) // uplink (weather)
    assertNull(Gdl90Decoder.decode(bytes(0x14, 0x00, 0xAB))) // truncated traffic report
    assertNull(Gdl90Decoder.decode(ByteArray(0)))
  }
}

class Gdl90SessionTest {
  private var now = 1_000_000L
  private val session = Gdl90Session { now }

  private fun send(vararg messages: ByteArray) = session.ingest(messages.map(Gdl90::frame).reduce { a, b -> a + b })

  @Test
  fun `ownship, traffic and receiver status`() {
    assertEquals(ReceiverLink.NO_DATA, session.snapshot().link)
    send(
        Gdl90Encoder.heartbeat(gpsValid = true, timestampSec = 3600),
        Gdl90Encoder.foreFlightId("Stratux", "Stratux", geoAltitudeIsMsl = false),
        Gdl90Encoder.report(ownship = true, address = 0xA0B1C2, latDeg = 37.0, lonDeg = -122.0, pressureAltitudeFt = 3_500.0, trackDeg = 90.0, horizontalVelocityKt = 100.0),
        Gdl90Encoder.geoAltitude(3_400.0),
        Gdl90Encoder.report(ownship = false, address = 0xAB4549, latDeg = 37.05, lonDeg = -122.0, pressureAltitudeFt = 4_000.0, trackDeg = 180.0, horizontalVelocityKt = 90.0))
    val s = session.snapshot()
    assertEquals(ReceiverLink.CONNECTED, s.link)
    assertTrue(s.gpsValid)
    assertEquals("Stratux", s.deviceName)
    val own = s.ownship!!
    assertEquals("a0b1c2", own.icaoAddress)
    assertEquals(100.0, own.groundspeedKt!!, 1e-9)
    assertEquals(90.0, own.trackDeg!!, 1.0)
    assertEquals(3_400.0, own.gpsAltitudeFt!!, 5.0)
    assertEquals(AltitudeDatum.ELLIPSOID, own.gpsAltitudeDatum)
    val t = s.traffic.single()
    assertEquals("ab4549", t.icao24)
    assertEquals(NavUnits.feetToMetres(4_000.0), t.altitudeM!!, 0.01) // pressure altitude
    assertEquals(500.0, TrafficRules.target(own, t).relativeAltitudeFt!!, 1.0)

    now += 4_000
    val later = session.snapshot()
    assertEquals(ReceiverLink.NO_DATA, later.link)
    assertNull(later.ownship)
    assertEquals(1, later.traffic.size) // traffic kept a little longer…
    now += 7_000
    assertTrue(session.snapshot().traffic.isEmpty()) // …then dropped
  }

  @Test
  fun `no GPS fix on the receiver`() {
    send(
        Gdl90Encoder.heartbeat(gpsValid = false, timestampSec = 0),
        Gdl90Encoder.report(ownship = true, address = 1, latDeg = null, lonDeg = null, pressureAltitudeFt = null))
    val s = session.snapshot()
    assertEquals(ReceiverLink.CONNECTED, s.link)
    assertFalse(s.gpsValid)
    assertNull(s.ownship)
  }
}

class Gdl90ScenarioTest {
  private val scenarios = Gdl90Scenario.loadAll()

  @Test
  fun `bundled scenarios load`() {
    assertEquals(listOf("converging", "overtaking", "pattern"), scenarios.map { it.id })
  }

  /**
   * The apps' simulator and tools/gdl90-replay must send identical bytes. These are the messages
   * (ForeFlight ID left out: its device name differs) that `gdl90_replay.py` produces for
   * "converging" at t = 10 s, heartbeat time 3600.
   */
  @Test
  fun `scenario bytes match the replay tool`() {
    val expected = listOf(
        "008101100e0000",
        "0a00a0b1c21a9017a950410b4999064000eb014e3132334142202000",
        "0b02a8000a",
        "1400a44e2b1a9b23a9582c0c8999071000be014e343432314b202000",
        "1400a2f9c01a9067a938760849990850000e014e3838535220202000",
        "1402a0b1c31a9017a950410b4999064000eb01202020202020202000")
    val framer = Gdl90Framer()
    val got = framer.feed(scenarios.first { it.id == "converging" }.frames(10.0, 3600))
        .filter { (it[0].toInt() and 0xFF) != Gdl90.ID_FOREFLIGHT }
        .map { m -> m.joinToString("") { "%02x".format(it) } }
    assertEquals(expected, got)
  }

  /** Plays a whole scenario through the real decoder, session and callout rules. */
  private fun callouts(id: String): List<String> {
    val scenario = scenarios.first { it.id == id }
    var now = 0L
    val session = Gdl90Session { now }
    val advisor = TrafficAdvisor()
    val out = mutableListOf<String>()
    for (t in 0 until scenario.durationSec) {
      now = t * 1_000L
      session.ingest(scenario.frames(t.toDouble(), t))
      val s = session.snapshot()
      val callout = advisor.next(s.ownship!!, s.traffic, now) ?: continue
      assertTrue("ownship's TIS-B shadow must never be called out", callout.target.aircraft.icao24 != "a0b1c3")
      out += "${callout.target.aircraft.icao24}: ${callout.spoken}"
    }
    return out
  }

  @Test
  fun `converging scenario calls traffic at 2 o'clock, then again as it closes`() {
    val c = callouts("converging")
    assertTrue(c.first(), c.first().startsWith("a44e2b: Traffic, 2 o'clock, ") && c.first().contains("500 above"))
    assertTrue(c.count { it.startsWith("a44e2b") } >= 2)
  }

  @Test
  fun `overtaking scenario calls traffic from behind`() {
    val c = callouts("overtaking")
    assertTrue(c.first(), c.first().startsWith("a7c3d1: Traffic, 6 o'clock"))
  }

  @Test
  fun `pattern scenario never calls the taxiing aircraft`() {
    val c = callouts("pattern")
    assertTrue(c.isNotEmpty())
    assertTrue(c.none { it.startsWith("a4444d") })
  }
}

class Gdl90UdpReceiverTest {
  @Test
  fun `receives a datagram on the port`() = runBlocking {
    val port = DatagramSocket(0).use { it.localPort } // a free port
    val receiver = Gdl90UdpReceiver(port)
    withTimeout(10_000) {
      val got = async { receiver.datagrams().first() }
      val sender = launch {
        DatagramSocket().use { s ->
          while (true) {
            s.send(DatagramPacket(SPEC_HEARTBEAT_FRAME, SPEC_HEARTBEAT_FRAME.size, InetAddress.getLoopbackAddress(), port))
            delay(200)
          }
        }
      }
      assertArrayEquals(SPEC_HEARTBEAT_FRAME, got.await())
      sender.cancel()
    }
    assertNotNull(receiver.name)
  }
}
