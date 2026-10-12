package com.metaglassar.planes.core

/** A point on Earth. [altitudeM] is metres above mean sea level. */
data class GeoPoint(
    val lat: Double,
    val lon: Double,
    val altitudeM: Double = 0.0,
)

data class Airport(
    val icao: String? = null,
    val iata: String? = null,
    val name: String? = null,
    /** City or municipality, used in spoken answers ("Chennai to Delhi"). */
    val city: String? = null,
) {
  /** Short code for cards: IATA first (MAA), then ICAO (VOMM). */
  val code: String? get() = iata ?: icao

  /** Name for speech: city first, then airport name, then code. */
  val spokenName: String? get() = city ?: name ?: code
}

data class Route(
    val origin: Airport? = null,
    val destination: Airport? = null,
)

/**
 * Extra facts about a flight that do not come with the position feed: airline, aircraft type,
 * registration and route. Every field is optional; the UI shows "Unknown" for missing values.
 */
data class AircraftInfo(
    val airline: String? = null,
    /** ICAO type designator, e.g. "A20N", "B738". */
    val typeCode: String? = null,
    /** Readable type, e.g. "Airbus A320neo". */
    val typeName: String? = null,
    val registration: String? = null,
    val route: Route? = null,
)

/**
 * One aircraft from a position feed, in the common format every data connector produces.
 *
 * @property icao24 Transponder address in lowercase hex; the stable ID of the airframe.
 * @property callsign Flight callsign as broadcast (e.g. "IGO2045"), trimmed; null when not sent.
 * @property altitudeM Altitude in metres (geometric when available, otherwise barometric).
 * @property speedMs Ground speed in metres per second.
 * @property trackDeg Direction of travel, degrees clockwise from true north.
 */
data class Aircraft(
    val icao24: String,
    val callsign: String?,
    val position: GeoPoint,
    val altitudeM: Double?,
    val speedMs: Double?,
    val trackDeg: Double?,
    val verticalRateMs: Double? = null,
    val onGround: Boolean = false,
    val originCountry: String? = null,
    val lastSeenEpochSec: Long? = null,
    val info: AircraftInfo = AircraftInfo(),
) {
  /** Position at the reported altitude, for geometry. */
  val position3d: GeoPoint get() = position.copy(altitudeM = altitudeM ?: position.altitudeM)
}
