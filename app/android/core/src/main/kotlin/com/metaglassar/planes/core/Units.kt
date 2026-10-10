package com.metaglassar.planes.core

import kotlin.math.roundToLong

/** Distance units. Altitude is always in feet (the aviation standard, also used by flight apps in India). */
enum class DistanceUnit { KILOMETRES, MILES }

object Units {
  const val FEET_PER_METRE = 3.28084
  const val KM_PER_MILE = 1.609344
  const val KMH_PER_MS = 3.6
  const val MPH_PER_MS = 2.236936

  fun feet(metres: Double): Double = metres * FEET_PER_METRE

  fun distance(km: Double, unit: DistanceUnit): Double =
      if (unit == DistanceUnit.MILES) km / KM_PER_MILE else km

  fun speed(ms: Double, unit: DistanceUnit): Double =
      if (unit == DistanceUnit.MILES) ms * MPH_PER_MS else ms * KMH_PER_MS

  fun distanceSymbol(unit: DistanceUnit) = if (unit == DistanceUnit.MILES) "mi" else "km"

  fun speedSymbol(unit: DistanceUnit) = if (unit == DistanceUnit.MILES) "mph" else "km/h"

  /** 32000 -> "32,000". Locale-independent so tests and speech are predictable. */
  fun groupThousands(value: Long): String {
    val digits = kotlin.math.abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (value < 0) "-$grouped" else grouped
  }

  /** Altitude for display: nearest 100 ft. */
  fun altitudeText(metres: Double?): String? =
      metres?.let { groupThousands((feet(it) / 100.0).roundToLong() * 100) + " ft" }

  fun distanceText(km: Double, unit: DistanceUnit): String {
    val d = distance(km, unit)
    val s = if (d < 10) String.format(java.util.Locale.ROOT, "%.1f", d) else d.roundToLong().toString()
    return "$s ${distanceSymbol(unit)}"
  }

  fun speedText(ms: Double?, unit: DistanceUnit): String? =
      ms?.let { "${groupThousands(speed(it, unit).roundToLong())} ${speedSymbol(unit)}" }
}

/** 8-point compass. */
enum class CompassPoint(val short: String, val spoken: String) {
  N("N", "north"),
  NE("NE", "north-east"),
  E("E", "east"),
  SE("SE", "south-east"),
  S("S", "south"),
  SW("SW", "south-west"),
  W("W", "west"),
  NW("NW", "north-west");

  companion object {
    fun of(bearingDeg: Double): CompassPoint {
      val i = ((Geo.normalizeDeg(bearingDeg) + 22.5) / 45.0).toInt() % 8
      return entries[i]
    }
  }
}
