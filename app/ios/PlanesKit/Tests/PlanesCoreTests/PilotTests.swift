import XCTest
@testable import PlanesCore

// Same cases as app/android/core/src/test/kotlin/.../pilot/PilotTest.kt — keep them in sync.

/// Ownship over Palo Alto, flying north-west (330°) at 100 kt, 2,500 ft.
private let home = GeoPoint(lat: 37.4611, lon: -122.1150)

private func own(trackDeg: Double? = 330, gsKt: Double? = 100, altFt: Double? = 2_500, icao: String? = "a1b2c3") -> Ownship {
  Ownship(
    position: home, groundspeedKt: gsKt, trackDeg: trackDeg, gpsAltitudeFt: altFt, pressureAltitudeFt: altFt, icaoAddress: icao,
    source: .receiver, timeMs: 0)
}

/// Traffic `nm` away at `bearingDeg` true, `relFt` relative to ownship.
private func traffic(
  _ id: String, _ bearingDeg: Double, _ nm: Double, relFt: Double? = 0, trackDeg: Double? = nil, kt: Double? = nil, fpm: Double? = nil,
  onGround: Bool = false
) -> Aircraft {
  Aircraft(
    icao24: id, callsign: id.uppercased(), position: Geo.destination(from: home, bearingDeg: bearingDeg, distanceKm: NavUnits.km(nm)),
    altitudeM: relFt.map { NavUnits.feetToMetres(2_500 + $0) }, speedMs: kt.map(NavUnits.ms), trackDeg: trackDeg,
    verticalRateMs: fpm.map { $0 / NavUnits.fpmPerMs }, onGround: onGround)
}

final class NavUnitsTests: XCTestCase {
  func testTracksAreThreeDigitsAndNorthIs360() {
    XCTAssertEqual(NavUnits.degreesText(45), "045")
    XCTAssertEqual(NavUnits.degreesText(0), "360")
    XCTAssertEqual(NavUnits.degreesText(359.7), "360")
    XCTAssertEqual(NavUnits.degreesText(4.6), "005")
    XCTAssertEqual(NavUnits.degreesSpoken(45), "zero four five")
    XCTAssertEqual(NavUnits.degreesSpoken(279), "two seven niner")
  }

  func testUnitConversions() {
    XCTAssertEqual(NavUnits.nm(1.852), 1, accuracy: 1e-9)
    XCTAssertEqual(NavUnits.kt(51.4444), 100, accuracy: 0.01)
    XCTAssertEqual(NavUnits.fpmPerMs, 196.85, accuracy: 0.01)
  }
}

final class TrackEstimatorTests: XCTestCase {
  func testUsesGpsSpeedAndCourse() {
    var est = TrackEstimator()
    let e = est.update(GpsFix(position: home, timeMs: 0, speedMs: NavUnits.ms(110), courseDeg: 45))!
    XCTAssertEqual(e.groundspeedKt, 110, accuracy: 0.01)
    XCTAssertEqual(e.trackDeg!, 45, accuracy: 0.01)
  }

  func testComputesTrackFromSuccessivePositions() {
    var est = TrackEstimator(smoothing: 1)
    XCTAssertNil(est.update(GpsFix(position: home, timeMs: 0)))
    // 0.1 NM due east in 3 s = 120 kt.
    let e = est.update(GpsFix(position: Geo.destination(from: home, bearingDeg: 90, distanceKm: NavUnits.km(0.1)), timeMs: 3_000))!
    XCTAssertEqual(e.groundspeedKt, 120, accuracy: 0.5)
    XCTAssertEqual(e.trackDeg!, 90, accuracy: 0.5)
  }

  func testSmoothingWrapsThroughNorth() {
    var est = TrackEstimator(smoothing: 0.5)
    _ = est.update(GpsFix(position: home, timeMs: 0, speedMs: NavUnits.ms(100), courseDeg: 350))
    let e = est.update(GpsFix(position: home, timeMs: 1_000, speedMs: NavUnits.ms(100), courseDeg: 10))!
    XCTAssertEqual(Geo.signedBearingDiff(from: 0, to: e.trackDeg!), 0, accuracy: 0.5)
  }

  func testNoTrackWhenStopped() {
    var est = TrackEstimator()
    let e = est.update(GpsFix(position: home, timeMs: 0, speedMs: 0.5, courseDeg: nil))!
    XCTAssertEqual(e.groundspeedKt, 0, accuracy: 1e-9)
    XCTAssertNil(e.trackDeg)
  }
}

final class NavigationTests: XCTestCase {
  func testDirectToBearingDistanceAndEte() {
    let dest = Geo.destination(from: home, bearingDeg: 90, distanceKm: NavUnits.km(20))
    let d = Navigation.directTo(from: home, to: dest, groundspeedKt: 120, trackDeg: 60)
    XCTAssertEqual(d.bearingDeg, 90, accuracy: 0.1)
    XCTAssertEqual(d.distanceNm, 20, accuracy: 0.01)
    XCTAssertEqual(d.eteMin!, 10, accuracy: 0.01)
    XCTAssertEqual(d.turnDeg!, 30, accuracy: 0.1)
  }

  func testNoEteWhenStoppedNoTurnWithoutTrack() {
    let d = Navigation.directTo(from: home, to: Geo.destination(from: home, bearingDeg: 270, distanceKm: 10), groundspeedKt: 0, trackDeg: nil)
    XCTAssertNil(d.eteMin)
    XCTAssertNil(d.turnDeg)
  }
}

final class TrafficRulesTests: XCTestCase {
  func testClockPositions() {
    XCTAssertEqual(TrafficRules.clock(0), 12)
    XCTAssertEqual(TrafficRules.clock(90), 3)
    XCTAssertEqual(TrafficRules.clock(180), 6)
    XCTAssertEqual(TrafficRules.clock(-90), 9)
    XCTAssertEqual(TrafficRules.clock(60), 2)
    XCTAssertEqual(TrafficRules.clock(350), 12)
    XCTAssertEqual(TrafficRules.clock(-25), 11)
  }

  func testTrafficDirectlyAheadIs12OClock() {
    let t = TrafficRules.target(own(), traffic("ahead", 330, 3))
    XCTAssertEqual(t.clock, 12)
    XCTAssertEqual(t.distanceNm, 3, accuracy: 0.01)
  }

  func testTraffic90DegreesRightIs3OClock() {
    XCTAssertEqual(TrafficRules.target(own(), traffic("right", 60, 2)).clock, 3)
  }

  func test500AboveClimbing() {
    let t = TrafficRules.target(own(), traffic("t", 30, 3, relFt: 510, fpm: 800))
    XCTAssertEqual(t.relativeAltitudeRoundedFt, 500)
    XCTAssertEqual(t.trend, .climbing)
    XCTAssertEqual(TrafficRules.spoken(t), "Traffic, 2 o'clock, 3 miles, 500 above, climbing.")
    XCTAssertEqual(TrafficRules.short(t), "2 o'clock · 3.0 NM · +500 ft ↑")
  }

  func testBelowSameAndUnknownAltitude() {
    let below = TrafficRules.target(own(), traffic("b", 330, 0.6, relFt: -1_480, fpm: -900))
    XCTAssertEqual(TrafficRules.spoken(below), "Traffic, 12 o'clock, less than 1 mile, 1,500 below, descending.")
    let same = TrafficRules.target(own(), traffic("s", 150, 1.4, relFt: 150, fpm: 100))
    XCTAssertEqual(TrafficRules.spoken(same), "Traffic, 6 o'clock, 1 mile, same altitude.")
    let unknown = TrafficRules.target(own(), traffic("u", 240, 4, relFt: nil))
    XCTAssertEqual(TrafficRules.spoken(unknown), "Traffic, 9 o'clock, 4 miles, altitude unknown.")
    XCTAssertEqual(TrafficRules.short(unknown), "9 o'clock · 4.0 NM · alt ?")
  }

  func testWithoutTrackUsesCompassDirections() {
    let t = TrafficRules.target(own(trackDeg: nil, gsKt: 0), traffic("t", 45, 2))
    XCTAssertNil(t.clock)
    XCTAssertEqual(TrafficRules.spoken(t), "Traffic, north-east, 2 miles, same altitude.")
  }

  func testHeadOnIsConvergingFlyingAwayIsNot() {
    let headOn = TrafficRules.target(own(), traffic("h", 330, 4, trackDeg: 150, kt: 120))
    XCTAssertEqual(headOn.closureKt!, 220, accuracy: 1)
    XCTAssertTrue(headOn.converging)
    let away = TrafficRules.target(own(), traffic("a", 150, 2, trackDeg: 150, kt: 120))
    XCTAssertFalse(away.converging)
  }

  func testFiltersByRangeAltitudeGroundAndOwnship() {
    let list = [
      traffic("far", 0, 6),
      traffic("high", 0, 2, relFt: 2_500),
      traffic("ground", 0, 1, onGround: true),
      traffic("a1b2c3", 90, 2),  // our own ICAO address
      traffic("shadow", 0, 0.05, relFt: 100),  // on top of us: a TIS-B shadow of ownship
      traffic("ok", 90, 4),
    ]
    XCTAssertEqual(TrafficRules.relevant(own(), list).map(\.aircraft.icao24), ["ok"])
  }

  func testConvergingTrafficComesBeforeNearerTraffic() {
    let near = traffic("near", 150, 1.5, trackDeg: 150, kt: 120)
    let closing = traffic("closing", 60, 3, trackDeg: 240, kt: 110)
    XCTAssertEqual(TrafficRules.relevant(own(), [near, closing]).map(\.aircraft.icao24), ["closing", "near"])
  }
}

final class TrafficAdvisorTests: XCTestCase {
  let config = CalloutConfig(minIntervalMs: 8_000, repeatIntervalMs: 15_000)

  func testOneCalloutAtATimeWithMinimumInterval() {
    let adv = TrafficAdvisor(config: config)
    let list = [traffic("a", 60, 3), traffic("b", 300, 2)]
    XCTAssertEqual(adv.next(own(), list, nowMs: 0)?.target.aircraft.icao24, "b")
    XCTAssertNil(adv.next(own(), list, nowMs: 5_000))
    XCTAssertEqual(adv.next(own(), list, nowMs: 8_000)?.target.aircraft.icao24, "a")
    XCTAssertNil(adv.next(own(), list, nowMs: 20_000))
  }

  func testRepeatsOnlyWhenMeaningfullyCloser() {
    let adv = TrafficAdvisor(config: config)
    XCTAssertNotNil(adv.next(own(), [traffic("x", 60, 4)], nowMs: 0))
    XCTAssertNil(adv.next(own(), [traffic("x", 60, 3.5)], nowMs: 20_000))
    XCTAssertNotNil(adv.next(own(), [traffic("x", 60, 2.9)], nowMs: 30_000))
    XCTAssertNil(adv.next(own(), [traffic("x", 60, 1)], nowMs: 35_000))
    XCTAssertNotNil(adv.next(own(), [traffic("x", 60, 1)], nowMs: 46_000))
  }

  func testNeverCallsOutOwnship() {
    let adv = TrafficAdvisor(config: config)
    XCTAssertNil(adv.next(own(), [traffic("a1b2c3", 0, 1)], nowMs: 0))
    XCTAssertNil(adv.next(own(icao: nil), [traffic("ghost", 0, 0.02, relFt: 0)], nowMs: 10_000))
  }
}

final class AirfieldsTests: XCTestCase {
  private func field(_ ident: String, _ iata: String?, _ faa: String?, _ name: String, _ city: String, _ bearing: Double, _ nm: Double, _ type: AirfieldType = .small) -> Airfield {
    let isIcao = ident.count == 4 && ident.first!.isLetter
    return Airfield(
      ident: ident, icao: isIcao ? ident : nil, iata: iata, faa: faa, name: name, city: city, country: "US",
      position: Geo.destination(from: home, bearingDeg: bearing, distanceKm: NavUnits.km(nm)), elevationFt: 10, type: type)
  }

  private var list: [Airfield] {
    [
      field("KPAO", nil, "PAO", "Palo Alto Airport", "Palo Alto", 120, 1.2),
      field("KSQL", "SQL", "SQL", "San Carlos Airport", "San Carlos", 320, 6),
      field("KSFO", "SFO", "SFO", "San Francisco International Airport", "San Francisco", 320, 20, .large),
      field("0Q5", nil, "0Q5", "Shelter Cove Airport", "Shelter Cove", 320, 200),
    ]
  }

  func testNearestAirport() {
    let n = Airfields.nearest(home, list)
    XCTAssertEqual(n.count, 1)
    XCTAssertEqual(n[0].airfield.ident, "KPAO")
    XCTAssertEqual(n[0].distanceNm, 1.2, accuracy: 0.01)
    XCTAssertEqual(n[0].bearingDeg, 120, accuracy: 0.1)
  }

  func testSearchByIcaoFaaIataAndName() {
    XCTAssertEqual(Airfields.search("kpao", list).first?.ident, "KPAO")
    XCTAssertEqual(Airfields.search("PAO", list).first?.ident, "KPAO")
    XCTAssertEqual(Airfields.search("0q5", list).first?.ident, "0Q5")
    XCTAssertEqual(Airfields.search("SFO", list).first?.ident, "KSFO")
    XCTAssertEqual(Airfields.search("san", list).map(\.ident), ["KSFO", "KSQL"])
    XCTAssertEqual(Airfields.search("francisco", list).map(\.ident), ["KSFO"])
    XCTAssertTrue(Airfields.search("k", list).isEmpty)
  }
}

final class PilotTextTests: XCTestCase {
  let dest = Airfield(
    ident: "KSQL", icao: "KSQL", iata: "SQL", faa: "SQL", name: "San Carlos Airport", city: "San Carlos", country: "US",
    position: GeoPoint(lat: 37.5119, lon: -122.2495), elevationFt: 5, type: .small)

  func testReadoutsAlwaysLabelGpsAltitude() {
    XCTAssertEqual(PilotText.groundspeed(112.4), "112 kt")
    XCTAssertEqual(PilotText.track(45), "045°")
    XCTAssertEqual(PilotText.gpsAltitude(4_517), "4,520 ft GPS")
    XCTAssertEqual(PilotText.gpsAltitude(nil), "— ft GPS")
    XCTAssertEqual(PilotText.ete(12.2), "12 min")
    XCTAssertEqual(PilotText.ete(65), "1 h 05 min")
    XCTAssertEqual(PilotText.eteSpoken(65), "1 hour 5 minutes")
  }

  func testGlassesCardHasOwnNumbersDirectToAndOneAlert() {
    let d = DirectToState(destination: dest, guidance: Navigation.directTo(from: home, to: dest.position, groundspeedKt: 100, trackDeg: 330))
    let alert = TrafficRules.target(own(), traffic("t", 30, 3, relFt: 500))
    let card = PilotText.card(own: own(), direct: d, nearest: nil, alert: alert)
    XCTAssertEqual(card.title, "100 kt · TRK 330° · 2,500 GPS")
    XCTAssertTrue(card.route!.hasPrefix("→ KSQL "))
    XCTAssertEqual(card.meta, "TRAFFIC 2 o'clock · 3.0 NM · +500 ft")
  }

  func testSpokenSummary() {
    let n = NearestAirfield(airfield: dest, distanceNm: 4, bearingDeg: 300)
    XCTAssertEqual(
      PilotText.summarySpoken(own: own(), direct: nil, nearest: n, top: nil),
      "Groundspeed 100 knots, track three three zero, GPS altitude 2,500 feet. Nearest airport San Carlos, 4 miles north-west. No traffic nearby.")
  }
}
