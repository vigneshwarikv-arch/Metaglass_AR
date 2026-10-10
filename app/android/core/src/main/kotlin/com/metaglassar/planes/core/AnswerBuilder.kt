package com.metaglassar.planes.core

import kotlin.math.roundToLong

/** A small card for screens on glasses (Meta Ray-Ban Display). Three short lines at most. */
data class AnswerCard(
    val title: String,
    val route: String?,
    val meta: String?,
)

/** What an output connector delivers: one short spoken sentence, plus an optional card. */
data class Answer(
    val spoken: String,
    val card: AnswerCard?,
)

/**
 * Builds the one-sentence spoken answer, e.g.
 * "IndiGo A320, Delhi to Chennai, 32,000 feet, 25 kilometres north."
 * Missing facts are left out rather than read as "unknown", so the sentence stays short.
 */
object AnswerBuilder {

  fun answer(selection: Selection, unit: DistanceUnit = DistanceUnit.KILOMETRES): Answer =
      when (selection) {
        is Selection.Hit -> Answer(describe(selection.target, unit), card(selection.target, unit))
        is Selection.Miss -> Answer(miss(selection, unit), card(selection.nearest, unit))
        Selection.NoAircraft -> Answer("No planes near you right now.", null)
      }

  /** "IndiGo A320, Delhi to Chennai, 32,000 feet, 25 kilometres north." */
  fun describe(seen: SeenAircraft, unit: DistanceUnit): String {
    val parts = mutableListOf(who(seen.aircraft))
    routeSpoken(seen.aircraft.info.route)?.let(parts::add)
    seen.aircraft.altitudeM?.let { parts.add("${altitudeSpoken(it)} feet") }
    parts.add("${distanceSpoken(seen.look.groundDistanceKm, unit)} ${CompassPoint.of(seen.look.bearingDeg).spoken}")
    return parts.joinToString(", ") + "."
  }

  /** "No plane that way. Nearest is IndiGo A320, 12 kilometres east. Turn right about 40 degrees." */
  fun miss(miss: Selection.Miss, unit: DistanceUnit): String {
    val seen = miss.nearest
    val where = "${distanceSpoken(seen.look.groundDistanceKm, unit)} ${CompassPoint.of(seen.look.bearingDeg).spoken}"
    val degrees = roundTo(miss.turnDeg, 5).coerceAtLeast(5)
    val turn =
        when (miss.turn) {
          TurnDirection.LEFT -> "Turn left about $degrees degrees."
          TurnDirection.RIGHT -> "Turn right about $degrees degrees."
          TurnDirection.UP -> "Look up about $degrees degrees."
          TurnDirection.DOWN -> "Look lower, about $degrees degrees."
        }
    return "No plane that way. Nearest is ${who(seen.aircraft)}, $where. $turn"
  }

  fun card(seen: SeenAircraft, unit: DistanceUnit): AnswerCard {
    val a = seen.aircraft
    val title = listOfNotNull(a.info.airline ?: a.callsign, AircraftTypes.spokenName(a.info)).joinToString(" · ")
        .ifEmpty { "Unknown aircraft" }
    val route = a.info.route?.let { r ->
      val from = r.origin?.code
      val to = r.destination?.code
      if (from == null && to == null) null else "${from ?: "?"} → ${to ?: "?"}"
    }
    val meta = listOfNotNull(
        Units.altitudeText(a.altitudeM),
        "${Units.distanceText(seen.look.groundDistanceKm, unit)} ${CompassPoint.of(seen.look.bearingDeg).short}",
    ).joinToString(" · ")
    return AnswerCard(title, route, meta)
  }

  /** Airline + type ("IndiGo A320"), else callsign ("Flight IGO2045"), else "An unknown aircraft". */
  fun who(a: Aircraft): String {
    val type = AircraftTypes.spokenName(a.info)
    return when {
      a.info.airline != null && type != null -> "${a.info.airline} $type"
      a.info.airline != null -> a.info.airline
      a.callsign != null && type != null -> "Flight ${a.callsign}, $type"
      a.callsign != null -> "Flight ${a.callsign}"
      type != null -> "A $type"
      else -> "An unknown aircraft"
    }
  }

  fun routeSpoken(route: Route?): String? {
    val from = route?.origin?.spokenName
    val to = route?.destination?.spokenName
    return when {
      from != null && to != null -> "$from to $to"
      to != null -> "to $to"
      from != null -> "from $from"
      else -> null
    }
  }

  /** Nearest 1,000 ft at cruise (above 10,000 ft), nearest 100 ft below that. */
  fun altitudeSpoken(metres: Double): String {
    val ft = Units.feet(metres)
    val rounded = if (ft >= 10_000) roundTo(ft, 1000) else roundTo(ft, 100)
    return Units.groupThousands(rounded)
  }

  fun distanceSpoken(km: Double, unit: DistanceUnit): String {
    val d = Units.distance(km, unit)
    val word = if (unit == DistanceUnit.MILES) "mile" else "kilometre"
    if (d < 1.0) return "less than 1 $word"
    val n = d.roundToLong()
    return if (n == 1L) "1 $word" else "${Units.groupThousands(n)} ${word}s"
  }

  private fun roundTo(value: Double, step: Int): Long = (value / step).roundToLong() * step
}
