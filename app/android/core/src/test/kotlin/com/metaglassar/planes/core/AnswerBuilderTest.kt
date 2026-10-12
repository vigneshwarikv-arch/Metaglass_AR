package com.metaglassar.planes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnswerBuilderTest {
  private val user = GeoPoint(12.9941, 80.1709)
  private val delhi = Airport(icao = "VIDP", iata = "DEL", name = "Indira Gandhi International", city = "Delhi")
  private val chennai = Airport(icao = "VOMM", iata = "MAA", name = "Chennai International", city = "Chennai")

  private fun seen(
      altFt: Double? = 32_000.0,
      km: Double = 25.0,
      bearing: Double = 0.0,
      info: AircraftInfo = AircraftInfo(airline = "IndiGo", typeCode = "A320", route = Route(delhi, chennai)),
      callsign: String? = "IGO2045",
  ): SeenAircraft {
    val a = Aircraft(
        icao24 = "800abc",
        callsign = callsign,
        position = Geo.destination(user, bearing, km),
        altitudeM = altFt?.let { it / Units.FEET_PER_METRE },
        speedMs = 230.0,
        trackDeg = 180.0,
        info = info,
    )
    return SeenAircraft(a, Geo.lookAngle(user, a.position3d))
  }

  @Test
  fun `full answer matches the spec example`() {
    val answer = AnswerBuilder.answer(Selection.Hit(seen(), 2.0))
    assertEquals("IndiGo A320, Delhi to Chennai, 32,000 feet, 25 kilometres north.", answer.spoken)
  }

  @Test
  fun `miles setting`() {
    val s = AnswerBuilder.describe(seen(km = 16.09344 * 2, bearing = 135.0), DistanceUnit.MILES)
    assertEquals("IndiGo A320, Delhi to Chennai, 32,000 feet, 20 miles south-east.", s)
  }

  @Test
  fun `low altitude rounds to 100 ft and singular distance`() {
    val s = AnswerBuilder.describe(seen(altFt = 4_440.0, km = 1.2, bearing = 270.0), DistanceUnit.KILOMETRES)
    assertEquals("IndiGo A320, Delhi to Chennai, 4,400 feet, 1 kilometre west.", s)
  }

  @Test
  fun `missing route and airline fall back to callsign and type`() {
    val s = AnswerBuilder.describe(seen(info = AircraftInfo(typeCode = "B77W")), DistanceUnit.KILOMETRES)
    assertEquals("Flight IGO2045, 777, 32,000 feet, 25 kilometres north.", s)
  }

  @Test
  fun `nothing known at all`() {
    val s = AnswerBuilder.describe(seen(altFt = null, info = AircraftInfo(), callsign = null), DistanceUnit.KILOMETRES)
    assertEquals("An unknown aircraft, 25 kilometres north.", s)
  }

  @Test
  fun `only destination known`() {
    assertEquals("to Delhi", AnswerBuilder.routeSpoken(Route(destination = delhi)))
  }

  @Test
  fun `neo types are spoken nicely`() {
    val s = AnswerBuilder.describe(seen(info = AircraftInfo(airline = "Air India", typeCode = "A20N")), DistanceUnit.KILOMETRES)
    assertEquals("Air India A320neo, 32,000 feet, 25 kilometres north.", s)
  }

  @Test
  fun `miss tells the user which way to turn`() {
    val answer = AnswerBuilder.answer(Selection.Miss(seen(km = 12.0, bearing = 90.0), TurnDirection.RIGHT, 38.0))
    assertEquals("No plane that way. Nearest is IndiGo A320, 12 kilometres east. Turn right about 40 degrees.", answer.spoken)
  }

  @Test
  fun `no aircraft`() {
    val answer = AnswerBuilder.answer(Selection.NoAircraft)
    assertEquals("No planes near you right now.", answer.spoken)
    assertNull(answer.card)
  }

  @Test
  fun `display card is short`() {
    val card = AnswerBuilder.card(seen(), DistanceUnit.KILOMETRES)
    assertEquals("IndiGo · A320", card.title)
    assertEquals("DEL → MAA", card.route)
    assertEquals("32,000 ft · 25 km N", card.meta)
  }
}
