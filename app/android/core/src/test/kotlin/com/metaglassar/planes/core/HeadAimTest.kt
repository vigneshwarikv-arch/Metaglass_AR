package com.metaglassar.planes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class HeadAimTest {
  private val aim = HeadAim()
  private val g = 9.81
  private val levelHead = Vec3(0.0, g, 0.0)

  @Test
  fun `level head facing magnetic north`() {
    // Field points forward (−Z) and dips down (−Y).
    val field = Vec3(0.0, -40.0, -25.0)
    assertEquals(0.0, aim.elevationDeg(levelHead)!!, 1e-6)
    assertEquals(0.0, Geo.signedBearingDiff(0.0, aim.magneticBearingDeg(levelHead, field)!!), 1e-6)
  }

  @Test
  fun `level head facing east sees north on its left`() {
    val field = Vec3(-25.0, -40.0, 0.0) // north is −X (left)
    assertEquals(90.0, aim.magneticBearingDeg(levelHead, field)!!, 1e-6)
  }

  @Test
  fun `head tilted up 30 degrees`() {
    val t = Math.toRadians(30.0)
    val accel = Vec3(0.0, g * cos(t), -g * sin(t))
    assertEquals(30.0, aim.elevationDeg(accel)!!, 1e-6)
  }

  @Test
  fun `declination converts magnetic to true north`() {
    val field = Vec3(0.0, -40.0, -25.0)
    val p = aim.pointing(levelHead, field, declinationDeg = 13.0)!!
    assertEquals(13.0, p.bearingDeg, 1e-6)
  }

  @Test
  fun `free fall has no answer`() {
    assertNull(aim.elevationDeg(Vec3(0.0, 0.0, 0.0)))
  }
}
