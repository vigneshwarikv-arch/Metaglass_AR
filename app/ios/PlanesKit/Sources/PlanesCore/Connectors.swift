import Foundation

/*
 * The engine's only contact with the outside world, as on Android (Connectors.kt). Data connectors
 * implement AircraftSource / AircraftInfoLookup; output connectors implement AnswerOutput.
 */

public enum FailureReason: Equatable, Sendable {
  case noInternet
  /// `retryAfterSec` is nil when the provider didn't say.
  case rateLimited(retryAfterSec: Int?)
  case http(code: Int)
  case unauthorized(String)
  case other(String)
}

public enum SourceResult: Equatable, Sendable {
  /// `staleBecause` is set when this is an older cached copy because a fresh fetch failed.
  case success(aircraft: [Aircraft], fetchedAt: Date, attribution: String?, staleBecause: FailureReason?)
  case failure(FailureReason)
}

public protocol AircraftSource: Sendable {
  var name: String { get }
  /// Shortest sensible time between two calls, in seconds.
  var minRefreshInterval: TimeInterval { get }
  func aircraftNear(center: GeoPoint, radiusKm: Double) async -> SourceResult
}

public protocol AircraftInfoLookup: Sendable {
  var attribution: String? { get }
  /// Returns what is known; never throws. Missing fields stay nil.
  func lookup(icao24: String, callsign: String?) async -> AircraftInfo
}

public protocol AnswerOutput {
  func deliver(_ answer: Answer) async
}
