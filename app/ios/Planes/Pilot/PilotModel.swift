import CoreLocation
import Foundation
import PlanesCore
import PlanesData
import UIKit

/// Pilot mode (MVP 2) settings, saved separately from the MVP 1 settings.
struct PilotSettings: Codable, Equatable {
  var disclaimerAccepted = false
  /// Listen for a Wi-Fi ADS-B receiver (GDL 90 on UDP 4000).
  var receiverEnabled = true
  /// Testing: play a scripted scenario instead of listening to a real receiver.
  var simulatedReceiver = false
  var simulatedScenario = "converging"
  var calloutRangeNm = TrafficRules.defaultRangeNm
  var calloutAltitudeBandFt = Int(TrafficRules.defaultAltitudeBandFt)
  var voiceCallouts = true
  /// Direct-to destination (airport ident), kept across launches.
  var destination: String?
}

enum PhoneGps { case noPermission, waiting, ok }
enum ReceiverMode { case off, wifi, simulated }

/// Pilot mode engine loop for the iPhone; same responsibilities as Android's PilotRuntime. Data
/// connectors: iPhone GPS and a GDL 90 receiver (Wi-Fi or simulated). Output connectors: this screen,
/// iPhone/glasses voice, Ray-Ban Display card. A SUPPLEMENTARY aid only.
///
/// Phase 1 runs while the app is on screen (the screen is kept awake in pilot mode); background
/// running on iOS needs Background Location and is left for later (see docs/08).
@MainActor
final class PilotModel: NSObject, ObservableObject, CLLocationManagerDelegate {
  @Published var settings: PilotSettings { didSet { save(); if oldValue != settings { settingsChanged(from: oldValue) } } }
  @Published private(set) var active = false
  @Published private(set) var ownship: Ownship?
  @Published private(set) var phoneGps: PhoneGps = .waiting
  @Published private(set) var receiverMode: ReceiverMode = .off
  @Published private(set) var receiver = ReceiverSnapshot.empty
  @Published private(set) var destination: Airfield?
  @Published private(set) var directTo: DirectToState?
  @Published private(set) var nearest: NearestAirfield?
  @Published private(set) var traffic: [TrafficTarget] = []
  @Published private(set) var lastCallout: Callout?

  /// Traffic and nearest airport come with a receiver (decided for Phase 1; no payments yet).
  var receiverConnected: Bool { receiver.link == .connected }

  private(set) lazy var airports: [Airfield] = AirportDatabase.load()
  let scenarios = Gdl90Scenario.loadAll()

  private weak var glasses: DatGlasses?
  private weak var simulated: SimulatedGlasses?
  private let voice = Voice()
  private let location = CLLocationManager()
  private var tracker = TrackEstimator()
  private var session = Gdl90Session()
  private let advisor = TrafficAdvisor()
  private var phoneOwnship: Ownship?
  private var tickTask: Task<Void, Never>?
  private var receiverTask: Task<Void, Never>?
  private var lastCard: AnswerCard?
  private var lastCardAt = Date.distantPast

  override init() {
    settings = UserDefaults.standard.data(forKey: "pilotSettings").flatMap { try? JSONDecoder().decode(PilotSettings.self, from: $0) } ?? PilotSettings()
    super.init()
    location.delegate = self
    location.desiredAccuracy = kCLLocationAccuracyBestForNavigation
    location.distanceFilter = kCLDistanceFilterNone
    location.activityType = .airborne
  }

  func attach(glasses: DatGlasses, simulated: SimulatedGlasses) {
    self.glasses = glasses
    self.simulated = simulated
    glasses.onCommand = { [weak self] cmd in
      guard let self, cmd == .pilotModeOff, self.active else { return }
      self.speak("Pilot mode off.")
      self.stop()
    }
  }

  // MARK: Start / stop

  func start() {
    guard !active else { return }
    active = true
    UIApplication.shared.isIdleTimerDisabled = true
    session.reset()
    advisor.reset()
    tracker.reset()
    startPhoneGps()
    restartReceiver()
    tickTask = Task { [weak self] in
      while !Task.isCancelled {
        self?.tick()
        try? await Task.sleep(nanoseconds: 1_000_000_000)
      }
    }
  }

  /// One tap (or "pilot mode off") stops everything: loop, GPS, receiver, callouts.
  func stop() {
    guard active else { return }
    active = false
    UIApplication.shared.isIdleTimerDisabled = false
    tickTask?.cancel()
    receiverTask?.cancel()
    location.stopUpdatingLocation()
    ownship = nil
    phoneOwnship = nil
    traffic = []
    nearest = nil
    directTo = nil
    lastCallout = nil
    receiver = .empty
    lastCard = nil
    if let g = glasses, g.status.isConnected { Task { await g.showCard(AnswerCard(title: "Pilot mode off", route: nil, meta: nil)) } }
  }

  func setDestination(_ a: Airfield?) { settings.destination = a?.ident }

  func search(_ query: String) -> [Airfield] { Airfields.search(query, airports) }

  /// Answer to "what's that plane?" / "Hey Meta, start" while pilot mode is on.
  func speakSummary() {
    speak(PilotText.summarySpoken(own: ownship, direct: directTo, nearest: nearest, top: traffic.first))
  }

  private func save() {
    if let data = try? JSONEncoder().encode(settings) { UserDefaults.standard.set(data, forKey: "pilotSettings") }
  }

  private func settingsChanged(from old: PilotSettings) {
    guard active else { return }
    if old.receiverEnabled != settings.receiverEnabled || old.simulatedReceiver != settings.simulatedReceiver
      || old.simulatedScenario != settings.simulatedScenario
    {
      restartReceiver()
    }
  }

  // MARK: Phone GPS

  private func startPhoneGps() {
    switch location.authorizationStatus {
    case .notDetermined:
      phoneGps = .waiting
      location.requestWhenInUseAuthorization()
    case .denied, .restricted:
      phoneGps = .noPermission
    default:
      phoneGps = .waiting
      location.startUpdatingLocation()
    }
  }

  nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    Task { @MainActor in if self.active { self.startPhoneGps() } }
  }

  nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    guard let l = locations.last else { return }
    Task { @MainActor in self.onFix(l) }
  }

  private func onFix(_ l: CLLocation) {
    guard active, l.horizontalAccuracy >= 0 else { return }
    let fix = GpsFix(
      position: GeoPoint(lat: l.coordinate.latitude, lon: l.coordinate.longitude), timeMs: Int64(l.timestamp.timeIntervalSince1970 * 1000),
      speedMs: l.speed >= 0 ? l.speed : nil, courseDeg: l.course >= 0 && l.speed > 1 ? l.course : nil)
    let est = tracker.update(fix)
    // iOS altitude is above mean sea level.
    phoneOwnship = Ownship(
      position: fix.position, groundspeedKt: est?.groundspeedKt, trackDeg: est?.trackDeg,
      gpsAltitudeFt: l.verticalAccuracy >= 0 ? Units.feet(l.altitude) : nil, gpsAltitudeDatum: .msl, source: .phone,
      timeMs: Int64(Date().timeIntervalSince1970 * 1000))
    phoneGps = .ok
  }

  // MARK: Receiver

  private func restartReceiver() {
    receiverTask?.cancel()
    session.reset()
    advisor.reset()
    receiver = .empty
    let feed: Gdl90Feed?
    if settings.simulatedReceiver {
      let sc = scenarios.first { $0.id == settings.simulatedScenario } ?? scenarios.first
      feed = sc.map { SimulatedReceiver(scenario: $0) }
      receiverMode = .simulated
    } else if settings.receiverEnabled {
      feed = Gdl90UdpReceiver()
      receiverMode = .wifi
    } else {
      feed = nil
      receiverMode = .off
    }
    guard let feed else { return }
    receiverTask = Task { [weak self] in
      for await datagram in feed.datagrams() {
        guard let self, !Task.isCancelled else { break }
        self.session.ingest(datagram)
      }
    }
  }

  // MARK: Once a second

  private func tick() {
    guard active else { return }
    advisor.config = CalloutConfig(maxRangeNm: settings.calloutRangeNm, maxAltitudeDiffFt: Double(settings.calloutAltitudeBandFt))
    let snap = session.snapshot()
    receiver = snap
    let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
    let phone = phoneOwnship.flatMap { nowMs - $0.timeMs <= 5_000 ? $0 : nil }
    let own = (snap.gpsValid ? snap.ownship : nil) ?? phone
    ownship = own
    destination = settings.destination.flatMap { id in airports.first { $0.ident == id } }
    if let own, let dest = destination {
      directTo = DirectToState(
        destination: dest,
        guidance: Navigation.directTo(from: own.position, to: dest.position, groundspeedKt: own.groundspeedKt, trackDeg: own.trackDeg))
    } else {
      directTo = nil
    }
    var callout: Callout?
    if let own, snap.link == .connected {
      nearest = Airfields.nearest(own.position, airports).first
      traffic = TrafficRules.relevant(own, snap.traffic, config: advisor.config)
      callout = advisor.next(own, snap.traffic, nowMs: nowMs)
    } else {
      nearest = nil
      traffic = []
    }
    if let callout {
      lastCallout = callout
      if settings.voiceCallouts { speak(callout.spoken) }
    }
    if let g = glasses, g.status.isConnected, g.hasDisplay {
      let card = PilotText.card(own: own, direct: directTo, nearest: nearest, alert: traffic.first)
      if card != lastCard, Date().timeIntervalSince(lastCardAt) >= 2 {
        lastCard = card
        lastCardAt = Date()
        Task { await g.showCard(card) }
      }
    }
  }

  private func speak(_ text: String) {
    Task {
      if let g = glasses, g.status.isConnected { await g.speak(text) } else { await voice.speak(text) }
    }
  }
}
