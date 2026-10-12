import XCTest
import PlanesCore
@testable import PlanesData

/// Canned HTTP: routes matched by longest URL prefix; records every call.
final class FakeHttp: HttpTransport, @unchecked Sendable {
  var routes: [String: HttpResponse] = [:]
  var calls: [String] = []
  var offline = false

  func on(_ prefix: String, _ code: Int, _ body: String, headers: [String: String] = [:]) {
    routes[prefix] = HttpResponse(code: code, body: Data(body.utf8), headers: headers)
  }

  func get(_ url: String, headers: [String: String]) async throws -> HttpResponse { try handle("GET \(url)") }
  func postForm(_ url: String, form: [String: String]) async throws -> HttpResponse { try handle("POST \(url)") }

  private func handle(_ call: String) throws -> HttpResponse {
    calls.append(call)
    if offline { throw HttpError.offline }
    let url = String(call.split(separator: " ", maxSplits: 1)[1])
    let match = routes.keys.filter { url.hasPrefix($0) }.max { $0.count < $1.count }
    return match.map { routes[$0]! } ?? HttpResponse(code: 404, body: Data())
  }
}

final class DataTests: XCTestCase {
  let here = GeoPoint(lat: 13, lon: 80.2)

  let states = """
    {"time":1791600000,"states":[
      ["80139e","AIC1JV  ","India",1791599998,1791599999,80.21,12.95,1828.8,false,95.2,10.5,-4.2,null,1889.76,"1234",false,0],
      ["8016d4","IGO971  ","India",1791599998,1791599999,80.16,12.99,null,true,0.0,90.0,null,null,null,null,false,0],
      ["89653c","        ","United Arab Emirates",null,1791599990,null,null,11582.4,false,250.0,300.0,0.0,null,11620.5,null,false,0]
    ]}
    """

  func testParseStates() {
    let list = OpenSkySource.parseStates(Data(states.utf8))!
    XCTAssertEqual(list.count, 2)
    XCTAssertEqual(list[0].callsign, "AIC1JV")
    XCTAssertEqual(list[0].altitudeM!, 1889.76, accuracy: 1e-9)
    XCTAssertTrue(list[1].onGround)
    XCTAssertNil(list[1].altitudeM)
    XCTAssertEqual(OpenSkySource.parseStates(Data(#"{"time":1,"states":null}"#.utf8))!.count, 0)
  }

  func testRateLimitAndOffline() async {
    let http = FakeHttp()
    http.on(OpenSkySource.api, 429, "", headers: ["X-Rate-Limit-Retry-After-Seconds": "120"])
    let r = await OpenSkySource(http: http).aircraftNear(center: here, radiusKm: 50)
    XCTAssertEqual(r, .failure(.rateLimited(retryAfterSec: 120)))
    http.offline = true
    let r2 = await OpenSkySource(http: http).aircraftNear(center: here, radiusKm: 50)
    XCTAssertEqual(r2, .failure(.noInternet))
  }

  func testTokenFetchedOnce() async {
    let http = FakeHttp()
    http.on(OpenSkySource.tokenURL, 200, #"{"access_token":"abc","expires_in":1800}"#)
    http.on(OpenSkySource.api, 200, states)
    let source = OpenSkySource(http: http, credentials: OpenSkyCredentials(clientId: "id", clientSecret: "s"))
    _ = await source.aircraftNear(center: here, radiusKm: 50)
    _ = await source.aircraftNear(center: here, radiusKm: 50)
    XCTAssertEqual(http.calls.filter { $0.hasPrefix("POST") }.count, 1)
  }

  func testAdsbdbFallbackToCallsign() async {
    let http = FakeHttp()
    http.on("\(AdsbdbLookup.api)/aircraft/", 404, #"{"response":"unknown aircraft"}"#)
    http.on(
      "\(AdsbdbLookup.api)/callsign/IGO2045", 200,
      #"{"response":{"flightroute":{"airline":{"name":"IndiGo Airlines"},"origin":{"iata_code":"DEL","municipality":"New Delhi"},"destination":{"iata_code":"ATQ","municipality":"Amritsar"}}}}"#)
    let info = await AdsbdbLookup(http: http).lookup(icao24: "8016d4", callsign: "IGO2045")
    XCTAssertEqual(info.airline, "IndiGo Airlines")
    XCTAssertEqual(info.route?.destination?.city, "Amritsar")
  }

  func testCacheBacksOffAfterRateLimit() async {
    final class Clock: @unchecked Sendable { var t = Date(timeIntervalSince1970: 0) }
    let clock = Clock()
    let http = FakeHttp()
    http.on(OpenSkySource.api, 200, states)
    let cached = CachingAircraftSource(OpenSkySource(http: http, now: { clock.t }), now: { clock.t })
    _ = await cached.aircraftNear(center: here, radiusKm: 50)
    clock.t = clock.t.addingTimeInterval(40)
    http.on(OpenSkySource.api, 429, "", headers: ["X-Rate-Limit-Retry-After-Seconds": "60"])
    guard case let .success(_, _, _, stale) = await cached.aircraftNear(center: here, radiusKm: 50) else { return XCTFail() }
    XCTAssertEqual(stale, .rateLimited(retryAfterSec: 60))
    clock.t = clock.t.addingTimeInterval(30)
    _ = await cached.aircraftNear(center: here, radiusKm: 50)
    XCTAssertEqual(http.calls.count, 2) // still backing off
  }

  func testSampleChennaiAnswer() async {
    let scenes = SampleSource.loadScenes()
    XCTAssertEqual(scenes.map(\.id), [SampleSource.chennai, SampleSource.milpitas])
    let scene = scenes[0]
    let fixed = Date(timeIntervalSince1970: 0)
    let source = SampleSource(scene: scene, now: { fixed })
    guard case let .success(all, _, _, _) = await source.aircraftNear(center: scene.center, radiusKm: 50) else { return XCTFail() }
    let target = TargetSelector.nearby(user: scene.center, aircraft: all, radiusKm: 50).first { $0.aircraft.callsign == "IGO2045" }!
    let selection = TargetSelector.select(
      user: scene.center, aircraft: all, pointing: Pointing(bearingDeg: target.look.bearingDeg, elevationDeg: target.look.elevationDeg))
    XCTAssertEqual(AnswerBuilder.answer(selection).spoken, "IndiGo A320, Delhi to Chennai, 32,000 feet, 25 kilometres north.")
  }
}
