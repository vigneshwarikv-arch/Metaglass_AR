import Foundation
import PlanesCore

/// The bundled airport list (Resources/airports.json, built by tools/airports/build_airports.py from
/// OurAirports, public domain; identical to the Android copy). ~24k airports, loaded once.
public enum AirportDatabase {
  public static let attribution = "Airports: OurAirports (public domain)"

  public static func load() -> [Airfield] {
    guard let url = Bundle.module.url(forResource: "airports", withExtension: "json"), let data = try? Data(contentsOf: url) else { return [] }
    return (try? parse(data)) ?? []
  }

  /// Rows: [ident, icao, iata, faa, name, city, country, lat, lon, elevationFt, type].
  public static func parse(_ data: Data) throws -> [Airfield] {
    guard let doc = try JSONSerialization.jsonObject(with: data) as? [String: Any], let rows = doc["airports"] as? [[Any]] else { return [] }
    return rows.compactMap { r in
      guard r.count >= 11, let ident = r[0] as? String, let name = r[4] as? String, let country = r[6] as? String,
        let lat = (r[7] as? NSNumber)?.doubleValue, let lon = (r[8] as? NSNumber)?.doubleValue
      else { return nil }
      let type: AirfieldType
      switch r[10] as? String {
      case "L": type = .large
      case "M": type = .medium
      case "W": type = .seaplane
      default: type = .small
      }
      return Airfield(
        ident: ident, icao: r[1] as? String, iata: r[2] as? String, faa: r[3] as? String, name: name, city: r[5] as? String,
        country: country, position: GeoPoint(lat: lat, lon: lon), elevationFt: (r[9] as? NSNumber)?.intValue, type: type)
    }
  }
}
