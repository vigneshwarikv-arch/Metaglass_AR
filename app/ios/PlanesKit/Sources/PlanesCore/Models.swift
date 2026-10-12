import Foundation

/// The product name is not decided yet. Change it here (and in the Android Brand.kt).
public enum Brand {
  public static let name = "[BrandName]"
}

/// A point on Earth. `altitudeM` is metres above mean sea level.
public struct GeoPoint: Equatable, Sendable {
  public var lat: Double
  public var lon: Double
  public var altitudeM: Double

  public init(lat: Double, lon: Double, altitudeM: Double = 0) {
    self.lat = lat
    self.lon = lon
    self.altitudeM = altitudeM
  }
}

public struct Airport: Equatable, Sendable {
  public var icao: String?
  public var iata: String?
  public var name: String?
  /// City or municipality, used in spoken answers ("Chennai to Delhi").
  public var city: String?

  public init(icao: String? = nil, iata: String? = nil, name: String? = nil, city: String? = nil) {
    self.icao = icao
    self.iata = iata
    self.name = name
    self.city = city
  }

  /// Short code for cards: IATA first (MAA), then ICAO (VOMM).
  public var code: String? { iata ?? icao }
  /// Name for speech: city first, then airport name, then code.
  public var spokenName: String? { city ?? name ?? code }
}

public struct Route: Equatable, Sendable {
  public var origin: Airport?
  public var destination: Airport?

  public init(origin: Airport? = nil, destination: Airport? = nil) {
    self.origin = origin
    self.destination = destination
  }
}

/// Airline, type, registration and route: facts that don't come with the position feed.
public struct AircraftInfo: Equatable, Sendable {
  public var airline: String?
  /// ICAO type designator, e.g. "A20N".
  public var typeCode: String?
  /// Readable type, e.g. "Airbus A320neo".
  public var typeName: String?
  public var registration: String?
  public var route: Route?

  public init(airline: String? = nil, typeCode: String? = nil, typeName: String? = nil, registration: String? = nil, route: Route? = nil) {
    self.airline = airline
    self.typeCode = typeCode
    self.typeName = typeName
    self.registration = registration
    self.route = route
  }
}

/// One aircraft from a position feed, in the common format every data connector produces.
public struct Aircraft: Equatable, Sendable, Identifiable {
  /// Transponder address in lowercase hex.
  public var icao24: String
  public var callsign: String?
  public var position: GeoPoint
  /// Metres (geometric when available, otherwise barometric).
  public var altitudeM: Double?
  /// Ground speed in metres per second.
  public var speedMs: Double?
  /// Degrees clockwise from true north.
  public var trackDeg: Double?
  public var verticalRateMs: Double?
  public var onGround: Bool
  public var originCountry: String?
  public var lastSeenEpochSec: Int?
  public var info: AircraftInfo

  public var id: String { icao24 }

  public init(
    icao24: String, callsign: String?, position: GeoPoint, altitudeM: Double?, speedMs: Double?, trackDeg: Double?,
    verticalRateMs: Double? = nil, onGround: Bool = false, originCountry: String? = nil, lastSeenEpochSec: Int? = nil,
    info: AircraftInfo = AircraftInfo()
  ) {
    self.icao24 = icao24
    self.callsign = callsign
    self.position = position
    self.altitudeM = altitudeM
    self.speedMs = speedMs
    self.trackDeg = trackDeg
    self.verticalRateMs = verticalRateMs
    self.onGround = onGround
    self.originCountry = originCountry
    self.lastSeenEpochSec = lastSeenEpochSec
    self.info = info
  }

  /// Position at the reported altitude, for geometry.
  public var position3d: GeoPoint {
    var p = position
    p.altitudeM = altitudeM ?? position.altitudeM
    return p
  }
}
