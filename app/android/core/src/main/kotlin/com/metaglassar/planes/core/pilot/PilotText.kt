package com.metaglassar.planes.core.pilot

import com.metaglassar.planes.core.AnswerCard
import com.metaglassar.planes.core.CompassPoint
import com.metaglassar.planes.core.Units
import kotlin.math.roundToLong

/** Destination picked for direct-to, with the current guidance to it. */
data class DirectToState(val destination: Airfield, val guidance: DirectTo)

/**
 * Text for pilot-mode readouts, the glasses card and the spoken summary. GPS altitude is ALWAYS
 * labelled "GPS"; nothing here looks like an altimeter or airspeed.
 */
object PilotText {
  const val DASH = "—"

  fun groundspeed(kt: Double?): String = kt?.let { "${it.roundToLong()} kt" } ?: "$DASH kt"

  fun track(deg: Double?): String = deg?.let { "${NavUnits.degreesText(it)}°" } ?: "$DASH°"

  /** "4,520 ft GPS" (nearest 10 ft). */
  fun gpsAltitude(ft: Double?): String =
      ft?.let { "${Units.groupThousands((it / 10.0).roundToLong() * 10)} ft GPS" } ?: "$DASH ft GPS"

  /** "12 min", "1 h 05 min". */
  fun ete(min: Double?): String {
    val m = min?.roundToLong() ?: return "$DASH min"
    return if (m < 60) "$m min" else "${m / 60} h ${(m % 60).toString().padStart(2, '0')} min"
  }

  /** "12 minutes", "1 hour 5 minutes". */
  fun eteSpoken(min: Double): String {
    val m = min.roundToLong()
    fun unit(n: Long, word: String) = if (n == 1L) "1 $word" else "$n ${word}s"
    return if (m < 60) unit(m, "minute") else listOf(unit(m / 60, "hour"), unit(m % 60, "minute")).joinToString(" ")
  }

  /** "→ KPAO 23 NM · 12 min". */
  fun directToLine(d: DirectToState): String =
      "→ ${d.destination.ident} ${TrafficRules.distanceText(d.guidance.distanceNm)} · ${ete(d.guidance.eteMin)}"

  /** "Nearest KSQL 4.1 NM NE". */
  fun nearestLine(n: NearestAirfield): String =
      "Nearest ${n.airfield.ident} ${TrafficRules.distanceText(n.distanceNm)} ${CompassPoint.of(n.bearingDeg).short}"

  /**
   * The three-line Ray-Ban Display card: own numbers, then direct-to (or nearest airport), then one
   * traffic alert (or nearest airport). Minimal on purpose.
   */
  fun card(own: Ownship?, direct: DirectToState?, nearest: NearestAirfield?, alert: TrafficTarget?): AnswerCard {
    val title =
        if (own == null) "No GPS position"
        else "${groundspeed(own.groundspeedKt)} · TRK ${track(own.trackDeg)} · ${gpsAltitude(own.gpsAltitudeFt).removeSuffix(" ft GPS")} GPS"
    val second = direct?.let(::directToLine) ?: nearest?.let(::nearestLine)
    val third =
        alert?.let { "TRAFFIC ${TrafficRules.short(it)}" } ?: if (direct != null) nearest?.let(::nearestLine) else null
    return AnswerCard(title, second, third)
  }

  /** Spoken when the pilot asks the glasses: own numbers, destination, nearest airport, top traffic. */
  fun summarySpoken(own: Ownship?, direct: DirectToState?, nearest: NearestAirfield?, top: TrafficTarget?): String {
    if (own == null) return "No GPS position yet."
    val parts = mutableListOf<String>()
    own.groundspeedKt?.let { parts += "Groundspeed ${it.roundToLong()} knots" }
    own.trackDeg?.let { parts += "track ${NavUnits.degreesSpoken(it)}" }
    own.gpsAltitudeFt?.let { parts += "GPS altitude ${Units.groupThousands((it / 100.0).roundToLong() * 100)} feet" }
    val sentences = mutableListOf<String>()
    if (parts.isNotEmpty()) sentences += parts.joinToString(", ") + "."
    direct?.let { d ->
      val ete = d.guidance.eteMin?.let { ", ${eteSpoken(it)}" } ?: ""
      sentences += "${spokenName(d.destination)}, ${TrafficRules.distanceSpoken(d.guidance.distanceNm)}$ete."
    }
    nearest?.let { n ->
      sentences +=
          "Nearest airport ${spokenName(n.airfield)}, ${TrafficRules.distanceSpoken(n.distanceNm)} ${CompassPoint.of(n.bearingDeg).spoken}."
    }
    sentences += top?.let(TrafficRules::spoken) ?: "No traffic nearby."
    return sentences.joinToString(" ")
  }

  /** Airport name for speech: "Palo Alto" from "Palo Alto Airport". */
  fun spokenName(a: Airfield): String =
      a.name.removeSuffix(" International Airport").removeSuffix(" Airport").removeSuffix(" Airfield").ifBlank { a.ident }
}
