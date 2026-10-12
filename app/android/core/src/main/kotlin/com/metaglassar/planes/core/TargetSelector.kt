package com.metaglassar.planes.core

import kotlin.math.abs

/** An aircraft together with where it appears from the user's position. */
data class SeenAircraft(
    val aircraft: Aircraft,
    val look: LookAngle,
)

enum class TurnDirection { LEFT, RIGHT, UP, DOWN }

/** Result of "which plane am I pointing at?". */
sealed interface Selection {
  /** An aircraft lies within the tolerance of the pointing direction. */
  data class Hit(val target: SeenAircraft, val offsetDeg: Double) : Selection

  /**
   * Nothing within the tolerance. [nearest] is the aircraft closest to the pointing direction; the
   * user should turn [turn] by about [turnDeg] degrees to face it.
   */
  data class Miss(val nearest: SeenAircraft, val turn: TurnDirection, val turnDeg: Double) : Selection

  /** No aircraft are available at all. */
  data object NoAircraft : Selection
}

object TargetSelector {
  /** Default half-width of the "pointing cone". Phone compasses are often off by 5–10°. */
  const val DEFAULT_TOLERANCE_DEG = 12.0

  /** Planes this far below the horizon are hidden by terrain and buildings; ignore them. */
  const val MIN_ELEVATION_DEG = -1.0

  /** Every aircraft within [radiusKm] of [user], nearest first. */
  fun nearby(user: GeoPoint, aircraft: List<Aircraft>, radiusKm: Double): List<SeenAircraft> =
      aircraft
          .map { SeenAircraft(it, Geo.lookAngle(user, it.position3d)) }
          .filter { it.look.groundDistanceKm <= radiusKm }
          .sortedBy { it.look.groundDistanceKm }

  /**
   * Picks the aircraft closest to [pointing]. Only airborne aircraft above the horizon count.
   * Ties within a degree go to the nearer aircraft (it is more likely the one the user sees).
   */
  fun select(
      user: GeoPoint,
      aircraft: List<Aircraft>,
      pointing: Pointing,
      toleranceDeg: Double = DEFAULT_TOLERANCE_DEG,
  ): Selection {
    val candidates =
        aircraft
            .filter { !it.onGround && it.altitudeM != null }
            .map { SeenAircraft(it, Geo.lookAngle(user, it.position3d)) }
            .filter { it.look.elevationDeg >= MIN_ELEVATION_DEG }
    if (candidates.isEmpty()) return Selection.NoAircraft

    val scored =
        candidates
            .map { it to Geo.angularSeparationDeg(pointing, Pointing(it.look.bearingDeg, it.look.elevationDeg)) }
            .sortedWith(compareBy({ Math.round(it.second) }, { it.first.look.groundDistanceKm }))
    val (best, offset) = scored.first()
    if (offset <= toleranceDeg) return Selection.Hit(best, offset)

    val dBearing = Geo.signedBearingDiff(pointing.bearingDeg, best.look.bearingDeg)
    val dElevation = best.look.elevationDeg - pointing.elevationDeg
    return if (abs(dBearing) >= abs(dElevation)) {
      Selection.Miss(best, if (dBearing >= 0) TurnDirection.RIGHT else TurnDirection.LEFT, abs(dBearing))
    } else {
      Selection.Miss(best, if (dElevation >= 0) TurnDirection.UP else TurnDirection.DOWN, abs(dElevation))
    }
  }
}
