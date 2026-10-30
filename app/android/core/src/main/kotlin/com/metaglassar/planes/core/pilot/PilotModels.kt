package com.metaglassar.planes.core.pilot

import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.core.Units
import kotlin.math.roundToInt

/*
 * Pilot mode (MVP 2). A SUPPLEMENTARY situational-awareness aid only — never a primary or certified
 * instrument. VFR, daytime. GPS altitude is always labelled "GPS". See docs/08-mvp2-pilot-mode.md.
 */

/** Aviation units used throughout pilot mode: knots, nautical miles, feet. */
object NavUnits {
  const val METRES_PER_NM = 1852.0
  const val MS_PER_KT = 1852.0 / 3600.0
  const val FPM_PER_MS = 60.0 * Units.FEET_PER_METRE

  fun nm(km: Double): Double = km * 1000.0 / METRES_PER_NM

  fun km(nm: Double): Double = nm * METRES_PER_NM / 1000.0

  fun kt(ms: Double): Double = ms / MS_PER_KT

  fun ms(kt: Double): Double = kt * MS_PER_KT

  fun feetToMetres(ft: Double): Double = ft / Units.FEET_PER_METRE

  /** Track or bearing as pilots write it: three digits, north is "360". */
  fun degreesText(deg: Double): String {
    val d = ((deg.roundToInt() % 360) + 360) % 360
    return if (d == 0) "360" else d.toString().padStart(3, '0')
  }

  /** "045" -> "zero four five", for speech. */
  fun degreesSpoken(deg: Double): String =
      degreesText(deg).map { DIGITS[it - '0'] }.joinToString(" ")

  private val DIGITS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "niner")
}

/** What a GPS altitude is measured from. Stratux sends height above the WGS-84 ellipsoid. */
enum class AltitudeDatum { MSL, ELLIPSOID }

/** Where ownship position and track come from. */
enum class PositionSource { RECEIVER, PHONE }

/**
 * Our own aircraft, from the ADS-B receiver's ownship report or the phone's GPS.
 *
 * @property trackDeg True track; null when unknown or too slow to tell (see [TrackEstimator.MIN_TRACK_SPEED_KT]).
 * @property gpsAltitudeFt GPS altitude. Always shown labelled "GPS"; check [gpsAltitudeDatum].
 * @property pressureAltitudeFt Pressure altitude (29.92 inHg), from the receiver's baro sensor if it has one.
 * @property icaoAddress Our transponder address, lowercase hex, so we never call ourselves out as traffic.
 */
data class Ownship(
    val position: GeoPoint,
    val groundspeedKt: Double?,
    val trackDeg: Double?,
    val gpsAltitudeFt: Double?,
    val gpsAltitudeDatum: AltitudeDatum = AltitudeDatum.MSL,
    val pressureAltitudeFt: Double? = null,
    val verticalRateFpm: Double? = null,
    val icaoAddress: String? = null,
    val nic: Int? = null,
    val nacp: Int? = null,
    val source: PositionSource,
    val timeMs: Long,
) {
  /**
   * Altitude to compare with traffic. Traffic reports carry PRESSURE altitude, so ownship pressure
   * altitude is the right reference; GPS altitude is the fallback and can differ by a few hundred feet.
   */
  val referenceAltitudeFt: Double? get() = pressureAltitudeFt ?: gpsAltitudeFt
}

enum class AirfieldType { LARGE, MEDIUM, SMALL, SEAPLANE }

/**
 * One airport from the bundled OurAirports dataset (public domain).
 *
 * @property ident What we show: ICAO code (KPAO, VOMM), or the FAA identifier for US fields without one (0B5).
 */
data class Airfield(
    val ident: String,
    val icao: String?,
    val iata: String?,
    val faa: String?,
    val name: String,
    val city: String?,
    val country: String,
    val position: GeoPoint,
    val elevationFt: Int?,
    val type: AirfieldType,
)
