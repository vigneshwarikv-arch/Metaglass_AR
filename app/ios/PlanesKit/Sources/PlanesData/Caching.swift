import Foundation
import PlanesCore

/// Wraps an AircraftSource: serves repeat calls from cache inside the refresh interval, backs off
/// after rate limits, and returns the last good answer marked stale when a fetch fails.
public actor CachingAircraftSource: AircraftSource {
  public nonisolated let name: String
  public nonisolated let minRefreshInterval: TimeInterval
  private let delegate: AircraftSource
  private let now: @Sendable () -> Date
  private let moveThresholdKm: Double
  private var last: (center: GeoPoint, radiusKm: Double, aircraft: [Aircraft], fetchedAt: Date, attribution: String?)?
  private var blockedUntil = Date.distantPast
  private var blockedReason: FailureReason?

  public init(_ delegate: AircraftSource, now: @escaping @Sendable () -> Date = Date.init, moveThresholdKm: Double = 2) {
    self.delegate = delegate
    self.name = delegate.name
    self.minRefreshInterval = delegate.minRefreshInterval
    self.now = now
    self.moveThresholdKm = moveThresholdKm
  }

  public func aircraftNear(center: GeoPoint, radiusKm: Double) async -> SourceResult {
    let t = now()
    if let c = last, c.radiusKm >= radiusKm, Geo.distanceKm(c.center, center) < moveThresholdKm,
      t.timeIntervalSince(c.fetchedAt) < minRefreshInterval
    {
      return .success(aircraft: c.aircraft, fetchedAt: c.fetchedAt, attribution: c.attribution, staleBecause: nil)
    }
    if t < blockedUntil, let reason = blockedReason {
      if let c = last { return .success(aircraft: c.aircraft, fetchedAt: c.fetchedAt, attribution: c.attribution, staleBecause: reason) }
      return .failure(reason)
    }
    let result = await delegate.aircraftNear(center: center, radiusKm: radiusKm)
    switch result {
    case let .success(aircraft, fetchedAt, attribution, _):
      last = (center, radiusKm, aircraft, fetchedAt, attribution)
      blockedReason = nil
      return result
    case let .failure(reason):
      if case let .rateLimited(retryAfter) = reason {
        blockedUntil = t.addingTimeInterval(TimeInterval(retryAfter ?? 60))
        blockedReason = reason
      }
      if let c = last, Geo.distanceKm(c.center, center) < 25 {
        return .success(aircraft: c.aircraft, fetchedAt: c.fetchedAt, attribution: c.attribution, staleBecause: reason)
      }
      return result
    }
  }
}

/// Remembers lookups (icao24 + callsign) so each flight is looked up once per `ttl`; doesn't cache offline failures.
public actor CachingInfoLookup: AircraftInfoLookup {
  public nonisolated let attribution: String?
  private let delegate: AdsbdbLookup
  private let now: @Sendable () -> Date
  private let ttl: TimeInterval
  private var cache: [String: (Date, AircraftInfo)] = [:]

  public init(_ delegate: AdsbdbLookup, now: @escaping @Sendable () -> Date = Date.init, ttl: TimeInterval = 6 * 3600) {
    self.delegate = delegate
    self.attribution = delegate.attribution
    self.now = now
    self.ttl = ttl
  }

  public func lookup(icao24: String, callsign: String?) async -> AircraftInfo {
    let key = "\(icao24)|\(callsign ?? "")"
    if let entry = cache[key], now().timeIntervalSince(entry.0) < ttl { return entry.1 }
    guard let info = try? await delegate.lookupOrThrow(icao24: icao24, callsign: callsign) else { return AircraftInfo() }
    if cache.count > 500 { cache.removeAll() }
    cache[key] = (now(), info)
    return info
  }
}

/// Adds lookup info to the `limit` nearest aircraft, a few requests at a time.
public func enrich(_ aircraft: [Aircraft], lookup: AircraftInfoLookup, limit: Int = 15) async -> [Aircraft] {
  var out = aircraft
  await withTaskGroup(of: (Int, AircraftInfo).self) { group in
    for (i, a) in aircraft.prefix(limit).enumerated() {
      group.addTask { (i, await lookup.lookup(icao24: a.icao24, callsign: a.callsign)) }
    }
    for await (i, extra) in group {
      let base = out[i].info
      out[i].info = AircraftInfo(
        airline: base.airline ?? extra.airline,
        typeCode: base.typeCode ?? extra.typeCode,
        typeName: base.typeName ?? extra.typeName,
        registration: base.registration ?? extra.registration,
        route: base.route ?? extra.route)
    }
  }
  return out
}
