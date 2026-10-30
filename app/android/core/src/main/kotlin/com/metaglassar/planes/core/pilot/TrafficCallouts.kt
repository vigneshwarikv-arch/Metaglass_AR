package com.metaglassar.planes.core.pilot

import com.metaglassar.planes.core.Aircraft
import com.metaglassar.planes.core.CompassPoint
import com.metaglassar.planes.core.Geo
import com.metaglassar.planes.core.Units
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

enum class VerticalTrend { CLIMBING, DESCENDING }

/**
 * A traffic aircraft as seen from ownship.
 *
 * @property clock 1..12 o'clock relative to our track (12 = ahead); null when we have no track.
 * @property relativeAltitudeFt Traffic minus ownship, unrounded; null when either altitude is unknown.
 * @property closureKt How fast the distance is shrinking (positive = getting closer); null if velocities unknown.
 */
data class TrafficTarget(
    val aircraft: Aircraft,
    val distanceNm: Double,
    val bearingDeg: Double,
    val clock: Int?,
    val relativeAltitudeFt: Double?,
    val trend: VerticalTrend?,
    val closureKt: Double?,
) {
  val converging: Boolean get() = (closureKt ?: 0.0) > TrafficRules.CONVERGING_KT

  /** Relative altitude rounded to the nearest 100 ft, or null. */
  val relativeAltitudeRoundedFt: Int?
    get() = relativeAltitudeFt?.let { ((it / 100.0).roundToLong() * 100).toInt() }
}

/** Thresholds for traffic callouts. Users can change range and altitude band in Settings. */
data class CalloutConfig(
    val maxRangeNm: Double = TrafficRules.DEFAULT_RANGE_NM,
    val maxAltitudeDiffFt: Double = TrafficRules.DEFAULT_ALTITUDE_BAND_FT,
    /** At most one callout this often, whatever the traffic. */
    val minIntervalMs: Long = 8_000,
    /** The same aircraft is not called again sooner than this… */
    val repeatIntervalMs: Long = 15_000,
    /** …and only if it is now at most this fraction of the distance it was last called at… */
    val repeatCloserFraction: Double = 0.7,
    /** …or at least this much closer. */
    val repeatCloserNm: Double = 1.0,
    /** Forget aircraft we haven't seen for this long. */
    val forgetAfterMs: Long = 60_000,
)

data class Callout(val target: TrafficTarget, val spoken: String, val short: String)

object TrafficRules {
  const val DEFAULT_RANGE_NM = 5.0
  const val DEFAULT_ALTITUDE_BAND_FT = 2_000.0
  /** Within this vertical distance traffic is "same altitude". */
  const val SAME_ALTITUDE_FT = 200.0
  /** Climb/descent rate that is worth saying. */
  const val TREND_FPM = 500.0
  /** Closure rate above which traffic counts as converging. */
  const val CONVERGING_KT = 10.0
  /** Traffic this close to us (horizontally AND vertically) is our own aircraft seen again (e.g. a TIS-B "shadow"). */
  const val OWNSHIP_RADIUS_NM = 0.1
  const val OWNSHIP_ALTITUDE_FT = 300.0

  /** Clock position (1..12) of a relative bearing in degrees: 0 = 12 o'clock, +90 = 3 o'clock. */
  fun clock(relativeBearingDeg: Double): Int {
    val h = (Geo.normalizeDeg(relativeBearingDeg) / 30.0).roundToInt() % 12
    return if (h == 0) 12 else h
  }

  /** Where [aircraft] is relative to [own]. */
  fun target(own: Ownship, aircraft: Aircraft): TrafficTarget {
    val bearing = Geo.bearingDeg(own.position, aircraft.position)
    val distNm = NavUnits.nm(Geo.distanceKm(own.position, aircraft.position))
    val rel = aircraft.altitudeM?.let { a -> own.referenceAltitudeFt?.let { o -> Units.feet(a) - o } }
    val fpm = aircraft.verticalRateMs?.let { it * NavUnits.FPM_PER_MS }
    val trend =
        when {
          fpm == null -> null
          fpm > TREND_FPM -> VerticalTrend.CLIMBING
          fpm < -TREND_FPM -> VerticalTrend.DESCENDING
          else -> null
        }
    return TrafficTarget(
        aircraft = aircraft,
        distanceNm = distNm,
        bearingDeg = bearing,
        clock = own.trackDeg?.let { clock(bearing - it) },
        relativeAltitudeFt = rel,
        trend = trend,
        closureKt = closureKt(own, aircraft, bearing, distNm),
    )
  }

  /** Rate at which the distance shrinks, from both velocity vectors (flat-Earth; fine within a few NM). */
  private fun closureKt(own: Ownship, a: Aircraft, bearingDeg: Double, distNm: Double): Double? {
    if (distNm < 1e-3) return null
    val speed = a.speedMs ?: return null
    val track = a.trackDeg ?: return null
    val (tn, te) = TrackEstimator.vector(NavUnits.kt(speed), track)
    val (on, oe) =
        if (own.groundspeedKt != null && own.trackDeg != null) TrackEstimator.vector(own.groundspeedKt, own.trackDeg)
        else 0.0 to 0.0
    val b = Math.toRadians(bearingDeg)
    // Unit vector from us to the traffic, (north, east); closure = −(relative velocity · unit vector).
    return -((tn - on) * cos(b) + (te - oe) * sin(b))
  }

  /** True if [a] is really us: same ICAO address, or sitting on top of us. */
  fun isOwnship(own: Ownship, a: Aircraft, target: TrafficTarget): Boolean {
    if (own.icaoAddress != null && a.icao24.equals(own.icaoAddress, ignoreCase = true)) return true
    val rel = target.relativeAltitudeFt
    return target.distanceNm < OWNSHIP_RADIUS_NM && (rel == null || abs(rel) < OWNSHIP_ALTITUDE_FT)
  }

  /**
   * Traffic worth showing, most important first: airborne, within range and altitude band, not us.
   * Converging traffic comes first; then nearest first.
   */
  fun relevant(own: Ownship, traffic: List<Aircraft>, config: CalloutConfig = CalloutConfig()): List<TrafficTarget> =
      traffic
          .asSequence()
          .filter { !it.onGround }
          .map { target(own, it) }
          .filter { !isOwnship(own, it.aircraft, it) }
          .filter { it.distanceNm <= config.maxRangeNm }
          .filter { t -> t.relativeAltitudeFt.let { it == null || abs(it) <= config.maxAltitudeDiffFt } }
          .sortedWith(compareByDescending<TrafficTarget> { it.converging }.thenBy { it.distanceNm })
          .toList()

  // region phrases

  /** "Traffic, 2 o'clock, 3 miles, 500 above, climbing." */
  fun spoken(t: TrafficTarget): String {
    val where = t.clock?.let { "$it o'clock" } ?: CompassPoint.of(t.bearingDeg).spoken
    val parts = mutableListOf("Traffic", where, distanceSpoken(t.distanceNm), altitudeSpoken(t))
    t.trend?.let { parts += if (it == VerticalTrend.CLIMBING) "climbing" else "descending" }
    return parts.joinToString(", ") + "."
  }

  /** "2 o'clock · 3.2 NM · +500 ft ↑", for cards and lists. */
  fun short(t: TrafficTarget): String {
    val where = t.clock?.let { "$it o'clock" } ?: CompassPoint.of(t.bearingDeg).short
    val alt =
        when (val r = t.relativeAltitudeFt) {
          null -> "alt ?"
          else -> if (abs(r) < SAME_ALTITUDE_FT) "same alt" else signedFeet(t.relativeAltitudeRoundedFt!!)
        }
    val arrow = when (t.trend) { VerticalTrend.CLIMBING -> " ↑"; VerticalTrend.DESCENDING -> " ↓"; null -> "" }
    return "$where · ${distanceText(t.distanceNm)} · $alt$arrow"
  }

  fun distanceSpoken(nm: Double): String {
    if (nm < 1.0) return "less than 1 mile"
    val n = nm.roundToLong()
    return if (n == 1L) "1 mile" else "$n miles"
  }

  /** "3.2 NM" under 10 NM, "23 NM" above. */
  fun distanceText(nm: Double): String =
      if (nm < 10) String.format(java.util.Locale.ROOT, "%.1f NM", nm) else "${nm.roundToLong()} NM"

  private fun altitudeSpoken(t: TrafficTarget): String {
    val r = t.relativeAltitudeFt ?: return "altitude unknown"
    if (abs(r) < SAME_ALTITUDE_FT) return "same altitude"
    val rounded = t.relativeAltitudeRoundedFt!!
    return "${Units.groupThousands(abs(rounded).toLong())} ${if (rounded > 0) "above" else "below"}"
  }

  private fun signedFeet(ft: Int) = (if (ft > 0) "+" else "−") + Units.groupThousands(abs(ft).toLong()) + " ft"

  // endregion
}

/**
 * Decides when to speak a traffic callout: one at a time, most important first, and the same aircraft
 * again only when it has come meaningfully closer. Call [next] about once a second.
 */
class TrafficAdvisor(var config: CalloutConfig = CalloutConfig()) {
  private data class Announced(val distanceNm: Double, val atMs: Long)

  private val announced = mutableMapOf<String, Announced>()
  private val lastSeen = mutableMapOf<String, Long>()
  private var lastCalloutMs: Long? = null

  fun reset() {
    announced.clear()
    lastSeen.clear()
    lastCalloutMs = null
  }

  /** The callout to speak now, or null. Records it as spoken. */
  fun next(own: Ownship, traffic: List<Aircraft>, nowMs: Long): Callout? {
    traffic.forEach { lastSeen[it.icao24] = nowMs }
    lastSeen.entries.removeAll { (id, seen) -> (nowMs - seen > config.forgetAfterMs).also { if (it) announced.remove(id) } }

    lastCalloutMs?.let { if (nowMs - it < config.minIntervalMs) return null }
    val pick = TrafficRules.relevant(own, traffic, config).firstOrNull { worthSaying(it, nowMs) } ?: return null
    announced[pick.aircraft.icao24] = Announced(pick.distanceNm, nowMs)
    lastCalloutMs = nowMs
    return Callout(pick, TrafficRules.spoken(pick), TrafficRules.short(pick))
  }

  private fun worthSaying(t: TrafficTarget, nowMs: Long): Boolean {
    val last = announced[t.aircraft.icao24] ?: return true
    if (nowMs - last.atMs < config.repeatIntervalMs) return false
    return t.distanceNm <= last.distanceNm * config.repeatCloserFraction ||
        last.distanceNm - t.distanceNm >= config.repeatCloserNm
  }
}
