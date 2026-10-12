import Foundation

/// Distance units. Altitude is always in feet.
public enum DistanceUnit: String, CaseIterable, Sendable { case kilometres, miles }

public enum Units {
  public static let feetPerMetre = 3.28084
  public static let kmPerMile = 1.609344
  public static let kmhPerMs = 3.6
  public static let mphPerMs = 2.236936

  public static func feet(_ metres: Double) -> Double { metres * feetPerMetre }

  public static func distance(_ km: Double, _ unit: DistanceUnit) -> Double { unit == .miles ? km / kmPerMile : km }

  public static func speed(_ ms: Double, _ unit: DistanceUnit) -> Double { unit == .miles ? ms * mphPerMs : ms * kmhPerMs }

  public static func distanceSymbol(_ unit: DistanceUnit) -> String { unit == .miles ? "mi" : "km" }

  public static func speedSymbol(_ unit: DistanceUnit) -> String { unit == .miles ? "mph" : "km/h" }

  /// 32000 -> "32,000". Locale-independent so tests and speech are predictable.
  public static func groupThousands(_ value: Int) -> String {
    let digits = String(abs(value))
    var out = ""
    for (i, ch) in digits.reversed().enumerated() {
      if i > 0 && i % 3 == 0 { out.append(",") }
      out.append(ch)
    }
    return (value < 0 ? "-" : "") + String(out.reversed())
  }

  /// Nearest 100 ft.
  public static func altitudeText(_ metres: Double?) -> String? {
    guard let m = metres else { return nil }
    return groupThousands(Int((feet(m) / 100).rounded()) * 100) + " ft"
  }

  public static func distanceText(_ km: Double, _ unit: DistanceUnit) -> String {
    let d = distance(km, unit)
    let s = d < 10 ? String(format: "%.1f", d) : String(Int(d.rounded()))
    return "\(s) \(distanceSymbol(unit))"
  }

  public static func speedText(_ ms: Double?, _ unit: DistanceUnit) -> String? {
    guard let ms else { return nil }
    return "\(groupThousands(Int(speed(ms, unit).rounded()))) \(speedSymbol(unit))"
  }
}

/// 8-point compass.
public enum CompassPoint: Int, CaseIterable, Sendable {
  case n, ne, e, se, s, sw, w, nw

  public var short: String { ["N", "NE", "E", "SE", "S", "SW", "W", "NW"][rawValue] }
  public var spoken: String {
    ["north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"][rawValue]
  }

  public static func of(_ bearingDeg: Double) -> CompassPoint {
    CompassPoint(rawValue: Int((Geo.normalizeDeg(bearingDeg) + 22.5) / 45) % 8)!
  }
}

/// Short spoken names for common ICAO type designators.
public enum AircraftTypes {
  static let spoken: [String: String] = [
    "A319": "A319", "A320": "A320", "A321": "A321",
    "A19N": "A319neo", "A20N": "A320neo", "A21N": "A321neo",
    "A332": "A330", "A333": "A330", "A339": "A330neo",
    "A343": "A340", "A346": "A340",
    "A359": "A350", "A35K": "A350-1000", "A388": "A380",
    "BCS1": "A220", "BCS3": "A220",
    "B737": "737", "B738": "737", "B739": "737",
    "B37M": "737 MAX", "B38M": "737 MAX", "B39M": "737 MAX",
    "B744": "747", "B748": "747-8",
    "B752": "757", "B763": "767", "B764": "767",
    "B772": "777", "B77L": "777", "B773": "777", "B77W": "777",
    "B788": "787", "B789": "787", "B78X": "787",
    "AT72": "ATR 72", "AT76": "ATR 72", "AT75": "ATR 72", "AT45": "ATR 42",
    "DH8D": "Dash 8", "Q400": "Dash 8",
    "E170": "Embraer 170", "E175": "Embraer 175", "E190": "Embraer 190", "E195": "Embraer 195",
    "E75L": "Embraer 175", "E290": "Embraer E2", "E295": "Embraer E2",
    "CRJ7": "CRJ-700", "CRJ9": "CRJ-900",
    "C172": "Cessna 172", "C182": "Cessna 182", "C208": "Cessna Caravan",
    "PC12": "Pilatus PC-12", "SR22": "Cirrus SR22", "P28A": "Piper Cherokee",
    "B06": "helicopter", "R44": "Robinson R44 helicopter", "EC35": "helicopter", "EC45": "helicopter",
  ]

  public static func spokenName(_ info: AircraftInfo) -> String? {
    info.typeCode.flatMap { spoken[$0.uppercased()] } ?? info.typeName ?? info.typeCode
  }
}
