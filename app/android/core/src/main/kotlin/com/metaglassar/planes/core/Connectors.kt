package com.metaglassar.planes.core

/*
 * The engine's only contact with the outside world. Data connectors (OpenSky, sample data, later a
 * GDL 90 receiver) implement AircraftSource / AircraftInfoLookup; output connectors (phone, Meta
 * glasses, later a watch) implement AnswerOutput. Core never imports a connector.
 */

/** Result of asking a data connector for aircraft. */
sealed interface SourceResult {
  data class Success(
      val aircraft: List<Aircraft>,
      /** When the data was fetched (epoch ms). */
      val fetchedAtMs: Long,
      /** Attribution the UI must show for this data, e.g. "The OpenSky Network". */
      val attribution: String?,
      /** Set when this is an older cached copy because a fresh fetch failed (e.g. rate-limited). */
      val staleBecause: FailureReason? = null,
  ) : SourceResult

  data class Failure(val reason: FailureReason) : SourceResult
}

sealed interface FailureReason {
  data object NoInternet : FailureReason
  /** The provider's rate limit was hit. [retryAfterSec] is null when the provider didn't say. */
  data class RateLimited(val retryAfterSec: Long?) : FailureReason
  data class Http(val code: Int) : FailureReason
  data class Unauthorized(val message: String) : FailureReason
  data class Other(val message: String) : FailureReason
}

/** A data connector that knows where aircraft are. */
interface AircraftSource {
  /** Short name for settings and logs ("OpenSky (live)", "Sample data"). */
  val name: String

  /** Shortest sensible time between two calls, so the UI doesn't burn the provider's quota. */
  val minRefreshIntervalMs: Long

  suspend fun aircraftNear(center: GeoPoint, radiusKm: Double): SourceResult
}

/** A data connector that adds airline, type, registration and route to an aircraft. */
interface AircraftInfoLookup {
  /** Attribution the UI must show when this lookup's data is displayed. */
  val attribution: String?

  /** Returns what is known; never throws. Missing fields stay null. */
  suspend fun lookup(icao24: String, callsign: String?): AircraftInfo
}

/** An output connector: somewhere an answer can be shown or spoken (phone, glasses, watch). */
interface AnswerOutput {
  suspend fun deliver(answer: Answer)
}
