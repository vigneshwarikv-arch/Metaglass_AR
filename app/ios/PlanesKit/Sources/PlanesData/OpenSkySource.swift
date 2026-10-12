import Foundation
import PlanesCore

/// OpenSky API client credentials (OAuth2 client credentials). From Secrets.xcconfig; never committed.
public struct OpenSkyCredentials: Sendable {
  public var clientId: String
  public var clientSecret: String

  public init(clientId: String, clientSecret: String) {
    self.clientId = clientId
    self.clientSecret = clientSecret
  }
}

/// Live positions from the OpenSky Network (`/states/all` with a bounding box). Non-commercial use only.
/// Same behaviour as the Android OpenSkySource.kt.
public actor OpenSkySource: AircraftSource {
  public static let api = "https://opensky-network.org/api"
  public static let tokenURL = "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token"
  public static let attribution = "Flight positions: The OpenSky Network (opensky-network.org)"
  public static let maxRadiusKm = 150.0

  public nonisolated let name = "OpenSky Network (live)"
  public nonisolated let minRefreshInterval: TimeInterval

  private let http: HttpTransport
  private let credentials: OpenSkyCredentials?
  private let now: @Sendable () -> Date
  private var token: String?
  private var tokenExpires = Date.distantPast

  public init(http: HttpTransport, credentials: OpenSkyCredentials? = nil, now: @escaping @Sendable () -> Date = Date.init) {
    self.http = http
    self.credentials = credentials
    self.now = now
    minRefreshInterval = credentials != nil ? 10 : 30
  }

  public func aircraftNear(center: GeoPoint, radiusKm: Double) async -> SourceResult {
    let box = Geo.boundingBox(center: center, radiusKm: min(radiusKm, Self.maxRadiusKm))
    let url = "\(Self.api)/states/all?lamin=\(box.latMin)&lomin=\(box.lonMin)&lamax=\(box.latMax)&lomax=\(box.lonMax)"
    do {
      var response = try await http.get(url, headers: try await authHeaders())
      if response.code == 401, credentials != nil {
        token = nil
        response = try await http.get(url, headers: try await authHeaders())
      }
      switch response.code {
      case 200:
        guard let list = Self.parseStates(response.body) else { return .failure(.other("Unexpected response from OpenSky")) }
        return .success(aircraft: list, fetchedAt: now(), attribution: Self.attribution, staleBecause: nil)
      case 429:
        return .failure(.rateLimited(retryAfterSec: response.header("X-Rate-Limit-Retry-After-Seconds").flatMap { Int($0) }))
      case 401, 403:
        return .failure(.unauthorized("OpenSky rejected the credentials"))
      default:
        return .failure(.http(code: response.code))
      }
    } catch let e as TokenError {
      return .failure(.unauthorized(e.message))
    } catch {
      return .failure(.noInternet)
    }
  }

  private struct TokenError: Error { let message: String }

  private func authHeaders() async throws -> [String: String] {
    guard let creds = credentials else { return [:] }
    if let t = token, now() < tokenExpires { return ["Authorization": "Bearer \(t)"] }
    let r = try await http.postForm(
      Self.tokenURL, form: ["grant_type": "client_credentials", "client_id": creds.clientId, "client_secret": creds.clientSecret])
    guard r.code == 200 else { throw TokenError(message: "OpenSky login failed (HTTP \(r.code))") }
    guard let obj = try? JSONSerialization.jsonObject(with: r.body) as? [String: Any], let t = obj["access_token"] as? String
    else { throw TokenError(message: "No token in OpenSky response") }
    let expiresIn = (obj["expires_in"] as? NSNumber)?.doubleValue ?? 1800
    token = t
    tokenExpires = now().addingTimeInterval(max(expiresIn - 60, 60))
    return ["Authorization": "Bearer \(t)"]
  }

  /// Parses `/states/all`: 0 icao24, 1 callsign, 2 country, 4 last_contact, 5 lon, 6 lat, 7 baro_alt,
  /// 8 on_ground, 9 velocity, 10 track, 11 vertical_rate, 13 geo_alt. Nil for malformed JSON.
  public static func parseStates(_ body: Data) -> [Aircraft]? {
    guard let root = try? JSONSerialization.jsonObject(with: body) as? [String: Any] else { return nil }
    guard let states = root["states"] as? [[Any]] else { return [] }
    func num(_ s: [Any], _ i: Int) -> Double? { i < s.count ? (s[i] as? NSNumber)?.doubleValue : nil }
    func str(_ s: [Any], _ i: Int) -> String? { i < s.count ? s[i] as? String : nil }
    return states.compactMap { s in
      guard let lat = num(s, 6), let lon = num(s, 5), let icao = str(s, 0)?.lowercased() else { return nil }
      let callsign = str(s, 1)?.trimmingCharacters(in: .whitespaces)
      return Aircraft(
        icao24: icao,
        callsign: (callsign?.isEmpty ?? true) ? nil : callsign,
        position: GeoPoint(lat: lat, lon: lon),
        altitudeM: num(s, 13) ?? num(s, 7),
        speedMs: num(s, 9),
        trackDeg: num(s, 10),
        verticalRateMs: num(s, 11),
        onGround: (s.count > 8 ? s[8] as? Bool : nil) ?? false,
        originCountry: str(s, 2),
        lastSeenEpochSec: num(s, 4).map { Int($0) })
    }
  }
}
