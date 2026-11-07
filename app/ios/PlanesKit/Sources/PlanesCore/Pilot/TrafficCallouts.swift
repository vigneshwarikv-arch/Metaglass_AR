import Foundation

public enum VerticalTrend: Sendable { case climbing, descending }

/// A traffic aircraft as seen from ownship (port of Android TrafficTarget).
public struct TrafficTarget: Equatable, Sendable, Identifiable {
  public var aircraft: Aircraft
  public var distanceNm: Double
  public var bearingDeg: Double
  /// 1...12 o'clock relative to our track (12 = ahead); nil when we have no track.
  public var clock: Int?
  /// Traffic minus ownship, unrounded; nil when either altitude is unknown.
  public var relativeAltitudeFt: Double?
  public var trend: VerticalTrend?
  /// How fast the distance is shrinking (positive = getting closer); nil if velocities unknown.
  public var closureKt: Double?

  public var id: String { aircraft.icao24 }
  public var converging: Bool { (closureKt ?? 0) > TrafficRules.convergingKt }
  /// Relative altitude rounded to the nearest 100 ft, or nil.
  public var relativeAltitudeRoundedFt: Int? { relativeAltitudeFt.map { roundHalfUp($0 / 100) * 100 } }
}

/// Thresholds for traffic callouts. Users can change range and altitude band in Settings.
public struct CalloutConfig: Equatable, Sendable {
  public var maxRangeNm: Double
  public var maxAltitudeDiffFt: Double
  /// At most one callout this often, whatever the traffic.
  public var minIntervalMs: Int64
  /// The same aircraft is not called again sooner than this…
  public var repeatIntervalMs: Int64
  /// …and only if it is now at most this fraction of the distance it was last called at…
  public var repeatCloserFraction: Double
  /// …or at least this much closer.
  public var repeatCloserNm: Double
  /// Forget aircraft we haven't seen for this long.
  public var forgetAfterMs: Int64

  public init(
    maxRangeNm: Double = TrafficRules.defaultRangeNm, maxAltitudeDiffFt: Double = TrafficRules.defaultAltitudeBandFt,
    minIntervalMs: Int64 = 8_000, repeatIntervalMs: Int64 = 15_000, repeatCloserFraction: Double = 0.7, repeatCloserNm: Double = 1.0,
    forgetAfterMs: Int64 = 60_000
  ) {
    self.maxRangeNm = maxRangeNm
    self.maxAltitudeDiffFt = maxAltitudeDiffFt
    self.minIntervalMs = minIntervalMs
    self.repeatIntervalMs = repeatIntervalMs
    self.repeatCloserFraction = repeatCloserFraction
    self.repeatCloserNm = repeatCloserNm
    self.forgetAfterMs = forgetAfterMs
  }
}

public struct Callout: Equatable, Sendable {
  public var target: TrafficTarget
  public var spoken: String
  public var short: String
}

public enum TrafficRules {
  public static let defaultRangeNm = 5.0
  public static let defaultAltitudeBandFt = 2_000.0
  /// Within this vertical distance traffic is "same altitude".
  public static let sameAltitudeFt = 200.0
  /// Climb/descent rate that is worth saying.
  public static let trendFpm = 500.0
  /// Closure rate above which traffic counts as converging.
  public static let convergingKt = 10.0
  /// Traffic this close (horizontally AND vertically) is our own aircraft seen again (e.g. a TIS-B "shadow").
  public static let ownshipRadiusNm = 0.1
  public static let ownshipAltitudeFt = 300.0

  /// Clock position (1...12) of a relative bearing: 0 = 12 o'clock, +90 = 3 o'clock.
  public static func clock(_ relativeBearingDeg: Double) -> Int {
    let h = roundHalfUp(Geo.normalizeDeg(relativeBearingDeg) / 30) % 12
    return h == 0 ? 12 : h
  }

  /// Where `aircraft` is relative to `own`.
  public static func target(_ own: Ownship, _ aircraft: Aircraft) -> TrafficTarget {
    let bearing = Geo.bearingDeg(from: own.position, to: aircraft.position)
    let distNm = NavUnits.nm(Geo.distanceKm(own.position, aircraft.position))
    var rel: Double?
    if let a = aircraft.altitudeM, let o = own.referenceAltitudeFt { rel = Units.feet(a) - o }
    var trend: VerticalTrend?
    if let fpm = aircraft.verticalRateMs.map({ $0 * NavUnits.fpmPerMs }) {
      if fpm > trendFpm { trend = .climbing } else if fpm < -trendFpm { trend = .descending }
    }
    return TrafficTarget(
      aircraft: aircraft, distanceNm: distNm, bearingDeg: bearing, clock: own.trackDeg.map { clock(bearing - $0) },
      relativeAltitudeFt: rel, trend: trend, closureKt: closureKt(own, aircraft, bearing, distNm))
  }

  /// Rate at which the distance shrinks, from both velocity vectors (flat-Earth; fine within a few NM).
  private static func closureKt(_ own: Ownship, _ a: Aircraft, _ bearingDeg: Double, _ distNm: Double) -> Double? {
    guard distNm >= 1e-3, let speed = a.speedMs, let track = a.trackDeg else { return nil }
    let t = TrackEstimator.vector(speedKt: NavUnits.kt(speed), trackDeg: track)
    var o = (north: 0.0, east: 0.0)
    if let gs = own.groundspeedKt, let tr = own.trackDeg { o = TrackEstimator.vector(speedKt: gs, trackDeg: tr) }
    let b = bearingDeg * .pi / 180
    return -((t.north - o.north) * cos(b) + (t.east - o.east) * sin(b))
  }

  /// True if `a` is really us: same ICAO address, or sitting on top of us.
  public static func isOwnship(_ own: Ownship, _ a: Aircraft, _ target: TrafficTarget) -> Bool {
    if let id = own.icaoAddress, a.icao24.lowercased() == id.lowercased() { return true }
    guard target.distanceNm < ownshipRadiusNm else { return false }
    return target.relativeAltitudeFt.map { abs($0) < ownshipAltitudeFt } ?? true
  }

  /// Traffic worth showing, most important first: airborne, within range and altitude band, not us.
  /// Converging traffic comes first; then nearest first.
  public static func relevant(_ own: Ownship, _ traffic: [Aircraft], config: CalloutConfig = CalloutConfig()) -> [TrafficTarget] {
    traffic
      .filter { !$0.onGround }
      .map { target(own, $0) }
      .filter { !isOwnship(own, $0.aircraft, $0) }
      .filter { $0.distanceNm <= config.maxRangeNm }
      .filter { t in t.relativeAltitudeFt.map { abs($0) <= config.maxAltitudeDiffFt } ?? true }
      .sorted { a, b in a.converging != b.converging ? a.converging : a.distanceNm < b.distanceNm }
  }

  // MARK: Phrases

  /// "Traffic, 2 o'clock, 3 miles, 500 above, climbing."
  public static func spoken(_ t: TrafficTarget) -> String {
    let wherePart = t.clock.map { "\($0) o'clock" } ?? CompassPoint.of(t.bearingDeg).spoken
    var parts = ["Traffic", wherePart, distanceSpoken(t.distanceNm), altitudeSpoken(t)]
    if let tr = t.trend { parts.append(tr == .climbing ? "climbing" : "descending") }
    return parts.joined(separator: ", ") + "."
  }

  /// "2 o'clock · 3.2 NM · +500 ft ↑", for cards and lists.
  public static func short(_ t: TrafficTarget) -> String {
    let wherePart = t.clock.map { "\($0) o'clock" } ?? CompassPoint.of(t.bearingDeg).short
    let alt: String
    if let r = t.relativeAltitudeFt, let rounded = t.relativeAltitudeRoundedFt {
      alt = abs(r) < sameAltitudeFt ? "same alt" : signedFeet(rounded)
    } else {
      alt = "alt ?"
    }
    let arrow = t.trend.map { $0 == .climbing ? " ↑" : " ↓" } ?? ""
    return "\(wherePart) · \(distanceText(t.distanceNm)) · \(alt)\(arrow)"
  }

  public static func distanceSpoken(_ nm: Double) -> String {
    if nm < 1 { return "less than 1 mile" }
    let n = roundHalfUp(nm)
    return n == 1 ? "1 mile" : "\(n) miles"
  }

  /// "3.2 NM" under 10 NM, "23 NM" above.
  public static func distanceText(_ nm: Double) -> String {
    nm < 10 ? String(format: "%.1f NM", nm) : "\(roundHalfUp(nm)) NM"
  }

  private static func altitudeSpoken(_ t: TrafficTarget) -> String {
    guard let r = t.relativeAltitudeFt, let rounded = t.relativeAltitudeRoundedFt else { return "altitude unknown" }
    if abs(r) < sameAltitudeFt { return "same altitude" }
    return "\(Units.groupThousands(abs(rounded))) \(rounded > 0 ? "above" : "below")"
  }

  private static func signedFeet(_ ft: Int) -> String { (ft > 0 ? "+" : "−") + Units.groupThousands(abs(ft)) + " ft" }
}

/// Decides when to speak a traffic callout: one at a time, most important first, and the same aircraft
/// again only when it has come meaningfully closer. Call `next` about once a second.
public final class TrafficAdvisor {
  private struct Announced {
    var distanceNm: Double
    var atMs: Int64
  }

  public var config: CalloutConfig
  private var announced: [String: Announced] = [:]
  private var lastSeen: [String: Int64] = [:]
  private var lastCalloutMs: Int64?

  public init(config: CalloutConfig = CalloutConfig()) { self.config = config }

  public func reset() {
    announced = [:]
    lastSeen = [:]
    lastCalloutMs = nil
  }

  /// The callout to speak now, or nil. Records it as spoken.
  public func next(_ own: Ownship, _ traffic: [Aircraft], nowMs: Int64) -> Callout? {
    for a in traffic { lastSeen[a.icao24] = nowMs }
    for (id, seen) in lastSeen where nowMs - seen > config.forgetAfterMs {
      lastSeen[id] = nil
      announced[id] = nil
    }
    if let last = lastCalloutMs, nowMs - last < config.minIntervalMs { return nil }
    guard let pick = TrafficRules.relevant(own, traffic, config: config).first(where: { worthSaying($0, nowMs) }) else { return nil }
    announced[pick.aircraft.icao24] = Announced(distanceNm: pick.distanceNm, atMs: nowMs)
    lastCalloutMs = nowMs
    return Callout(target: pick, spoken: TrafficRules.spoken(pick), short: TrafficRules.short(pick))
  }

  private func worthSaying(_ t: TrafficTarget, _ nowMs: Int64) -> Bool {
    guard let last = announced[t.aircraft.icao24] else { return true }
    if nowMs - last.atMs < config.repeatIntervalMs { return false }
    return t.distanceNm <= last.distanceNm * config.repeatCloserFraction || last.distanceNm - t.distanceNm >= config.repeatCloserNm
  }
}
