import Foundation

/// A small card for glasses with a screen (Meta Ray-Ban Display).
public struct AnswerCard: Equatable, Sendable {
  public var title: String
  public var route: String?
  public var meta: String?
}

/// What an output connector delivers: one short spoken sentence, plus an optional card.
public struct Answer: Equatable, Sendable {
  public var spoken: String
  public var card: AnswerCard?

  public init(spoken: String, card: AnswerCard?) {
    self.spoken = spoken
    self.card = card
  }
}

/// Builds the spoken answer, e.g. "IndiGo A320, Delhi to Chennai, 32,000 feet, 25 kilometres north."
/// Same rules as the Android AnswerBuilder.kt.
public enum AnswerBuilder {
  public static func answer(_ selection: Selection, unit: DistanceUnit = .kilometres) -> Answer {
    switch selection {
    case .hit(let seen, _):
      return Answer(spoken: describe(seen, unit: unit), card: card(seen, unit: unit))
    case .miss(let nearest, let turn, let turnDeg):
      return Answer(spoken: miss(nearest: nearest, turn: turn, turnDeg: turnDeg, unit: unit), card: card(nearest, unit: unit))
    case .noAircraft:
      return Answer(spoken: "No planes near you right now.", card: nil)
    }
  }

  public static func describe(_ seen: SeenAircraft, unit: DistanceUnit) -> String {
    var parts = [who(seen.aircraft)]
    if let r = routeSpoken(seen.aircraft.info.route) { parts.append(r) }
    if let alt = seen.aircraft.altitudeM { parts.append("\(altitudeSpoken(alt)) feet") }
    parts.append("\(distanceSpoken(seen.look.groundDistanceKm, unit: unit)) \(CompassPoint.of(seen.look.bearingDeg).spoken)")
    return parts.joined(separator: ", ") + "."
  }

  public static func miss(nearest seen: SeenAircraft, turn: TurnDirection, turnDeg: Double, unit: DistanceUnit) -> String {
    let where_ = "\(distanceSpoken(seen.look.groundDistanceKm, unit: unit)) \(CompassPoint.of(seen.look.bearingDeg).spoken)"
    let degrees = max(roundTo(turnDeg, 5), 5)
    let turnText: String
    switch turn {
    case .left: turnText = "Turn left about \(degrees) degrees."
    case .right: turnText = "Turn right about \(degrees) degrees."
    case .up: turnText = "Look up about \(degrees) degrees."
    case .down: turnText = "Look lower, about \(degrees) degrees."
    }
    return "No plane that way. Nearest is \(who(seen.aircraft)), \(where_). \(turnText)"
  }

  public static func card(_ seen: SeenAircraft, unit: DistanceUnit) -> AnswerCard {
    let a = seen.aircraft
    let titleParts = [a.info.airline ?? a.callsign, AircraftTypes.spokenName(a.info)].compactMap { $0 }
    let title = titleParts.isEmpty ? "Unknown aircraft" : titleParts.joined(separator: " · ")
    var route: String?
    if let r = a.info.route, r.origin?.code != nil || r.destination?.code != nil {
      route = "\(r.origin?.code ?? "?") → \(r.destination?.code ?? "?")"
    }
    let meta = [
      Units.altitudeText(a.altitudeM),
      "\(Units.distanceText(seen.look.groundDistanceKm, unit)) \(CompassPoint.of(seen.look.bearingDeg).short)",
    ].compactMap { $0 }.joined(separator: " · ")
    return AnswerCard(title: title, route: route, meta: meta)
  }

  public static func who(_ a: Aircraft) -> String {
    let type = AircraftTypes.spokenName(a.info)
    switch (a.info.airline, a.callsign, type) {
    case let (airline?, _, type?): return "\(airline) \(type)"
    case let (airline?, _, nil): return airline
    case let (nil, callsign?, type?): return "Flight \(callsign), \(type)"
    case let (nil, callsign?, nil): return "Flight \(callsign)"
    case let (nil, nil, type?): return "A \(type)"
    default: return "An unknown aircraft"
    }
  }

  public static func routeSpoken(_ route: Route?) -> String? {
    let from = route?.origin?.spokenName, to = route?.destination?.spokenName
    switch (from, to) {
    case let (f?, t?): return "\(f) to \(t)"
    case let (nil, t?): return "to \(t)"
    case let (f?, nil): return "from \(f)"
    default: return nil
    }
  }

  /// Nearest 1,000 ft above 10,000 ft, nearest 100 ft below.
  public static func altitudeSpoken(_ metres: Double) -> String {
    let ft = Units.feet(metres)
    return Units.groupThousands(ft >= 10_000 ? roundTo(ft, 1000) : roundTo(ft, 100))
  }

  public static func distanceSpoken(_ km: Double, unit: DistanceUnit) -> String {
    let d = Units.distance(km, unit)
    let word = unit == .miles ? "mile" : "kilometre"
    if d < 1 { return "less than 1 \(word)" }
    let n = Int(d.rounded())
    return n == 1 ? "1 \(word)" : "\(Units.groupThousands(n)) \(word)s"
  }

  static func roundTo(_ value: Double, _ step: Int) -> Int { Int((value / Double(step)).rounded()) * step }
}
