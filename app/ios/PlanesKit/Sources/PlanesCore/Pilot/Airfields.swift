import Foundation

public struct NearestAirfield: Equatable, Sendable {
  public var airfield: Airfield
  public var distanceNm: Double
  public var bearingDeg: Double

  public init(airfield: Airfield, distanceNm: Double, bearingDeg: Double) {
    self.airfield = airfield
    self.distanceNm = distanceNm
    self.bearingDeg = bearingDeg
  }
}

/// Nearest-airport and search over the bundled airport list. Plain scans: ~24k airports take a few ms.
public enum Airfields {
  /// The `count` airports nearest to `position`, nearest first.
  public static func nearest(_ position: GeoPoint, _ airfields: [Airfield], count: Int = 1) -> [NearestAirfield] {
    airfields
      .map { ($0, Geo.distanceKm(position, $0.position)) }
      .sorted { $0.1 < $1.1 }
      .prefix(count)
      .map { NearestAirfield(airfield: $0.0, distanceNm: NavUnits.nm($0.1), bearingDeg: Geo.bearingDeg(from: position, to: $0.0.position)) }
  }

  /// Destination search for direct-to: by ICAO (KPAO), FAA (PAO, 0B5), IATA (MAA) or name/city.
  /// Exact codes first, then code prefixes, then names; bigger airports first within each group.
  public static func search(_ query: String, _ airfields: [Airfield], limit: Int = 20) -> [Airfield] {
    let q = query.trimmingCharacters(in: .whitespaces).uppercased()
    guard q.count >= 2 else { return [] }
    return airfields
      .compactMap { a in rank(a, q).map { (a, $0) } }
      .sorted { x, y in
        if x.1 != y.1 { return x.1 < y.1 }
        if x.0.type != y.0.type { return x.0.type.rawValue < y.0.type.rawValue }
        return x.0.name < y.0.name
      }
      .prefix(limit)
      .map(\.0)
  }

  private static func rank(_ a: Airfield, _ q: String) -> Int? {
    let codes = [a.ident, a.icao, a.iata, a.faa].compactMap { $0 }
    if codes.contains(q) { return 0 }
    if codes.contains(where: { $0.hasPrefix(q) }) { return 1 }
    guard q.count >= 3 else { return nil }
    let words = [a.name, a.city].compactMap { $0?.uppercased() }
    let separators = CharacterSet(charactersIn: " -/")
    if words.contains(where: { w in w.hasPrefix(q) || w.components(separatedBy: separators).contains { $0.hasPrefix(q) } }) { return 2 }
    if words.contains(where: { $0.contains(q) }) { return 3 }
    return nil
  }
}
