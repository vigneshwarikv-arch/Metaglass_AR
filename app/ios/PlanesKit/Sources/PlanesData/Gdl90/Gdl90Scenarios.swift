import Foundation
import PlanesCore

/// Scripted GDL 90 traffic for testing without flying (Resources/scenarios.json, a copy of the Android
/// file; CI checks they match). `frames` produces what a Stratux would send in one second: heartbeat,
/// ForeFlight ID, ownship, ownship geometric altitude, traffic reports — all framed with CRC.
public struct Gdl90Scenario: Sendable, Identifiable {
  public static let ellipsoidOffsetFt = 100.0

  public struct State: Sendable {
    public var icao: Int
    public var callsign: String
    public var position: GeoPoint
    public var altFt: Double
    public var trackDeg: Double
    public var gsKt: Double
    public var vsFpm: Double
    public var airborne: Bool
    public var addressType = 0
  }

  struct Own: Codable, Sendable {
    var icao: String, callsign: String, lat: Double, lon: Double, altFt: Double, trackDeg: Double, gsKt: Double
  }

  /// One entry of "traffic"; which fields are used depends on `kind` (see the note in scenarios.json).
  struct Traffic: Codable, Sendable {
    var kind: String
    var icao: String
    var callsign: String?
    var relBearingDeg: Double?, distNm: Double?, relAltFt: Double?, meetSec: Double?, missNm: Double?, vsFpm: Double?
    var centerLat: Double?, centerLon: Double?, radiusNm: Double?, gsKt: Double?, altFt: Double?, clockwise: Bool?, startDeg: Double?
    var lat: Double?, lon: Double?, trackDeg: Double?
  }

  struct Raw: Codable, Sendable {
    var id: String, name: String, durationSec: Int, ownship: Own, traffic: [Traffic]
  }

  struct File: Codable { var scenarios: [Raw] }

  public let id: String
  public let name: String
  public let durationSec: Int
  let own: Own
  let traffic: [Traffic]

  init(_ r: Raw) {
    id = r.id
    name = r.name
    durationSec = r.durationSec
    own = r.ownship
    traffic = r.traffic
  }

  public static func loadAll() -> [Gdl90Scenario] {
    guard let url = Bundle.module.url(forResource: "scenarios", withExtension: "json"), let data = try? Data(contentsOf: url) else { return [] }
    return (try? parse(data)) ?? []
  }

  public static func parse(_ data: Data) throws -> [Gdl90Scenario] {
    try JSONDecoder().decode(File.self, from: data).scenarios.map(Gdl90Scenario.init)
  }

  private var ownStart: GeoPoint { GeoPoint(lat: own.lat, lon: own.lon) }

  public func ownshipAt(_ t: Double) -> State {
    State(
      icao: hex(own.icao), callsign: own.callsign, position: Self.along(ownStart, own.trackDeg, own.gsKt, t), altFt: own.altFt,
      trackDeg: own.trackDeg, gsKt: own.gsKt, vsFpm: 0, airborne: true)
  }

  public func trafficAt(_ t: Double) -> [State] { traffic.map { stateOf($0, t) } }

  private func stateOf(_ x: Traffic, _ t: Double) -> State {
    let cs = x.callsign ?? ""
    switch x.kind {
    case "meet":
      let start = Geo.destination(from: ownStart, bearingDeg: own.trackDeg + (x.relBearingDeg ?? 0), distanceKm: NavUnits.km(x.distNm ?? 0))
      let meet = x.meetSec ?? 60
      let target = Geo.destination(from: ownshipAt(meet).position, bearingDeg: own.trackDeg, distanceKm: NavUnits.km(x.missNm ?? 0))
      let track = Geo.bearingDeg(from: start, to: target)
      let gs = NavUnits.nm(Geo.distanceKm(start, target)) / meet * 3600
      let vs = x.vsFpm ?? 0
      return State(
        icao: hex(x.icao), callsign: cs, position: Self.along(start, track, gs, t), altFt: own.altFt + (x.relAltFt ?? 0) + vs * t / 60,
        trackDeg: track, gsKt: gs, vsFpm: vs, airborne: true)
    case "orbit":
      let dir = (x.clockwise ?? false) ? 1.0 : -1.0
      let r = x.radiusNm ?? 1, gs = x.gsKt ?? 80
      let angle = (x.startDeg ?? 0) + dir * (gs / 3600) / (2 * .pi * r) * 360 * t
      let center = GeoPoint(lat: x.centerLat ?? 0, lon: x.centerLon ?? 0)
      return State(
        icao: hex(x.icao), callsign: cs, position: Geo.destination(from: center, bearingDeg: angle, distanceKm: NavUnits.km(r)),
        altFt: x.altFt ?? 0, trackDeg: Geo.normalizeDeg(angle + dir * 90), gsKt: gs, vsFpm: 0, airborne: true)
    case "ground":
      let start = GeoPoint(lat: x.lat ?? 0, lon: x.lon ?? 0)
      return State(
        icao: hex(x.icao), callsign: "", position: Self.along(start, x.trackDeg ?? 0, x.gsKt ?? 0, t), altFt: 0, trackDeg: x.trackDeg ?? 0,
        gsKt: x.gsKt ?? 0, vsFpm: 0, airborne: false)
    default:  // "shadow": a TIS-B copy of ownship
      var s = ownshipAt(t)
      s.icao = hex(x.icao)
      s.callsign = ""
      s.addressType = 2
      return s
    }
  }

  /// The datagram a receiver would send at `t` seconds into the scenario (it loops after `durationSec`).
  /// `utcSec` is the heartbeat time stamp (seconds since midnight UTC).
  public func frames(_ t: Double, utcSec: Int, deviceName: String = "SimRcvr") -> [UInt8] {
    let tt = t.truncatingRemainder(dividingBy: Double(durationSec))
    let o = ownshipAt(tt)
    var msgs = [
      Gdl90Encoder.heartbeat(gpsValid: true, timestampSec: utcSec),
      Gdl90Encoder.foreFlightId(name: deviceName, longName: "\(Brand.name) sim", geoAltitudeIsMsl: false),
      report(o, ownship: true),
      // Like Stratux: geometric altitude is height above the ellipsoid, ~100 ft below MSL in California.
      Gdl90Encoder.geoAltitude(o.altFt - Self.ellipsoidOffsetFt),
    ]
    msgs += trafficAt(tt).map { report($0, ownship: false) }
    return msgs.flatMap(Gdl90.frame)
  }

  private func report(_ s: State, ownship: Bool) -> [UInt8] {
    Gdl90Encoder.report(
      ownship: ownship, address: s.icao, latDeg: s.position.lat, lonDeg: s.position.lon, pressureAltitudeFt: s.altFt, airborne: s.airborne,
      trackDeg: s.trackDeg, horizontalVelocityKt: s.gsKt, verticalVelocityFpm: s.vsFpm, callsign: s.callsign, addressType: s.addressType,
      emitterCategory: s.airborne ? 1 : 18)
  }

  private func hex(_ s: String) -> Int { Int(s, radix: 16) ?? 0 }

  private static func along(_ start: GeoPoint, _ trackDeg: Double, _ gsKt: Double, _ t: Double) -> GeoPoint {
    Geo.destination(from: start, bearingDeg: trackDeg, distanceKm: NavUnits.km(gsKt * t / 3600))
  }
}
