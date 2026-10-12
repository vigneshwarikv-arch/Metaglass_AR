import Foundation
import PlanesCore

/// A place with recorded (synthetic) traffic: Chennai and Milpitas.
public struct SampleScene: Sendable, Identifiable {
  public var id: String
  public var name: String
  public var center: GeoPoint
  public var aircraft: [Aircraft]
}

/// Offline data from flights.json (shared with Android). Planes drift at 10% speed and loop every
/// 5 minutes so they stay findable. In sample mode the app uses the scene centre as the user's location.
public struct SampleSource: AircraftSource, AircraftInfoLookup {
  public static let chennai = "chennai"
  public static let milpitas = "milpitas"

  public let scene: SampleScene
  public var name: String { "Sample data — \(scene.name)" }
  public let minRefreshInterval: TimeInterval = 2
  public let attribution: String? = "Sample data (not real flights)"
  let start: Date
  let now: @Sendable () -> Date
  let loopSeconds: Double
  let speedFactor: Double

  public init(scene: SampleScene, now: @escaping @Sendable () -> Date = Date.init, loopSeconds: Double = 300, speedFactor: Double = 0.1) {
    self.scene = scene
    self.now = now
    self.start = now()
    self.loopSeconds = loopSeconds
    self.speedFactor = speedFactor
  }

  public func aircraftNear(center: GeoPoint, radiusKm: Double) async -> SourceResult {
    .success(aircraft: snapshot(), fetchedAt: now(), attribution: attribution, staleBecause: nil)
  }

  public func snapshot() -> [Aircraft] {
    let t = now().timeIntervalSince(start).truncatingRemainder(dividingBy: loopSeconds) * speedFactor
    return scene.aircraft.map { a in
      guard !a.onGround, let speed = a.speedMs, speed > 0, let track = a.trackDeg else { return a }
      var moved = a
      moved.position = Geo.destination(from: a.position, bearingDeg: track, distanceKm: speed * t / 1000)
      moved.altitudeM = a.altitudeM.map { max($0 + (a.verticalRateMs ?? 0) * t, 150) }
      return moved
    }
  }

  public func lookup(icao24: String, callsign: String?) async -> AircraftInfo {
    scene.aircraft.first { $0.icao24 == icao24 }?.info ?? AircraftInfo()
  }

  public static func loadScenes() -> [SampleScene] {
    guard let url = Bundle.module.url(forResource: "flights", withExtension: "json"),
      let data = try? Data(contentsOf: url)
    else { return [] }
    return parseScenes(data)
  }

  public static func parseScenes(_ data: Data) -> [SampleScene] {
    guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
      let airportsJson = root["airports"] as? [String: [String: Any]],
      let scenesJson = root["scenes"] as? [[String: Any]]
    else { return [] }
    func s(_ o: [String: Any], _ k: String) -> String? { o[k] as? String }
    func d(_ o: [String: Any], _ k: String) -> Double? { (o[k] as? NSNumber)?.doubleValue }
    let airports = airportsJson.mapValues { Airport(icao: s($0, "icao"), iata: s($0, "iata"), name: s($0, "name"), city: s($0, "city")) }
    let ktToMs = 0.514444, fpmToMs = 0.00508
    return scenesJson.compactMap { scene in
      guard let id = s(scene, "id"), let name = s(scene, "name"), let c = scene["center"] as? [String: Any],
        let lat = d(c, "lat"), let lon = d(c, "lon"), let flights = scene["flights"] as? [[String: Any]]
      else { return nil }
      let center = GeoPoint(lat: lat, lon: lon, altitudeM: d(c, "altitudeM") ?? 0)
      let aircraft: [Aircraft] = flights.compactMap { f in
        guard let icao = s(f, "icao24"), let brg = d(f, "bearingDeg"), let km = d(f, "distanceKm") else { return nil }
        var pos = Geo.destination(from: center, bearingDeg: brg, distanceKm: km)
        pos.altitudeM = 0
        let from = s(f, "from").flatMap { airports[$0] }
        let to = s(f, "to").flatMap { airports[$0] }
        return Aircraft(
          icao24: icao,
          callsign: s(f, "callsign"),
          position: pos,
          altitudeM: d(f, "altitudeFt").map { $0 / Units.feetPerMetre },
          speedMs: d(f, "speedKt").map { $0 * ktToMs },
          trackDeg: d(f, "trackDeg"),
          verticalRateMs: d(f, "verticalRateFpm").map { $0 * fpmToMs },
          onGround: (f["onGround"] as? Bool) ?? false,
          info: AircraftInfo(
            airline: s(f, "airline"), typeCode: s(f, "typeCode"), typeName: s(f, "typeName"),
            registration: s(f, "registration"),
            route: (from != nil || to != nil) ? Route(origin: from, destination: to) : nil))
      }
      return SampleScene(id: id, name: name, center: center, aircraft: aircraft)
    }
  }
}
