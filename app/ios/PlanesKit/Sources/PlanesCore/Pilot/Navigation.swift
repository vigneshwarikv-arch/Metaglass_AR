import Foundation

/// One position fix from the phone. `speedMs` and `courseDeg` are the GPS's own (Doppler) values when it gives them.
public struct GpsFix: Equatable, Sendable {
  public var position: GeoPoint
  public var timeMs: Int64
  public var speedMs: Double?
  public var courseDeg: Double?

  public init(position: GeoPoint, timeMs: Int64, speedMs: Double? = nil, courseDeg: Double? = nil) {
    self.position = position
    self.timeMs = timeMs
    self.speedMs = speedMs
    self.courseDeg = courseDeg
  }
}

public struct TrackEstimate: Equatable, Sendable {
  public var groundspeedKt: Double
  public var trackDeg: Double?
}

/// Groundspeed and true track from successive GPS fixes, smoothed. Same rules as Android's TrackEstimator:
/// GPS speed/course when present, otherwise the last two fixes; smoothing on the (north, east) velocity
/// vector so track wraps through 360°; no track below `minTrackSpeedKt`.
public struct TrackEstimator: Sendable {
  public static let defaultSmoothing = 0.4
  /// Below this the direction of travel is noise (taxiing slowly, standing still).
  public static let minTrackSpeedKt = 5.0
  public static let maxGapMs: Int64 = 10_000

  private let smoothing: Double
  private var last: GpsFix?
  private var north = 0.0
  private var east = 0.0
  private var hasVelocity = false

  public init(smoothing: Double = TrackEstimator.defaultSmoothing) { self.smoothing = smoothing }

  public mutating func reset() {
    last = nil
    hasVelocity = false
  }

  /// Feeds one fix; returns the current estimate, or nil until there is enough data.
  public mutating func update(_ fix: GpsFix) -> TrackEstimate? {
    let prev = last
    let gap = prev.map { fix.timeMs - $0.timeMs }
    if let g = gap, g <= 0 || g > Self.maxGapMs { hasVelocity = false }
    last = fix
    let usablePrev: GpsFix? = (gap.map { $0 >= 1 && $0 <= Self.maxGapMs } ?? false) ? prev : nil
    guard let measured = Self.measuredVelocityKt(fix, usablePrev) else { return current() }
    if !hasVelocity {
      north = measured.north
      east = measured.east
      hasVelocity = true
    } else {
      north += smoothing * (measured.north - north)
      east += smoothing * (measured.east - east)
    }
    return current()
  }

  private func current() -> TrackEstimate? {
    guard hasVelocity else { return nil }
    let gs = hypot(north, east)
    let track = gs >= Self.minTrackSpeedKt ? Geo.normalizeDeg(atan2(east, north) * 180 / .pi) : nil
    return TrackEstimate(groundspeedKt: gs, trackDeg: track)
  }

  private static func measuredVelocityKt(_ fix: GpsFix, _ prev: GpsFix?) -> (north: Double, east: Double)? {
    if let speed = fix.speedMs, let course = fix.courseDeg { return vector(speedKt: NavUnits.kt(speed), trackDeg: course) }
    if let speed = fix.speedMs, NavUnits.kt(speed) < minTrackSpeedKt { return (0, 0) }
    guard let prev else { return nil }
    let dtSec = Double(fix.timeMs - prev.timeMs) / 1000
    let distNm = NavUnits.nm(Geo.distanceKm(prev.position, fix.position))
    let gsKt = fix.speedMs.map(NavUnits.kt) ?? (distNm / dtSec * 3600)
    return vector(speedKt: gsKt, trackDeg: Geo.bearingDeg(from: prev.position, to: fix.position))
  }

  public static func vector(speedKt: Double, trackDeg: Double) -> (north: Double, east: Double) {
    let r = trackDeg * .pi / 180
    return (speedKt * cos(r), speedKt * sin(r))
  }
}

/// Direct-to guidance. `turnDeg`: signed angle from our track to the destination (+ = right), nil
/// without a track. `eteMin`: time en route at the current groundspeed, nil when (nearly) stopped.
public struct DirectTo: Equatable, Sendable {
  public var bearingDeg: Double
  public var distanceNm: Double
  public var eteMin: Double?
  public var turnDeg: Double?
}

public enum Navigation {
  public static let minGsForEteKt = 5.0

  public static func directTo(from: GeoPoint, to: GeoPoint, groundspeedKt: Double?, trackDeg: Double?) -> DirectTo {
    let bearing = Geo.bearingDeg(from: from, to: to)
    let dist = NavUnits.nm(Geo.distanceKm(from, to))
    let ete = groundspeedKt.flatMap { $0 >= minGsForEteKt ? dist / $0 * 60 : nil }
    return DirectTo(bearingDeg: bearing, distanceNm: dist, eteMin: ete, turnDeg: trackDeg.map { Geo.signedBearingDiff(from: $0, to: bearing) })
  }
}
