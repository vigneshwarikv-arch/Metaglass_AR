package com.metaglassar.planes.core.pilot

import com.metaglassar.planes.core.Aircraft
import com.metaglassar.planes.core.Geo
import com.metaglassar.planes.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Same cases as app/ios/PlanesKit/Tests/PlanesCoreTests/PilotTests.swift — keep them in sync.

/** Ownship over Palo Alto, flying north-west (330°) at 100 kt, 2,500 ft. */
private val home = GeoPoint(37.4611, -122.1150)

private fun own(
    trackDeg: Double? = 330.0,
    gsKt: Double? = 100.0,
    altFt: Double? = 2_500.0,
    icao: String? = "a1b2c3",
) = Ownship(
    position = home,
    groundspeedKt = gsKt,
    trackDeg = trackDeg,
    gpsAltitudeFt = altFt,
    pressureAltitudeFt = altFt,
    icaoAddress = icao,
    source = PositionSource.RECEIVER,
    timeMs = 0,
)

/** Traffic [nm] away at [bearingDeg] true, [relFt] relative to ownship. */
private fun traffic(
    id: String,
    bearingDeg: Double,
    nm: Double,
    relFt: Double? = 0.0,
    trackDeg: Double? = null,
    kt: Double? = null,
    fpm: Double? = null,
    onGround: Boolean = false,
) = Aircraft(
    icao24 = id,
    callsign = id.uppercase(),
    position = Geo.destination(home, bearingDeg, NavUnits.km(nm)),
    altitudeM = relFt?.let { NavUnits.feetToMetres(2_500.0 + it) },
    speedMs = kt?.let(NavUnits::ms),
    trackDeg = trackDeg,
    verticalRateMs = fpm?.let { it / NavUnits.FPM_PER_MS },
    onGround = onGround,
)

class NavUnitsTest {
  @Test
  fun `tracks are three digits and north is 360`() {
    assertEquals("045", NavUnits.degreesText(45.0))
    assertEquals("360", NavUnits.degreesText(0.0))
    assertEquals("360", NavUnits.degreesText(359.7))
    assertEquals("005", NavUnits.degreesText(4.6))
    assertEquals("zero four five", NavUnits.degreesSpoken(45.0))
    assertEquals("two seven niner", NavUnits.degreesSpoken(279.0))
  }

  @Test
  fun `unit conversions`() {
    assertEquals(1.0, NavUnits.nm(1.852), 1e-9)
    assertEquals(100.0, NavUnits.kt(51.4444), 0.01)
    assertEquals(196.85, NavUnits.FPM_PER_MS, 0.01)
  }
}

class TrackEstimatorTest {
  @Test
  fun `uses the GPS's own speed and course when given`() {
    val est = TrackEstimator()
    val e = est.update(GpsFix(home, 0, speedMs = NavUnits.ms(110.0), courseDeg = 45.0))!!
    assertEquals(110.0, e.groundspeedKt, 0.01)
    assertEquals(45.0, e.trackDeg!!, 0.01)
  }

  @Test
  fun `computes track from successive positions`() {
    val est = TrackEstimator(smoothing = 1.0)
    assertNull(est.update(GpsFix(home, 0)))
    // 0.1 NM due east in 3 s = 120 kt.
    val e = est.update(GpsFix(Geo.destination(home, 90.0, NavUnits.km(0.1)), 3_000))!!
    assertEquals(120.0, e.groundspeedKt, 0.5)
    assertEquals(90.0, e.trackDeg!!, 0.5)
  }

  @Test
  fun `smoothing wraps through north`() {
    val est = TrackEstimator(smoothing = 0.5)
    est.update(GpsFix(home, 0, speedMs = NavUnits.ms(100.0), courseDeg = 350.0))
    val e = est.update(GpsFix(home, 1_000, speedMs = NavUnits.ms(100.0), courseDeg = 10.0))!!
    // Halfway between 350° and 010° is 360°, not 180°.
    assertEquals(0.0, Geo.signedBearingDiff(0.0, e.trackDeg!!), 0.5)
  }

  @Test
  fun `no track when stopped`() {
    val est = TrackEstimator()
    val e = est.update(GpsFix(home, 0, speedMs = 0.5, courseDeg = null))!!
    assertEquals(0.0, e.groundspeedKt, 1e-9)
    assertNull(e.trackDeg)
  }
}

class NavigationTest {
  @Test
  fun `direct-to bearing, distance and ETE`() {
    val dest = Geo.destination(home, 90.0, NavUnits.km(20.0))
    val d = Navigation.directTo(home, dest, groundspeedKt = 120.0, trackDeg = 60.0)
    assertEquals(90.0, d.bearingDeg, 0.1)
    assertEquals(20.0, d.distanceNm, 0.01)
    assertEquals(10.0, d.eteMin!!, 0.01)
    assertEquals(30.0, d.turnDeg!!, 0.1) // turn right 30°
  }

  @Test
  fun `no ETE when stopped, no turn without track`() {
    val d = Navigation.directTo(home, Geo.destination(home, 270.0, 10.0), groundspeedKt = 0.0, trackDeg = null)
    assertNull(d.eteMin)
    assertNull(d.turnDeg)
  }
}

class TrafficRulesTest {
  @Test
  fun `clock positions`() {
    assertEquals(12, TrafficRules.clock(0.0))
    assertEquals(3, TrafficRules.clock(90.0))
    assertEquals(6, TrafficRules.clock(180.0))
    assertEquals(9, TrafficRules.clock(-90.0))
    assertEquals(2, TrafficRules.clock(60.0))
    assertEquals(12, TrafficRules.clock(350.0))
    assertEquals(11, TrafficRules.clock(-25.0))
  }

  @Test
  fun `traffic directly ahead is 12 o'clock`() {
    val t = TrafficRules.target(own(), traffic("ahead", 330.0, 3.0))
    assertEquals(12, t.clock)
    assertEquals(3.0, t.distanceNm, 0.01)
  }

  @Test
  fun `traffic 90 degrees right of track is 3 o'clock`() {
    assertEquals(3, TrafficRules.target(own(), traffic("right", 60.0, 2.0)).clock)
  }

  @Test
  fun `500 ft above, climbing`() {
    val t = TrafficRules.target(own(), traffic("t", 30.0, 3.0, relFt = 510.0, fpm = 800.0))
    assertEquals(500, t.relativeAltitudeRoundedFt)
    assertEquals(VerticalTrend.CLIMBING, t.trend)
    assertEquals("Traffic, 2 o'clock, 3 miles, 500 above, climbing.", TrafficRules.spoken(t))
    assertEquals("2 o'clock · 3.0 NM · +500 ft ↑", TrafficRules.short(t))
  }

  @Test
  fun `below, same altitude and unknown altitude`() {
    val below = TrafficRules.target(own(), traffic("b", 330.0, 0.6, relFt = -1_480.0, fpm = -900.0))
    assertEquals("Traffic, 12 o'clock, less than 1 mile, 1,500 below, descending.", TrafficRules.spoken(below))
    val same = TrafficRules.target(own(), traffic("s", 150.0, 1.4, relFt = 150.0, fpm = 100.0))
    assertEquals("Traffic, 6 o'clock, 1 mile, same altitude.", TrafficRules.spoken(same))
    val unknown = TrafficRules.target(own(), traffic("u", 240.0, 4.0, relFt = null))
    assertEquals("Traffic, 9 o'clock, 4 miles, altitude unknown.", TrafficRules.spoken(unknown))
    assertEquals("9 o'clock · 4.0 NM · alt ?", TrafficRules.short(unknown))
  }

  @Test
  fun `without a track, uses compass directions`() {
    val t = TrafficRules.target(own(trackDeg = null, gsKt = 0.0), traffic("t", 45.0, 2.0))
    assertNull(t.clock)
    assertEquals("Traffic, north-east, 2 miles, same altitude.", TrafficRules.spoken(t))
  }

  @Test
  fun `head-on traffic is converging, traffic flying away is not`() {
    // Ahead, flying towards us at 120 kt while we fly at 100 kt: closure 220 kt.
    val headOn = TrafficRules.target(own(), traffic("h", 330.0, 4.0, trackDeg = 150.0, kt = 120.0))
    assertEquals(220.0, headOn.closureKt!!, 1.0)
    assertTrue(headOn.converging)
    val away = TrafficRules.target(own(), traffic("a", 150.0, 2.0, trackDeg = 150.0, kt = 120.0))
    assertTrue(!away.converging)
  }

  @Test
  fun `filters by range, altitude band, ground and ownship`() {
    val list =
        listOf(
            traffic("far", 0.0, 6.0),
            traffic("high", 0.0, 2.0, relFt = 2_500.0),
            traffic("ground", 0.0, 1.0, onGround = true),
            traffic("a1b2c3", 90.0, 2.0), // our own ICAO address
            traffic("shadow", 0.0, 0.05, relFt = 100.0), // on top of us: a TIS-B shadow of ownship
            traffic("ok", 90.0, 4.0),
        )
    assertEquals(listOf("ok"), TrafficRules.relevant(own(), list).map { it.aircraft.icao24 })
  }

  @Test
  fun `converging traffic comes before nearer traffic`() {
    val near = traffic("near", 150.0, 1.5, trackDeg = 150.0, kt = 120.0) // behind, flying away
    val closing = traffic("closing", 60.0, 3.0, trackDeg = 240.0, kt = 110.0) // from 2-3 o'clock towards us
    val order = TrafficRules.relevant(own(), listOf(near, closing)).map { it.aircraft.icao24 }
    assertEquals(listOf("closing", "near"), order)
  }
}

class TrafficAdvisorTest {
  private val config = CalloutConfig(minIntervalMs = 8_000, repeatIntervalMs = 15_000)

  @Test
  fun `one callout at a time with a minimum interval`() {
    val adv = TrafficAdvisor(config)
    val list = listOf(traffic("a", 60.0, 3.0), traffic("b", 300.0, 2.0))
    val first = adv.next(own(), list, 0)!!
    assertEquals("b", first.target.aircraft.icao24) // nearest first (neither converging)
    assertNull(adv.next(own(), list, 5_000)) // too soon
    assertEquals("a", adv.next(own(), list, 8_000)!!.target.aircraft.icao24)
    assertNull(adv.next(own(), list, 20_000)) // both already called, neither closer
  }

  @Test
  fun `repeats an aircraft only when meaningfully closer`() {
    val adv = TrafficAdvisor(config)
    assertTrue(adv.next(own(), listOf(traffic("x", 60.0, 4.0)), 0) != null)
    assertNull(adv.next(own(), listOf(traffic("x", 60.0, 3.5)), 20_000)) // 0.5 NM closer: not enough
    assertTrue(adv.next(own(), listOf(traffic("x", 60.0, 2.9)), 30_000) != null) // >1 NM closer
    assertNull(adv.next(own(), listOf(traffic("x", 60.0, 1.0)), 35_000)) // closer, but within repeat interval
    assertTrue(adv.next(own(), listOf(traffic("x", 60.0, 1.0)), 46_000) != null)
  }

  @Test
  fun `never calls out ownship`() {
    val adv = TrafficAdvisor(config)
    assertNull(adv.next(own(), listOf(traffic("a1b2c3", 0.0, 1.0)), 0))
    assertNull(adv.next(own(icao = null), listOf(traffic("ghost", 0.0, 0.02, relFt = 0.0)), 10_000))
  }
}

class AirfieldsTest {
  private fun field(ident: String, iata: String?, faa: String?, name: String, city: String, bearing: Double, nm: Double, type: AirfieldType = AirfieldType.SMALL) =
      Airfield(ident, ident.takeIf { it.length == 4 && it[0].isLetter() }, iata, faa, name, city, "US", Geo.destination(home, bearing, NavUnits.km(nm)), 10, type)

  private val list =
      listOf(
          field("KPAO", null, "PAO", "Palo Alto Airport", "Palo Alto", 120.0, 1.2),
          field("KSQL", "SQL", "SQL", "San Carlos Airport", "San Carlos", 320.0, 6.0),
          field("KSFO", "SFO", "SFO", "San Francisco International Airport", "San Francisco", 320.0, 20.0, AirfieldType.LARGE),
          field("0Q5", null, "0Q5", "Shelter Cove Airport", "Shelter Cove", 320.0, 200.0),
      )

  @Test
  fun `nearest airport`() {
    val n = Airfields.nearest(home, list).single()
    assertEquals("KPAO", n.airfield.ident)
    assertEquals(1.2, n.distanceNm, 0.01)
    assertEquals(120.0, n.bearingDeg, 0.1)
  }

  @Test
  fun `search by ICAO, FAA, IATA and name`() {
    assertEquals("KPAO", Airfields.search("kpao", list).first().ident)
    assertEquals("KPAO", Airfields.search("PAO", list).first().ident)
    assertEquals("0Q5", Airfields.search("0q5", list).first().ident)
    assertEquals("KSFO", Airfields.search("SFO", list).first().ident)
    // Names: bigger airports first.
    assertEquals(listOf("KSFO", "KSQL"), Airfields.search("san", list).map { it.ident })
    assertEquals(listOf("KSFO"), Airfields.search("francisco", list).map { it.ident })
    assertTrue(Airfields.search("k", list).isEmpty())
  }
}

class PilotTextTest {
  private val dest = Airfield("KSQL", "KSQL", "SQL", "SQL", "San Carlos Airport", "San Carlos", "US", GeoPoint(37.5119, -122.2495), 5, AirfieldType.SMALL)

  @Test
  fun `readouts always label GPS altitude`() {
    assertEquals("112 kt", PilotText.groundspeed(112.4))
    assertEquals("045°", PilotText.track(45.0))
    assertEquals("4,520 ft GPS", PilotText.gpsAltitude(4_517.0))
    assertEquals("— ft GPS", PilotText.gpsAltitude(null))
    assertEquals("12 min", PilotText.ete(12.2))
    assertEquals("1 h 05 min", PilotText.ete(65.0))
    assertEquals("1 hour 5 minutes", PilotText.eteSpoken(65.0))
  }

  @Test
  fun `glasses card has own numbers, direct-to and one alert`() {
    val d = DirectToState(dest, Navigation.directTo(home, dest.position, 100.0, 330.0))
    val alert = TrafficRules.target(own(), traffic("t", 30.0, 3.0, relFt = 500.0))
    val card = PilotText.card(own(), d, null, alert)
    assertEquals("100 kt · TRK 330° · 2,500 GPS", card.title)
    assertTrue(card.route!!.startsWith("→ KSQL "))
    assertEquals("TRAFFIC 2 o'clock · 3.0 NM · +500 ft", card.meta)
  }

  @Test
  fun `spoken summary`() {
    val n = NearestAirfield(dest, 4.0, 300.0)
    assertEquals(
        "Groundspeed 100 knots, track three three zero, GPS altitude 2,500 feet. Nearest airport San Carlos, 4 miles north-west. No traffic nearby.",
        PilotText.summarySpoken(own(), null, n, null))
  }
}
