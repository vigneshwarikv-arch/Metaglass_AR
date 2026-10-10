package com.metaglassar.planes.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Where something is as seen by an observer: compass bearing, angle above the horizon, distance. */
data class LookAngle(
    /** Degrees clockwise from true north, 0 until 360. */
    val bearingDeg: Double,
    /** Degrees above the horizon (negative = below). */
    val elevationDeg: Double,
    /** Distance along the ground in kilometres. */
    val groundDistanceKm: Double,
)

/** A direction the user is pointing (phone) or looking (glasses). */
data class Pointing(
    /** Degrees clockwise from true north. */
    val bearingDeg: Double,
    /** Degrees above the horizon. */
    val elevationDeg: Double,
)

/** Spherical-Earth geometry. Accurate to well under a degree at the 50–100 km ranges this app uses. */
object Geo {
  const val EARTH_RADIUS_M = 6_371_000.0

  /** Great-circle distance along the ground, in kilometres. */
  fun distanceKm(a: GeoPoint, b: GeoPoint): Double = centralAngleRad(a, b) * EARTH_RADIUS_M / 1000.0

  /** Initial compass bearing from [from] to [to], degrees clockwise from true north (0 until 360). */
  fun bearingDeg(from: GeoPoint, to: GeoPoint): Double {
    val lat1 = rad(from.lat)
    val lat2 = rad(to.lat)
    val dLon = rad(to.lon - from.lon)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return normalizeDeg(deg(atan2(y, x)))
  }

  /**
   * Angle above the observer's horizon at which [target] appears, including Earth's curvature
   * (a plane 50 km away at 3,000 m sits about 0.2° lower than flat-Earth maths would say).
   */
  fun elevationDeg(observer: GeoPoint, target: GeoPoint): Double {
    val theta = centralAngleRad(observer, target)
    val rObs = EARTH_RADIUS_M + observer.altitudeM
    val rTgt = EARTH_RADIUS_M + target.altitudeM
    // Target in the observer's local frame: "up" along the observer's radius, "out" along the ground.
    val up = rTgt * cos(theta) - rObs
    val out = rTgt * sin(theta)
    return deg(atan2(up, out))
  }

  fun lookAngle(observer: GeoPoint, target: GeoPoint): LookAngle =
      LookAngle(
          bearingDeg = bearingDeg(observer, target),
          elevationDeg = elevationDeg(observer, target),
          groundDistanceKm = distanceKm(observer, target),
      )

  /**
   * Angle in degrees between two directions given as (bearing, elevation) — how far the user would
   * have to turn their gaze to go from one to the other.
   */
  fun angularSeparationDeg(a: Pointing, b: Pointing): Double {
    val e1 = rad(a.elevationDeg)
    val e2 = rad(b.elevationDeg)
    val dAz = rad(a.bearingDeg - b.bearingDeg)
    val c = sin(e1) * sin(e2) + cos(e1) * cos(e2) * cos(dAz)
    return deg(acos(c.coerceIn(-1.0, 1.0)))
  }

  /** Signed difference [to] − [from] in degrees, in −180..180 (positive = clockwise / to the right). */
  fun signedBearingDiff(from: Double, to: Double): Double {
    var d = (to - from) % 360.0
    if (d > 180.0) d -= 360.0
    if (d <= -180.0) d += 360.0
    return d
  }

  /** A point [distanceKm] away from [from] along [bearingDeg] (same altitude). */
  fun destination(from: GeoPoint, bearingDeg: Double, distanceKm: Double): GeoPoint {
    val delta = distanceKm * 1000.0 / EARTH_RADIUS_M
    val brg = rad(bearingDeg)
    val lat1 = rad(from.lat)
    val lon1 = rad(from.lon)
    val lat2 = asin(sin(lat1) * cos(delta) + cos(lat1) * sin(delta) * cos(brg))
    val lon2 = lon1 + atan2(sin(brg) * sin(delta) * cos(lat1), cos(delta) - sin(lat1) * sin(lat2))
    return GeoPoint(deg(lat2), normalizeLon(deg(lon2)), from.altitudeM)
  }

  /** Smallest lat/lon box that contains a circle of [radiusKm] around [center]. */
  fun boundingBox(center: GeoPoint, radiusKm: Double): BoundingBox {
    val dLat = deg(radiusKm * 1000.0 / EARTH_RADIUS_M)
    val cosLat = cos(rad(center.lat)).coerceAtLeast(0.01)
    val dLon = (dLat / cosLat).coerceAtMost(180.0)
    return BoundingBox(
        latMin = (center.lat - dLat).coerceAtLeast(-90.0),
        lonMin = center.lon - dLon,
        latMax = (center.lat + dLat).coerceAtMost(90.0),
        lonMax = center.lon + dLon,
    )
  }

  fun normalizeDeg(d: Double): Double {
    val r = d % 360.0
    return if (r < 0) r + 360.0 else r
  }

  private fun normalizeLon(d: Double): Double = ((d + 540.0) % 360.0) - 180.0

  private fun centralAngleRad(a: GeoPoint, b: GeoPoint): Double {
    val dLat = rad(b.lat - a.lat)
    val dLon = rad(b.lon - a.lon)
    val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).let { it * it }
    return 2 * atan2(sqrt(h), sqrt(abs(1 - h)))
  }

  internal fun rad(d: Double) = d * PI / 180.0

  internal fun deg(r: Double) = r * 180.0 / PI
}

data class BoundingBox(val latMin: Double, val lonMin: Double, val latMax: Double, val lonMax: Double)
