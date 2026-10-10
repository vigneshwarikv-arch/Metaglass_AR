package com.metaglassar.planes.core

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/** Three components in a sensor's own axes. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
  fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z

  fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

  val length: Double get() = sqrt(dot(this))

  fun normalized(): Vec3? = length.takeIf { it > 1e-9 }?.let { Vec3(x / it, y / it, z / it) }

  /** This vector with its component along unit vector [axis] removed. */
  fun perpendicularTo(axis: Vec3): Vec3? {
    val along = dot(axis)
    return Vec3(x - along * axis.x, y - along * axis.y, z - along * axis.z).takeIf { it.length > 1e-9 }
  }
}

/**
 * Turns one glasses IMU reading (accelerometer + magnetometer) into where the wearer is looking.
 *
 * Standard tilt-compensated compass: at rest the accelerometer reads "up" (reaction to gravity);
 * the magnetic field with its vertical part removed is magnetic north; north × up is east.
 *
 * Axis convention: Meta's BirdSpotter sample (meta-wearables-dat-android) measured on worn glasses
 * that +Y is up and −Z is forward. TODO(glasses): re-check on our test pair — look level at the
 * horizon and confirm the app reports ~0° elevation; if not, change [forward].
 */
class HeadAim(private val forward: Vec3 = Vec3(0.0, 0.0, -1.0)) {

  /** Degrees above the horizon, or null in free fall. */
  fun elevationDeg(acceleration: Vec3): Double? {
    val up = acceleration.normalized() ?: return null
    return Math.toDegrees(asin(forward.dot(up).coerceIn(-1.0, 1.0)))
  }

  /** Degrees clockwise from MAGNETIC north, or null when it can't be known (e.g. looking straight up). */
  fun magneticBearingDeg(acceleration: Vec3, magneticField: Vec3): Double? {
    val up = acceleration.normalized() ?: return null
    val north = magneticField.perpendicularTo(up)?.normalized() ?: return null
    val aim = forward.perpendicularTo(up)?.normalized() ?: return null
    val east = north.cross(up)
    return Geo.normalizeDeg(Math.toDegrees(atan2(aim.dot(east), aim.dot(north))))
  }

  /**
   * Where the wearer is looking, relative to TRUE north. [declinationDeg] is the local magnetic
   * declination (east positive), which the phone app gets from its location.
   */
  fun pointing(acceleration: Vec3, magneticField: Vec3, declinationDeg: Double): Pointing? {
    val elevation = elevationDeg(acceleration) ?: return null
    val bearing = magneticBearingDeg(acceleration, magneticField) ?: return null
    return Pointing(Geo.normalizeDeg(bearing + declinationDeg), elevation)
  }
}
