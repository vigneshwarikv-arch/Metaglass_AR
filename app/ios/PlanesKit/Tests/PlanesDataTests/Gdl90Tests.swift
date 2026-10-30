import Network
import PlanesCore
import XCTest
@testable import PlanesData

// Same cases as app/android/data/src/test/kotlin/.../gdl90/Gdl90Test.kt — keep them in sync.

/// GDL90 §2.2.4: a complete Heartbeat frame including flags and CRC.
private let specHeartbeatFrame: [UInt8] = [0x7E, 0x00, 0x81, 0x41, 0xDB, 0xD0, 0x08, 0x02, 0xB3, 0x8B, 0x7E]

/// GDL90 §3.5.2, Table 12: Traffic Report for N825V over Salem, OR (message ID + 27 bytes, no CRC).
private let specTrafficReport: [UInt8] = [
  0x14, 0x00, 0xAB, 0x45, 0x49, 0x1F, 0xEF, 0x15, 0xA8, 0x89, 0x78, 0x0F, 0x09, 0xA9,
  0x07, 0xB0, 0x01, 0x20, 0x01, 0x4E, 0x38, 0x32, 0x35, 0x56, 0x20, 0x20, 0x20, 0x00,
]

private func report(_ m: [UInt8]) -> Gdl90Report? {
  if case let .report(r) = Gdl90Decoder.decode(m) { return r }
  return nil
}

final class Gdl90FramingTests: XCTestCase {
  func testCrcMatchesSpecHeartbeatExample() {
    let clear = Array(specHeartbeatFrame[1..<8])
    XCTAssertEqual(Gdl90.crc(clear), 0x8BB3)
    XCTAssertEqual(Gdl90.frame(clear), specHeartbeatFrame)
  }

  func testDecodesSpecHeartbeat() {
    var framer = Gdl90Framer()
    let msgs = framer.feed(specHeartbeatFrame)
    XCTAssertEqual(msgs.count, 1)
    guard case let .heartbeat(hb) = Gdl90Decoder.decode(msgs[0]) else { return XCTFail("not a heartbeat") }
    XCTAssertTrue(hb.gpsPositionValid)
    XCTAssertTrue(hb.uatInitialized)
    XCTAssertTrue(hb.utcOk)
    XCTAssertFalse(hb.maintenanceRequired)
    XCTAssertEqual(hb.timestampSec, 0xD0DB)
    XCTAssertEqual(hb.uplinkCount, 1)
    XCTAssertEqual(hb.basicLongCount, 2)
  }

  func testBadCrcIsDropped() {
    var corrupt = specHeartbeatFrame
    corrupt[3] = 0x42
    var framer = Gdl90Framer()
    XCTAssertTrue(framer.feed(corrupt).isEmpty)
    XCTAssertEqual(framer.badFrames, 1)
    XCTAssertEqual(framer.goodFrames, 0)
  }

  func testByteStuffingRoundTrip() {
    let framed = Gdl90.frame([0x02, 0x7E, 0x7D, 0x11])
    XCTAssertEqual(Array(framed[0..<7]), [0x7E, 0x02, 0x7D, 0x5E, 0x7D, 0x5D, 0x11])
    var framer = Gdl90Framer()
    XCTAssertEqual(framer.feed(framed), [[0x02, 0x7E, 0x7D, 0x11]])
  }

  func testBackToBackJunkAndSplitFrames() {
    let b = Gdl90.frame(specTrafficReport)
    var framer = Gdl90Framer()
    XCTAssertEqual(framer.feed([0x01, 0x02] + specHeartbeatFrame + b).count, 2)
    XCTAssertTrue(framer.feed(b[0..<10]).isEmpty)
    XCTAssertEqual(framer.feed(b[10...]).count, 1)
  }
}

final class Gdl90DecoderTests: XCTestCase {
  func testDecodesSpecTrafficReport() {
    let r = report(specTrafficReport)!
    XCTAssertFalse(r.isOwnship)
    XCTAssertFalse(r.trafficAlert)
    XCTAssertEqual(r.addressType, 0)
    XCTAssertEqual(r.id, "ab4549")
    XCTAssertEqual(r.latDeg!, 44.90708, accuracy: 1e-4)
    XCTAssertEqual(r.lonDeg!, -122.99488, accuracy: 1e-4)
    XCTAssertEqual(r.pressureAltitudeFt, 5_000)
    XCTAssertTrue(r.airborne)
    XCTAssertFalse(r.extrapolated)
    XCTAssertEqual(r.trackType, 1)
    XCTAssertEqual(r.nic, 10)
    XCTAssertEqual(r.nacp, 9)
    XCTAssertEqual(r.horizontalVelocityKt, 123)
    XCTAssertEqual(r.verticalVelocityFpm, 64)
    XCTAssertEqual(r.trackDeg!, 45, accuracy: 1e-9)
    XCTAssertEqual(r.emitterCategory, 1)
    XCTAssertEqual(r.callsign, "N825V")
    XCTAssertEqual(r.emergencyCode, 0)
  }

  func testEncoderReproducesSpecBytes() {
    let r = report(specTrafficReport)!
    let encoded = Gdl90Encoder.report(
      ownship: false, address: r.address, latDeg: r.latDeg, lonDeg: r.lonDeg, pressureAltitudeFt: 5_000, trackDeg: 45,
      horizontalVelocityKt: 123, verticalVelocityFpm: 64, callsign: "N825V", nic: 10, nacp: 9)
    XCTAssertEqual(encoded, specTrafficReport)
  }

  func testUnavailableValuesAreNilNotZero() {
    var m = specTrafficReport
    for i in 5...10 { m[i] = 0 }
    m[13] = 0x09
    m[11] = 0xFF
    m[12] = 0xF0
    m[14] = 0xFF
    m[15] = 0xF8
    m[16] = 0x00
    let r = report(m)!
    XCTAssertNil(r.latDeg)
    XCTAssertNil(r.lonDeg)
    XCTAssertNil(r.pressureAltitudeFt)
    XCTAssertNil(r.horizontalVelocityKt)
    XCTAssertNil(r.verticalVelocityFpm)
    XCTAssertNil(r.trackDeg)
    XCTAssertFalse(r.airborne)
    XCTAssertNil(r.toAircraft(receivedAtMs: 0), "traffic without a position is unknown, not at 0,0")
  }

  func testVerticalVelocityScalingAndSpecialValues() {
    func vv(_ raw: Int) -> Int? {
      var m = specTrafficReport
      m[15] = (m[15] & 0xF0) | UInt8(raw >> 8)
      m[16] = UInt8(raw & 0xFF)
      return report(m)!.verticalVelocityFpm
    }
    XCTAssertEqual(vv(0x000), 0)
    XCTAssertEqual(vv(0xFFF), -64)
    XCTAssertEqual(vv(0x1FD), 32_576)
    XCTAssertEqual(vv(0xE03), -32_576)
    XCTAssertNil(vv(0x800))
    XCTAssertNil(vv(0x400))
  }

  func testOwnshipGeometricAltitude() {
    guard case let .ownshipGeoAltitude(m) = Gdl90Decoder.decode([0x0B, 0xFF, 0x38, 0x7F, 0xFF]) else { return XCTFail() }
    XCTAssertEqual(m.altitudeFt, -1_000)
    XCTAssertNil(m.vfomM)
    XCTAssertFalse(m.verticalWarning)
    guard case let .ownshipGeoAltitude(m2) = Gdl90Decoder.decode([0x0B, 0x00, 0xC8, 0x80, 0x0A]) else { return XCTFail() }
    XCTAssertEqual(m2.altitudeFt, 1_000)
    XCTAssertEqual(m2.vfomM, 10)
    XCTAssertTrue(m2.verticalWarning)
  }

  func testForeFlightIdMessage() {
    guard case let .foreFlightId(id) = Gdl90Decoder.decode(Gdl90Encoder.foreFlightId(name: "Stratux", longName: "Stratux v1.6", geoAltitudeIsMsl: false))
    else { return XCTFail() }
    XCTAssertEqual(id.version, 1)
    XCTAssertNil(id.serial)
    XCTAssertEqual(id.name, "Stratux")
    XCTAssertEqual(id.longName, "Stratux v1.6")
    XCTAssertFalse(id.geoAltitudeIsMsl)
  }

  func testUnknownAndShortMessagesAreIgnoredSafely() {
    XCTAssertEqual(Gdl90Decoder.decode([0xCC, 1, 2, 3]), .other(id: 0xCC, length: 4))
    XCTAssertEqual(Gdl90Decoder.decode([0x07] + [UInt8](repeating: 0, count: 435)), .other(id: 0x07, length: 436))
    XCTAssertNil(Gdl90Decoder.decode([0x14, 0x00, 0xAB]))
    XCTAssertNil(Gdl90Decoder.decode([]))
  }
}

/// A mutable clock the session can read.
private final class TestClock: @unchecked Sendable {
  var ms: Int64 = 1_000_000
}

final class Gdl90SessionTests: XCTestCase {
  func testOwnshipTrafficAndReceiverStatus() {
    let clock = TestClock()
    var session = Gdl90Session(clockMs: { clock.ms })
    XCTAssertEqual(session.snapshot().link, .noData)
    let msgs = [
      Gdl90Encoder.heartbeat(gpsValid: true, timestampSec: 3600),
      Gdl90Encoder.foreFlightId(name: "Stratux", longName: "Stratux", geoAltitudeIsMsl: false),
      Gdl90Encoder.report(ownship: true, address: 0xA0B1C2, latDeg: 37, lonDeg: -122, pressureAltitudeFt: 3_500, trackDeg: 90, horizontalVelocityKt: 100),
      Gdl90Encoder.geoAltitude(3_400),
      Gdl90Encoder.report(ownship: false, address: 0xAB4549, latDeg: 37.05, lonDeg: -122, pressureAltitudeFt: 4_000, trackDeg: 180, horizontalVelocityKt: 90),
    ]
    session.ingest(msgs.flatMap(Gdl90.frame))
    let s = session.snapshot()
    XCTAssertEqual(s.link, .connected)
    XCTAssertTrue(s.gpsValid)
    XCTAssertEqual(s.deviceName, "Stratux")
    let own = s.ownship!
    XCTAssertEqual(own.icaoAddress, "a0b1c2")
    XCTAssertEqual(own.groundspeedKt!, 100, accuracy: 1e-9)
    XCTAssertEqual(own.trackDeg!, 90, accuracy: 1)
    XCTAssertEqual(own.gpsAltitudeFt!, 3_400, accuracy: 5)
    XCTAssertEqual(own.gpsAltitudeDatum, .ellipsoid)
    XCTAssertEqual(s.traffic.count, 1)
    let t = s.traffic[0]
    XCTAssertEqual(t.icao24, "ab4549")
    XCTAssertEqual(t.altitudeM!, NavUnits.feetToMetres(4_000), accuracy: 0.01)
    XCTAssertEqual(TrafficRules.target(own, t).relativeAltitudeFt!, 500, accuracy: 1)

    clock.ms += 4_000
    let later = session.snapshot()
    XCTAssertEqual(later.link, .noData)
    XCTAssertNil(later.ownship)
    XCTAssertEqual(later.traffic.count, 1)
    clock.ms += 7_000
    XCTAssertTrue(session.snapshot().traffic.isEmpty)
  }

  func testNoGpsFixOnTheReceiver() {
    var session = Gdl90Session(clockMs: { 0 })
    session.ingest(
      [Gdl90Encoder.heartbeat(gpsValid: false, timestampSec: 0), Gdl90Encoder.report(ownship: true, address: 1, latDeg: nil, lonDeg: nil, pressureAltitudeFt: nil)]
        .flatMap(Gdl90.frame))
    let s = session.snapshot()
    XCTAssertEqual(s.link, .connected)
    XCTAssertFalse(s.gpsValid)
    XCTAssertNil(s.ownship)
  }
}

final class Gdl90ScenarioTests: XCTestCase {
  let scenarios = Gdl90Scenario.loadAll()

  func testBundledScenariosLoad() {
    XCTAssertEqual(scenarios.map(\.id), ["converging", "overtaking", "pattern"])
  }

  /// The apps' simulator and tools/gdl90-replay must send identical bytes (same golden messages as Android).
  func testScenarioBytesMatchTheReplayTool() {
    let expected = [
      "008101100e0000",
      "0a00a0b1c21a9017a950410b4999064000eb014e3132334142202000",
      "0b02a8000a",
      "1400a44e2b1a9b23a9582c0c8999071000be014e343432314b202000",
      "1400a2f9c01a9067a938760849990850000e014e3838535220202000",
      "1402a0b1c31a9017a950410b4999064000eb01202020202020202000",
    ]
    var framer = Gdl90Framer()
    let got = framer.feed(scenarios.first { $0.id == "converging" }!.frames(10, utcSec: 3600))
      .filter { $0[0] != Gdl90.idForeFlight }
      .map { $0.map { String(format: "%02x", $0) }.joined() }
    XCTAssertEqual(got, expected)
  }

  /// Plays a whole scenario through the real decoder, session and callout rules.
  private func callouts(_ id: String) -> [String] {
    let scenario = scenarios.first { $0.id == id }!
    let clock = TestClock()
    var session = Gdl90Session(clockMs: { clock.ms })
    let advisor = TrafficAdvisor()
    var out: [String] = []
    for t in 0..<scenario.durationSec {
      clock.ms = Int64(t) * 1_000
      session.ingest(scenario.frames(Double(t), utcSec: t))
      let s = session.snapshot()
      guard let c = advisor.next(s.ownship!, s.traffic, nowMs: clock.ms) else { continue }
      XCTAssertNotEqual(c.target.aircraft.icao24, "a0b1c3", "ownship's TIS-B shadow must never be called out")
      out.append("\(c.target.aircraft.icao24): \(c.spoken)")
    }
    return out
  }

  func testConvergingCallsTrafficAt2OClockThenAgainAsItCloses() {
    let c = callouts("converging")
    XCTAssertTrue(c.first!.hasPrefix("a44e2b: Traffic, 2 o'clock, ") && c.first!.contains("500 above"), c.first!)
    XCTAssertGreaterThanOrEqual(c.filter { $0.hasPrefix("a44e2b") }.count, 2)
  }

  func testOvertakingCallsTrafficFromBehind() {
    let c = callouts("overtaking")
    XCTAssertTrue(c.first!.hasPrefix("a7c3d1: Traffic, 6 o'clock"), c.first!)
  }

  func testPatternNeverCallsTheTaxiingAircraft() {
    let c = callouts("pattern")
    XCTAssertFalse(c.isEmpty)
    XCTAssertFalse(c.contains { $0.hasPrefix("a4444d") })
  }
}

final class Gdl90UdpReceiverTests: XCTestCase {
  func testReceivesADatagramOnThePort() async throws {
    let port: UInt16 = 45_123
    let receiver = Gdl90UdpReceiver(port: port)
    let got = Task { () -> [UInt8]? in
      for await d in receiver.datagrams() { return d }
      return nil
    }
    let sender = NWConnection(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port)!, using: .udp)
    sender.start(queue: .global())
    let sending = Task {
      while !Task.isCancelled {
        sender.send(content: Data(specHeartbeatFrame), completion: .idempotent)
        try? await Task.sleep(nanoseconds: 200_000_000)
      }
    }
    let timeout = Task {
      try? await Task.sleep(nanoseconds: 10_000_000_000)
      got.cancel()
    }
    let value = await got.value
    sending.cancel()
    timeout.cancel()
    sender.cancel()
    XCTAssertEqual(value, specHeartbeatFrame)
  }
}

// Same cases as app/android/data/src/test/kotlin/.../AirportDatabaseTest.kt.
final class AirportDatabaseTests: XCTestCase {
  let airports = AirportDatabase.load()

  func testBundledListLoads() {
    XCTAssertGreaterThan(airports.count, 20_000)
    let pao = airports.first { $0.ident == "KPAO" }!
    XCTAssertEqual(pao.name, "Palo Alto Airport")
    XCTAssertEqual(pao.faa, "PAO")
    XCTAssertEqual(pao.type, .medium)
  }

  func testUsFieldsWithOnlyAnFaaIdentifierAreIncluded() {
    let f = airports.first { $0.ident == "0B5" }!
    XCTAssertNil(f.icao)
    XCTAssertEqual(f.country, "US")
  }

  func testSearchAndNearestOnTheRealList() {
    XCTAssertEqual(Airfields.search("KPAO", airports).first?.ident, "KPAO")
    XCTAssertEqual(Airfields.search("MAA", airports).first?.ident, "VOMM")
    XCTAssertEqual(Airfields.nearest(GeoPoint(lat: 12.99, lon: 80.17), airports).first?.airfield.ident, "VOMM")
  }
}
