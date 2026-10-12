import Foundation
import PlanesCore

/// Airline, type, registration and route from adsbdb.com (free, prototype only; see the Android README for terms).
/// `/v0/aircraft/{icao24}?callsign=`, falling back to `/v0/callsign/{callsign}` when the airframe is unknown.
public struct AdsbdbLookup: AircraftInfoLookup {
  public static let api = "https://api.adsbdb.com/v0"
  public let attribution: String? = "Aircraft and route data: adsbdb.com"
  let http: HttpTransport

  public init(http: HttpTransport) { self.http = http }

  /// Thrown when offline, so callers don't cache "unknown" for a network blip.
  public struct Unavailable: Error {}

  public func lookup(icao24: String, callsign: String?) async -> AircraftInfo {
    (try? await lookupOrThrow(icao24: icao24, callsign: callsign)) ?? AircraftInfo()
  }

  public func lookupOrThrow(icao24: String, callsign: String?) async throws -> AircraftInfo {
    let cs = callsign?.addingPercentEncoding(withAllowedCharacters: .alphanumerics)
    let query = cs.map { "?callsign=\($0)" } ?? ""
    do {
      let r = try await http.get("\(Self.api)/aircraft/\(icao24.lowercased())\(query)", headers: [:])
      if r.code == 200 { return Self.parse(r.body) }
      if let cs {
        let byCallsign = try await http.get("\(Self.api)/callsign/\(cs)", headers: [:])
        if byCallsign.code == 200 { return Self.parse(byCallsign.body) }
      }
      return AircraftInfo()
    } catch {
      throw Unavailable()
    }
  }

  public static func parse(_ body: Data) -> AircraftInfo {
    guard let root = try? JSONSerialization.jsonObject(with: body) as? [String: Any],
      let response = root["response"] as? [String: Any]
    else { return AircraftInfo() }
    let aircraft = response["aircraft"] as? [String: Any]
    let route = response["flightroute"] as? [String: Any]
    let airline = route?["airline"] as? [String: Any]
    func s(_ o: [String: Any]?, _ k: String) -> String? {
      guard let v = (o?[k] as? String)?.trimmingCharacters(in: .whitespaces), !v.isEmpty else { return nil }
      return v
    }
    func airport(_ any: Any?) -> Airport? {
      guard let o = any as? [String: Any] else { return nil }
      return Airport(icao: s(o, "icao_code"), iata: s(o, "iata_code"), name: s(o, "name"), city: s(o, "municipality"))
    }
    let typeName = [s(aircraft, "manufacturer"), s(aircraft, "type")].compactMap { $0 }.joined(separator: " ")
    var r: Route?
    if route != nil {
      let candidate = Route(origin: airport(route?["origin"]), destination: airport(route?["destination"]))
      if candidate.origin != nil || candidate.destination != nil { r = candidate }
    }
    return AircraftInfo(
      airline: s(airline, "name"),
      typeCode: s(aircraft, "icao_type"),
      typeName: typeName.isEmpty ? nil : typeName,
      registration: s(aircraft, "registration"),
      route: r)
  }
}
