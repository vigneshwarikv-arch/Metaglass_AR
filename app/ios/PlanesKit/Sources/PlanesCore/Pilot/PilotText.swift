import Foundation

/// Destination picked for direct-to, with the current guidance to it.
public struct DirectToState: Equatable, Sendable {
  public var destination: Airfield
  public var guidance: DirectTo

  public init(destination: Airfield, guidance: DirectTo) {
    self.destination = destination
    self.guidance = guidance
  }
}

/// Text for pilot-mode readouts, the glasses card and the spoken summary (port of Android PilotText).
/// GPS altitude is ALWAYS labelled "GPS"; nothing here looks like an altimeter or airspeed.
public enum PilotText {
  public static let dash = "—"

  public static func groundspeed(_ kt: Double?) -> String { kt.map { "\(roundHalfUp($0)) kt" } ?? "\(dash) kt" }

  public static func track(_ deg: Double?) -> String { deg.map { "\(NavUnits.degreesText($0))°" } ?? "\(dash)°" }

  /// "4,520 ft GPS" (nearest 10 ft).
  public static func gpsAltitude(_ ft: Double?) -> String {
    ft.map { "\(Units.groupThousands(roundHalfUp($0 / 10) * 10)) ft GPS" } ?? "\(dash) ft GPS"
  }

  /// "12 min", "1 h 05 min".
  public static func ete(_ min: Double?) -> String {
    guard let min else { return "\(dash) min" }
    let m = roundHalfUp(min)
    return m < 60 ? "\(m) min" : "\(m / 60) h \(String(format: "%02d", m % 60)) min"
  }

  /// "12 minutes", "1 hour 5 minutes".
  public static func eteSpoken(_ min: Double) -> String {
    let m = roundHalfUp(min)
    func unit(_ n: Int, _ word: String) -> String { n == 1 ? "1 \(word)" : "\(n) \(word)s" }
    return m < 60 ? unit(m, "minute") : "\(unit(m / 60, "hour")) \(unit(m % 60, "minute"))"
  }

  /// "→ KPAO 23 NM · 12 min".
  public static func directToLine(_ d: DirectToState) -> String {
    "→ \(d.destination.ident) \(TrafficRules.distanceText(d.guidance.distanceNm)) · \(ete(d.guidance.eteMin))"
  }

  /// "Nearest KSQL 4.1 NM NE".
  public static func nearestLine(_ n: NearestAirfield) -> String {
    "Nearest \(n.airfield.ident) \(TrafficRules.distanceText(n.distanceNm)) \(CompassPoint.of(n.bearingDeg).short)"
  }

  /// The three-line Ray-Ban Display card: own numbers, then direct-to (or nearest airport), then one
  /// traffic alert (or nearest airport). Minimal on purpose.
  public static func card(own: Ownship?, direct: DirectToState?, nearest: NearestAirfield?, alert: TrafficTarget?) -> AnswerCard {
    let title: String
    if let own {
      let alt = gpsAltitude(own.gpsAltitudeFt).replacingOccurrences(of: " ft GPS", with: "")
      title = "\(groundspeed(own.groundspeedKt)) · TRK \(track(own.trackDeg)) · \(alt) GPS"
    } else {
      title = "No GPS position"
    }
    let second = direct.map(directToLine) ?? nearest.map(nearestLine)
    let third = alert.map { "TRAFFIC \(TrafficRules.short($0))" } ?? (direct != nil ? nearest.map(nearestLine) : nil)
    return AnswerCard(title: title, route: second, meta: third)
  }

  /// Spoken when the pilot asks the glasses: own numbers, destination, nearest airport, top traffic.
  public static func summarySpoken(own: Ownship?, direct: DirectToState?, nearest: NearestAirfield?, top: TrafficTarget?) -> String {
    guard let own else { return "No GPS position yet." }
    var parts: [String] = []
    if let gs = own.groundspeedKt { parts.append("Groundspeed \(roundHalfUp(gs)) knots") }
    if let t = own.trackDeg { parts.append("track \(NavUnits.degreesSpoken(t))") }
    if let a = own.gpsAltitudeFt { parts.append("GPS altitude \(Units.groupThousands(roundHalfUp(a / 100) * 100)) feet") }
    var sentences: [String] = []
    if !parts.isEmpty { sentences.append(parts.joined(separator: ", ") + ".") }
    if let d = direct {
      let e = d.guidance.eteMin.map { ", \(eteSpoken($0))" } ?? ""
      sentences.append("\(spokenName(d.destination)), \(TrafficRules.distanceSpoken(d.guidance.distanceNm))\(e).")
    }
    if let n = nearest {
      sentences.append(
        "Nearest airport \(spokenName(n.airfield)), \(TrafficRules.distanceSpoken(n.distanceNm)) \(CompassPoint.of(n.bearingDeg).spoken).")
    }
    sentences.append(top.map(TrafficRules.spoken) ?? "No traffic nearby.")
    return sentences.joined(separator: " ")
  }

  /// Airport name for speech: "Palo Alto" from "Palo Alto Airport".
  public static func spokenName(_ a: Airfield) -> String {
    var n = a.name
    for suffix in [" International Airport", " Airport", " Airfield"] where n.hasSuffix(suffix) {
      n = String(n.dropLast(suffix.count))
      break
    }
    return n.trimmingCharacters(in: .whitespaces).isEmpty ? a.ident : n
  }
}
