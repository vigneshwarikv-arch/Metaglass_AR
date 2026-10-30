package com.metaglassar.planes.core.pilot

import com.metaglassar.planes.core.Geo
import com.metaglassar.planes.core.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** One position fix from the phone. [speedMs] and [courseDeg] are the GPS's own (Doppler) values when it gives them. */
data class GpsFix(
    val position: GeoPoint,
    val timeMs: Long,
    val speedMs: Double? = null,
    val courseDeg: Double? = null,
)

data class TrackEstimate(val groundspeedKt: Double, val trackDeg: Double?)

/**
 * Groundspeed and true track from successive GPS fixes, smoothed.
 *
 * Uses the GPS's own speed/course when present (more accurate than differencing positions), otherwise
 * the distance and bearing between the last two fixes. Smoothing is done on the velocity vector
 * (north, east) so track wraps correctly through 360°. Track is null below [MIN_TRACK_SPEED_KT].
 */
class TrackEstimator(private val smoothing: Double = DEFAULT_SMOOTHING) {
  private var last: GpsFix? = null
  private var north = 0.0
  private var east = 0.0
  private var hasVelocity = false

  fun reset() {
    last = null
    hasVelocity = false
  }

  /** Feeds one fix; returns the current estimate, or null until there is enough data. */
  fun update(fix: GpsFix): TrackEstimate? {
    val prev = last
    val gapMs = prev?.let { fix.timeMs - it.timeMs }
    if (gapMs != null && (gapMs <= 0 || gapMs > MAX_GAP_MS)) {
      // Out of order or a long gap (tunnel, app paused): start again.
      hasVelocity = false
    }
    last = fix
    val measured = measuredVelocityKt(fix, prev.takeIf { gapMs != null && gapMs in 1..MAX_GAP_MS })
        ?: return current()
    if (!hasVelocity) {
      north = measured.first
      east = measured.second
      hasVelocity = true
    } else {
      north += smoothing * (measured.first - north)
      east += smoothing * (measured.second - east)
    }
    return current()
  }

  private fun current(): TrackEstimate? {
    if (!hasVelocity) return null
    val gs = hypot(north, east)
    val track = if (gs >= MIN_TRACK_SPEED_KT) Geo.normalizeDeg(Math.toDegrees(atan2(east, north))) else null
    return TrackEstimate(gs, track)
  }

  /** (north, east) in knots. */
  private fun measuredVelocityKt(fix: GpsFix, prev: GpsFix?): Pair<Double, Double>? {
    val speed = fix.speedMs
    val course = fix.courseDeg
    if (speed != null && course != null) return vector(NavUnits.kt(speed), course)
    if (speed != null && NavUnits.kt(speed) < MIN_TRACK_SPEED_KT) return 0.0 to 0.0
    if (prev == null) return null
    val dtSec = (fix.timeMs - prev.timeMs) / 1000.0
    val distNm = NavUnits.nm(Geo.distanceKm(prev.position, fix.position))
    val gsKt = speed?.let(NavUnits::kt) ?: (distNm / dtSec * 3600.0)
    return vector(gsKt, Geo.bearingDeg(prev.position, fix.position))
  }

  companion object {
    const val DEFAULT_SMOOTHING = 0.4
    /** Below this the direction of travel is noise (taxiing slowly, standing still). */
    const val MIN_TRACK_SPEED_KT = 5.0
    const val MAX_GAP_MS = 10_000L

    fun vector(speedKt: Double, trackDeg: Double): Pair<Double, Double> {
      val r = Math.toRadians(trackDeg)
      return speedKt * cos(r) to speedKt * sin(r)
    }
  }
}

/**
 * Direct-to guidance.
 *
 * @property turnDeg Signed angle from our track to the destination (+ = right), for the arrow; null without a track.
 * @property eteMin Time en route at the current groundspeed; null when (nearly) stopped.
 */
data class DirectTo(
    val bearingDeg: Double,
    val distanceNm: Double,
    val eteMin: Double?,
    val turnDeg: Double?,
)

object Navigation {
  const val MIN_GS_FOR_ETE_KT = 5.0

  fun directTo(from: GeoPoint, to: GeoPoint, groundspeedKt: Double?, trackDeg: Double?): DirectTo {
    val bearing = Geo.bearingDeg(from, to)
    val dist = NavUnits.nm(Geo.distanceKm(from, to))
    val ete = groundspeedKt?.takeIf { it >= MIN_GS_FOR_ETE_KT }?.let { dist / it * 60.0 }
    return DirectTo(bearing, dist, ete, trackDeg?.let { Geo.signedBearingDiff(it, bearing) })
  }
}
