package com.metaglassar.planes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetSelectorTest {
  private val user = GeoPoint(37.4323, -121.8996) // Milpitas, CA

  private fun plane(id: String, bearing: Double, km: Double, altM: Double?, onGround: Boolean = false) =
      Aircraft(
          icao24 = id,
          callsign = id.uppercase(),
          position = Geo.destination(user, bearing, km),
          altitudeM = altM,
          speedMs = 200.0,
          trackDeg = 0.0,
          onGround = onGround,
      )

  private val north = plane("north", 0.0, 10.0, 3000.0) // ~16.7° up
  private val east = plane("east", 90.0, 20.0, 9000.0) // ~24° up
  private val southWest = plane("sw", 225.0, 40.0, 10000.0) // ~14° up

  @Test
  fun `pointing at a plane selects it`() {
    val result = TargetSelector.select(user, listOf(north, east, southWest), Pointing(2.0, 15.0))
    assertTrue(result is Selection.Hit)
    assertEquals("north", (result as Selection.Hit).target.aircraft.icao24)
    assertTrue(result.offsetDeg < 3.0)
  }

  @Test
  fun `pointing within tolerance still selects`() {
    val result = TargetSelector.select(user, listOf(north, east), Pointing(10.0, 20.0), toleranceDeg = 12.0)
    assertEquals("north", (result as Selection.Hit).target.aircraft.icao24)
  }

  @Test
  fun `pointing where there is nothing reports the nearest and which way to turn`() {
    // Facing north-west, nothing there; the north plane is about 45° to the right.
    val result = TargetSelector.select(user, listOf(north, east), Pointing(315.0, 16.0))
    assertTrue(result is Selection.Miss)
    result as Selection.Miss
    assertEquals("north", result.nearest.aircraft.icao24)
    assertEquals(TurnDirection.RIGHT, result.turn)
    assertEquals(45.0, result.turnDeg, 1.0)
  }

  @Test
  fun `turn left when the nearest plane is counter-clockwise`() {
    val result = TargetSelector.select(user, listOf(east), Pointing(150.0, 24.0)) as Selection.Miss
    assertEquals(TurnDirection.LEFT, result.turn)
    assertEquals(60.0, result.turnDeg, 1.0)
  }

  @Test
  fun `look up when the plane is straight ahead but higher`() {
    val result = TargetSelector.select(user, listOf(north), Pointing(0.0, -10.0)) as Selection.Miss
    assertEquals(TurnDirection.UP, result.turn)
    assertEquals(26.7, result.turnDeg, 0.5)
  }

  @Test
  fun `aircraft on the ground or without altitude are ignored`() {
    val parked = plane("parked", 0.0, 2.0, 0.0, onGround = true)
    val noAlt = plane("noalt", 0.0, 3.0, null)
    assertEquals(Selection.NoAircraft, TargetSelector.select(user, listOf(parked, noAlt), Pointing(0.0, 0.0)))
  }

  @Test
  fun `empty sky`() {
    assertEquals(Selection.NoAircraft, TargetSelector.select(user, emptyList(), Pointing(0.0, 30.0)))
  }

  @Test
  fun `of two planes in the same direction the nearer wins`() {
    val near = plane("near", 0.0, 5.0, 1500.0) // ~16.7°
    val far = plane("far", 0.0, 30.0, 9000.0) // ~16.6°
    val result = TargetSelector.select(user, listOf(far, near), Pointing(0.0, 16.7)) as Selection.Hit
    assertEquals("near", result.target.aircraft.icao24)
  }

  @Test
  fun `nearby list is sorted by distance and limited to the radius`() {
    val list = TargetSelector.nearby(user, listOf(southWest, east, north), radiusKm = 30.0)
    assertEquals(listOf("north", "east"), list.map { it.aircraft.icao24 })
  }
}
