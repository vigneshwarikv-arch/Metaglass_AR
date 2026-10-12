import XCTest
@testable import PlanesCore

/// Same cases as the Android core tests (GeoTest, TargetSelectorTest, AnswerBuilderTest, HeadAimTest),
/// so both apps give identical answers.
final class GeoTests: XCTestCase {
  let chennai = GeoPoint(lat: 12.9941, lon: 80.1709)

  func testDueNorth10kmAt3000mIs16_7DegreesUp() {
    var plane = Geo.destination(from: chennai, bearingDeg: 0, distanceKm: 10)
    plane.altitudeM = 3000
    let look = Geo.lookAngle(observer: chennai, target: plane)
    XCTAssertEqual(Geo.signedBearingDiff(from: 0, to: look.bearingDeg), 0, accuracy: 0.01)
    XCTAssertEqual(look.groundDistanceKm, 10, accuracy: 0.01)
    XCTAssertEqual(look.elevationDeg, 16.7, accuracy: 0.1)
  }

  func testBearings() {
    for b in [90.0, 180, 270] {
      XCTAssertEqual(Geo.bearingDeg(from: chennai, to: Geo.destination(from: chennai, bearingDeg: b, distanceKm: 20)), b, accuracy: 0.1)
    }
  }

  func testChennaiToDelhi() {
    XCTAssertEqual(Geo.distanceKm(chennai, GeoPoint(lat: 28.5562, lon: 77.1)), 1757, accuracy: 10)
  }

  func testOverheadAndCurvature() {
    var over = chennai
    over.altitudeM = 1000
    XCTAssertEqual(Geo.elevationDeg(observer: chennai, target: over), 90, accuracy: 0.001)
    var far = Geo.destination(from: chennai, bearingDeg: 45, distanceKm: 100)
    far.altitudeM = 300
    XCTAssertLessThan(Geo.elevationDeg(observer: chennai, target: far), 0)
  }

  func testSignedDiffAndSeparation() {
    XCTAssertEqual(Geo.signedBearingDiff(from: 350, to: 10), 20, accuracy: 1e-9)
    XCTAssertEqual(Geo.signedBearingDiff(from: 10, to: 350), -20, accuracy: 1e-9)
    XCTAssertEqual(Geo.angularSeparationDeg(Pointing(bearingDeg: 0, elevationDeg: 0), Pointing(bearingDeg: 30, elevationDeg: 0)), 30, accuracy: 1e-6)
    XCTAssertEqual(Geo.angularSeparationDeg(Pointing(bearingDeg: 0, elevationDeg: 10), Pointing(bearingDeg: 0, elevationDeg: 25)), 15, accuracy: 1e-6)
  }

  func testCompassPoints() {
    XCTAssertEqual(CompassPoint.of(359), .n)
    XCTAssertEqual(CompassPoint.of(23), .ne)
    XCTAssertEqual(CompassPoint.of(270), .w)
    XCTAssertEqual(CompassPoint.of(-45), .nw)
  }
}

final class TargetSelectorTests: XCTestCase {
  let user = GeoPoint(lat: 37.4323, lon: -121.8996)

  func plane(_ id: String, _ bearing: Double, _ km: Double, _ alt: Double?, onGround: Bool = false) -> Aircraft {
    Aircraft(
      icao24: id, callsign: id.uppercased(), position: Geo.destination(from: user, bearingDeg: bearing, distanceKm: km),
      altitudeM: alt, speedMs: 200, trackDeg: 0, onGround: onGround)
  }

  func testPointingAtAPlaneSelectsIt() {
    let r = TargetSelector.select(
      user: user, aircraft: [plane("north", 0, 10, 3000), plane("east", 90, 20, 9000)], pointing: Pointing(bearingDeg: 2, elevationDeg: 15))
    guard case let .hit(t, offset) = r else { return XCTFail("\(r)") }
    XCTAssertEqual(t.aircraft.icao24, "north")
    XCTAssertLessThan(offset, 3)
  }

  func testMissSaysWhichWayToTurn() {
    let r = TargetSelector.select(user: user, aircraft: [plane("north", 0, 10, 3000)], pointing: Pointing(bearingDeg: 315, elevationDeg: 16))
    guard case let .miss(n, turn, deg) = r else { return XCTFail("\(r)") }
    XCTAssertEqual(n.aircraft.icao24, "north")
    XCTAssertEqual(turn, .right)
    XCTAssertEqual(deg, 45, accuracy: 1)
  }

  func testLookUp() {
    let r = TargetSelector.select(user: user, aircraft: [plane("north", 0, 10, 3000)], pointing: Pointing(bearingDeg: 0, elevationDeg: -10))
    guard case let .miss(_, turn, deg) = r else { return XCTFail("\(r)") }
    XCTAssertEqual(turn, .up)
    XCTAssertEqual(deg, 26.7, accuracy: 0.5)
  }

  func testGroundAndNoAltitudeIgnored() {
    let r = TargetSelector.select(
      user: user, aircraft: [plane("parked", 0, 2, 0, onGround: true), plane("noalt", 0, 3, nil)], pointing: Pointing(bearingDeg: 0, elevationDeg: 0))
    XCTAssertEqual(r, .noAircraft)
  }

  func testNearerWinsTies() {
    let r = TargetSelector.select(
      user: user, aircraft: [plane("far", 0, 30, 9000), plane("near", 0, 5, 1500)], pointing: Pointing(bearingDeg: 0, elevationDeg: 16.7))
    guard case let .hit(t, _) = r else { return XCTFail("\(r)") }
    XCTAssertEqual(t.aircraft.icao24, "near")
  }

  func testNearbySortedAndLimited() {
    let list = TargetSelector.nearby(
      user: user, aircraft: [plane("sw", 225, 40, 10000), plane("east", 90, 20, 9000), plane("north", 0, 10, 3000)], radiusKm: 30)
    XCTAssertEqual(list.map(\.aircraft.icao24), ["north", "east"])
  }
}

final class AnswerBuilderTests: XCTestCase {
  let user = GeoPoint(lat: 12.9941, lon: 80.1709)
  let delhi = Airport(icao: "VIDP", iata: "DEL", name: "Indira Gandhi International", city: "Delhi")
  let chennai = Airport(icao: "VOMM", iata: "MAA", name: "Chennai International", city: "Chennai")

  func seen(altFt: Double? = 32000, km: Double = 25, bearing: Double = 0, info: AircraftInfo? = nil, callsign: String? = "IGO2045") -> SeenAircraft {
    let a = Aircraft(
      icao24: "800abc", callsign: callsign, position: Geo.destination(from: user, bearingDeg: bearing, distanceKm: km),
      altitudeM: altFt.map { $0 / Units.feetPerMetre }, speedMs: 230, trackDeg: 180,
      info: info ?? AircraftInfo(airline: "IndiGo", typeCode: "A320", route: Route(origin: delhi, destination: chennai)))
    return SeenAircraft(aircraft: a, look: Geo.lookAngle(observer: user, target: a.position3d))
  }

  func testSpecExample() {
    XCTAssertEqual(AnswerBuilder.answer(.hit(seen(), offsetDeg: 2)).spoken, "IndiGo A320, Delhi to Chennai, 32,000 feet, 25 kilometres north.")
  }

  func testMiles() {
    XCTAssertEqual(
      AnswerBuilder.describe(seen(km: 16.09344 * 2, bearing: 135), unit: .miles),
      "IndiGo A320, Delhi to Chennai, 32,000 feet, 20 miles south-east.")
  }

  func testFallbacks() {
    XCTAssertEqual(AnswerBuilder.describe(seen(info: AircraftInfo(typeCode: "B77W")), unit: .kilometres), "Flight IGO2045, 777, 32,000 feet, 25 kilometres north.")
    XCTAssertEqual(AnswerBuilder.describe(seen(altFt: nil, info: AircraftInfo(), callsign: nil), unit: .kilometres), "An unknown aircraft, 25 kilometres north.")
  }

  func testMiss() {
    let s = AnswerBuilder.answer(.miss(nearest: seen(km: 12, bearing: 90), turn: .right, turnDeg: 38)).spoken
    XCTAssertEqual(s, "No plane that way. Nearest is IndiGo A320, 12 kilometres east. Turn right about 40 degrees.")
  }

  func testCard() {
    let c = AnswerBuilder.card(seen(), unit: .kilometres)
    XCTAssertEqual(c.title, "IndiGo · A320")
    XCTAssertEqual(c.route, "DEL → MAA")
    XCTAssertEqual(c.meta, "32,000 ft · 25 km N")
  }
}

final class HeadAimTests: XCTestCase {
  let aim = HeadAim()
  let level = Vec3(9.81, 0, 0)

  func testLevelFacingNorth() {
    XCTAssertEqual(aim.elevationDeg(up: level)!, 0, accuracy: 1e-6)
    XCTAssertEqual(Geo.signedBearingDiff(from: 0, to: aim.magneticBearingDeg(up: level, field: Vec3(-40, 0, 25))!), 0, accuracy: 1e-6)
  }

  func testFacingEast() {
    XCTAssertEqual(aim.magneticBearingDeg(up: level, field: Vec3(-40, -25, 0))!, 90, accuracy: 1e-6)
  }

  func testTiltedUp30() {
    let t = 30.0 * .pi / 180
    XCTAssertEqual(aim.elevationDeg(up: Vec3(9.81 * cos(t), 0, 9.81 * sin(t)))!, 30, accuracy: 1e-6)
  }

  func testPhoneBackCameraLevelNorth() {
    // Phone upright, screen facing the user who faces north: up is +Y, back camera (−Z) points north.
    let phone = HeadAim(forward: Vec3(0, 0, -1))
    XCTAssertEqual(phone.elevationDeg(up: Vec3(0, 9.81, 0))!, 0, accuracy: 1e-6)
    XCTAssertEqual(Geo.signedBearingDiff(from: 0, to: phone.magneticBearingDeg(up: Vec3(0, 9.81, 0), field: Vec3(0, -40, -25))!), 0, accuracy: 1e-6)
  }
}
