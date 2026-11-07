import Foundation

/*
 * Pilot mode (MVP 2). A SUPPLEMENTARY situational-awareness aid only — never a primary or certified
 * instrument. VFR, daytime. GPS altitude is always labelled "GPS". Port of the Android
 * core/pilot package; keep both in sync (same functions, same tests).
 */

/// Rounds half up, like Kotlin's roundToInt/roundToLong (Swift's .rounded() rounds half away from
/// zero, which differs for negative values such as −150 ft).
@inline(__always) func roundHalfUp(_ x: Double) -> Int { Int((x + 0.5).rounded(.down)) }

/// Aviation units used throughout pilot mode: knots, nautical miles, feet.
public enum NavUnits {
  public static let metresPerNm = 1852.0
  public static let msPerKt = 1852.0 / 3600.0
  public static let fpmPerMs = 60.0 * Units.feetPerMetre

  public static func nm(_ km: Double) -> Double { km * 1000 / metresPerNm }
  public static func km(_ nm: Double) -> Double { nm * metresPerNm / 1000 }
  public static func kt(_ ms: Double) -> Double { ms / msPerKt }
  public static func ms(_ kt: Double) -> Double { kt * msPerKt }
  public static func feetToMetres(_ ft: Double) -> Double { ft / Units.feetPerMetre }

  /// Track or bearing as pilots write it: three digits, north is "360".
  public static func degreesText(_ deg: Double) -> String {
    let d = ((roundHalfUp(deg) % 360) + 360) % 360
    if d == 0 { return "360" }
    let s = String(d)
    return String(repeating: "0", count: max(0, 3 - s.count)) + s
  }

  /// "045" -> "zero four five", for speech.
  public static func degreesSpoken(_ deg: Double) -> String {
    let digits = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "niner"]
    return degreesText(deg).compactMap { $0.wholeNumberValue.map { digits[$0] } }.joined(separator: " ")
  }
}

/// What a GPS altitude is measured from. Stratux sends height above the WGS-84 ellipsoid.
public enum AltitudeDatum: String, Sendable, Codable { case msl, ellipsoid }

/// Where ownship position and track come from.
public enum PositionSource: String, Sendable { case receiver, phone }

/// Our own aircraft, from the ADS-B receiver's ownship report or the iPhone's GPS.
public struct Ownship: Equatable, Sendable {
  public var position: GeoPoint
  public var groundspeedKt: Double?
  /// True track; nil when unknown or too slow to tell.
  public var trackDeg: Double?
  /// Always shown labelled "GPS"; check `gpsAltitudeDatum`.
  public var gpsAltitudeFt: Double?
  public var gpsAltitudeDatum: AltitudeDatum
  /// Pressure altitude (29.92 inHg), from the receiver's baro sensor if it has one.
  public var pressureAltitudeFt: Double?
  public var verticalRateFpm: Double?
  /// Our transponder address, lowercase hex, so we never call ourselves out as traffic.
  public var icaoAddress: String?
  public var nic: Int?
  public var nacp: Int?
  public var source: PositionSource
  public var timeMs: Int64

  public init(
    position: GeoPoint, groundspeedKt: Double?, trackDeg: Double?, gpsAltitudeFt: Double?, gpsAltitudeDatum: AltitudeDatum = .msl,
    pressureAltitudeFt: Double? = nil, verticalRateFpm: Double? = nil, icaoAddress: String? = nil, nic: Int? = nil, nacp: Int? = nil,
    source: PositionSource, timeMs: Int64
  ) {
    self.position = position
    self.groundspeedKt = groundspeedKt
    self.trackDeg = trackDeg
    self.gpsAltitudeFt = gpsAltitudeFt
    self.gpsAltitudeDatum = gpsAltitudeDatum
    self.pressureAltitudeFt = pressureAltitudeFt
    self.verticalRateFpm = verticalRateFpm
    self.icaoAddress = icaoAddress
    self.nic = nic
    self.nacp = nacp
    self.source = source
    self.timeMs = timeMs
  }

  /// Altitude to compare with traffic. Traffic reports carry PRESSURE altitude, so ownship pressure
  /// altitude is the right reference; GPS altitude is the fallback and can differ by a few hundred feet.
  public var referenceAltitudeFt: Double? { pressureAltitudeFt ?? gpsAltitudeFt }
}

public enum AirfieldType: Int, Sendable, Codable { case large, medium, small, seaplane }

/// One airport from the bundled OurAirports dataset (public domain).
public struct Airfield: Equatable, Sendable, Identifiable {
  /// What we show: ICAO code (KPAO, VOMM), or the FAA identifier for US fields without one (0B5).
  public var ident: String
  public var icao: String?
  public var iata: String?
  public var faa: String?
  public var name: String
  public var city: String?
  public var country: String
  public var position: GeoPoint
  public var elevationFt: Int?
  public var type: AirfieldType

  public var id: String { ident }

  public init(
    ident: String, icao: String?, iata: String?, faa: String?, name: String, city: String?, country: String, position: GeoPoint,
    elevationFt: Int?, type: AirfieldType
  ) {
    self.ident = ident
    self.icao = icao
    self.iata = iata
    self.faa = faa
    self.name = name
    self.city = city
    self.country = country
    self.position = position
    self.elevationFt = elevationFt
    self.type = type
  }
}
