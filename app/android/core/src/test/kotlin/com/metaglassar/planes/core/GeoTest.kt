package com.metaglassar.planes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
  private val chennai = GeoPoint(12.9941, 80.1709) // MAA

  @Test
  fun `aircraft due north 10 km at 3000 m is at bearing 0 and about 16_7 degrees up`() {
    val plane = Geo.destination(chennai, 0.0, 10.0).copy(altitudeM = 3000.0)
    val look = Geo.lookAngle(chennai, plane)
    assertEquals(0.0, Geo.signedBearingDiff(0.0, look.bearingDeg), 0.01)
    assertEquals(10.0, look.groundDistanceKm, 0.01)
    // atan(3000 / 10000) = 16.70°; Earth's curvature lowers it by ~0.04°.
    assertEquals(16.7, look.elevationDeg, 0.1)
  }

  @Test
  fun `bearings for east, south and west`() {
    assertEquals(90.0, Geo.bearingDeg(chennai, Geo.destination(chennai, 90.0, 20.0)), 0.1)
    assertEquals(180.0, Geo.bearingDeg(chennai, Geo.destination(chennai, 180.0, 20.0)), 0.1)
    assertEquals(270.0, Geo.bearingDeg(chennai, Geo.destination(chennai, 270.0, 20.0)), 0.1)
  }

  @Test
  fun `Chennai to Delhi is about 1760 km`() {
    val delhi = GeoPoint(28.5562, 77.1000)
    assertEquals(1757.0, Geo.distanceKm(chennai, delhi), 10.0)
  }

  @Test
  fun `overhead aircraft is at 90 degrees elevation`() {
    assertEquals(90.0, Geo.elevationDeg(chennai, chennai.copy(altitudeM = 1000.0)), 0.001)
  }

  @Test
  fun `low aircraft far away sits below the horizon because of curvature`() {
    val far = Geo.destination(chennai, 45.0, 100.0).copy(altitudeM = 300.0)
    assertTrue(Geo.elevationDeg(chennai, far) < 0.0)
  }

  @Test
  fun `signed bearing difference wraps around north`() {
    assertEquals(20.0, Geo.signedBearingDiff(350.0, 10.0), 1e-9)
    assertEquals(-20.0, Geo.signedBearingDiff(10.0, 350.0), 1e-9)
    assertEquals(180.0, Geo.signedBearingDiff(0.0, 180.0), 1e-9)
  }

  @Test
  fun `angular separation combines bearing and elevation`() {
    assertEquals(0.0, Geo.angularSeparationDeg(Pointing(10.0, 20.0), Pointing(10.0, 20.0)), 1e-6)
    assertEquals(30.0, Geo.angularSeparationDeg(Pointing(0.0, 0.0), Pointing(30.0, 0.0)), 1e-6)
    assertEquals(15.0, Geo.angularSeparationDeg(Pointing(0.0, 10.0), Pointing(0.0, 25.0)), 1e-6)
    // Near the zenith, bearing hardly matters.
    assertTrue(Geo.angularSeparationDeg(Pointing(0.0, 89.0), Pointing(180.0, 89.0)) < 2.1)
  }

  @Test
  fun `bounding box contains the radius`() {
    val box = Geo.boundingBox(chennai, 50.0)
    assertTrue(box.latMax > Geo.destination(chennai, 0.0, 50.0).lat - 1e-9)
    assertTrue(box.lonMax > Geo.destination(chennai, 90.0, 50.0).lon - 1e-6)
    assertTrue(box.latMin < Geo.destination(chennai, 180.0, 50.0).lat + 1e-9)
  }

  @Test
  fun `compass points`() {
    assertEquals(CompassPoint.N, CompassPoint.of(359.0))
    assertEquals(CompassPoint.N, CompassPoint.of(22.0))
    assertEquals(CompassPoint.NE, CompassPoint.of(23.0))
    assertEquals(CompassPoint.W, CompassPoint.of(270.0))
    assertEquals(CompassPoint.NW, CompassPoint.of(-45.0))
  }
}
