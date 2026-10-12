import Combine
import CoreLocation
import Foundation
import PlanesCore
import PlanesData

enum DataMode: String, Codable { case live, sample }

struct AppSettings: Codable, Equatable {
  var unit: DistanceUnit = .kilometres
  var radiusKm: Int = 50
  var dataMode: DataMode = .live
  var sampleScene: String = SampleSource.chennai
  var simulatedGlasses = false
  var simulatedModel: SimulatedModel = .rayBanDisplay
}

enum LocationState: Equatable {
  case needsPermission, denied, searching
  case known(GeoPoint, isSample: Bool)

  var point: GeoPoint? { if case let .known(p, _) = self { return p } else { return nil } }
}

enum FeedState: Equatable {
  case loading
  case ok
  case stale(FailureReason)
  case error(FailureReason)
}

/// Compass state, kept apart so only the Point screen redraws on every compass change.
@MainActor
final class PointState: ObservableObject {
  @Published var pointing: Pointing?
  @Published var compassUnreliable = false
  @Published var selection: Selection?
}

/// Same responsibilities as the Android MainViewModel.
@MainActor
final class AppModel: ObservableObject {
  @Published var settings: AppSettings { didSet { saveSettings(); if oldValue != settings { settingsChanged(from: oldValue) } } }
  @Published private(set) var location: LocationState = .searching
  @Published private(set) var nearby: [SeenAircraft] = []
  @Published private(set) var feed: FeedState = .loading
  @Published private(set) var attributions: [String] = []
  @Published var lastAnswer: Answer?
  @Published var simHead = Pointing(bearingDeg: 0, elevationDeg: 15)
  let point = PointState()

  let glasses = DatGlasses()
  let simulated = SimulatedGlasses()
  let locationService = LocationService()
  private let phoneAim = PhoneAim()
  private let phoneVoice = Voice()
  let sampleScenes = SampleSource.loadScenes()
  let hasOpenSkyCredentials: Bool
  private let liveSource: CachingAircraftSource
  private let liveLookup: CachingInfoLookup
  private var sampleSources: [String: SampleSource] = [:]
  private var allAircraft: [Aircraft] = []
  private var feedTask: Task<Void, Never>?
  private var feedCenter: GeoPoint?
  private var visible = false
  private var cancellables = Set<AnyCancellable>()

  var compassAvailable: Bool { phoneAim.available }

  init() {
    let stored = UserDefaults.standard.data(forKey: "settings").flatMap { try? JSONDecoder().decode(AppSettings.self, from: $0) }
    settings = stored ?? AppSettings()
    let http = URLSessionTransport(userAgent: "MetaglassAR-prototype/0.1 (non-commercial testing)")
    let id = Bundle.main.object(forInfoDictionaryKey: "OpenSkyClientId") as? String ?? ""
    let secret = Bundle.main.object(forInfoDictionaryKey: "OpenSkyClientSecret") as? String ?? ""
    let creds = (!id.isEmpty && !secret.isEmpty) ? OpenSkyCredentials(clientId: id, clientSecret: secret) : nil
    hasOpenSkyCredentials = creds != nil
    liveSource = CachingAircraftSource(OpenSkySource(http: http, credentials: creds))
    liveLookup = CachingInfoLookup(AdsbdbLookup(http: http))
    for scene in sampleScenes { sampleSources[scene.id] = SampleSource(scene: scene) }

    glasses.declinationDeg = { [weak self] in self?.locationService.declinationDeg ?? 0 }
    glasses.onAsk = { [weak self] trigger in self?.ask(trigger) }
    glasses.start()
    if settings.simulatedGlasses { simulated.enable(settings.simulatedModel) }

    locationService.$location.compactMap { $0 }.sink { [weak self] p in self?.onLocation(p) }.store(in: &cancellables)
    locationService.$authorization.sink { [weak self] _ in self?.refreshLocationState() }.store(in: &cancellables)
    glasses.objectWillChange.sink { [weak self] in self?.objectWillChange.send() }.store(in: &cancellables)
  }

  // MARK: Lifecycle

  func setVisible(_ on: Bool) {
    visible = on
    if on {
      locationService.start()
      refreshLocationState()
      phoneAim.start { [weak self] magnetic, unreliable in self?.onPhoneAim(magnetic, unreliable) }
    } else {
      feedTask?.cancel()
      feedTask = nil
      locationService.stop()
      phoneAim.stop()
    }
  }

  func requestLocation() { locationService.requestPermission() }

  func retry() { restartFeed() }

  private func settingsChanged(from old: AppSettings) {
    if old.dataMode != settings.dataMode || old.sampleScene != settings.sampleScene || old.radiusKm != settings.radiusKm {
      refreshLocationState()
    }
  }

  private func saveSettings() {
    if let data = try? JSONEncoder().encode(settings) { UserDefaults.standard.set(data, forKey: "settings") }
  }

  // MARK: Location and data

  private func refreshLocationState() {
    if settings.dataMode == .sample, let scene = sampleScenes.first(where: { $0.id == settings.sampleScene }) ?? sampleScenes.first {
      location = .known(scene.center, isSample: true)
      restartFeed()
      return
    }
    switch locationService.authorization {
    case .notDetermined: location = .needsPermission; clearAircraft()
    case .denied, .restricted: location = .denied; clearAircraft()
    default:
      if let p = locationService.location {
        location = .known(p, isSample: false)
        restartFeed()
      } else {
        location = .searching
      }
    }
  }

  private func onLocation(_ p: GeoPoint) {
    guard settings.dataMode == .live else { return }
    location = .known(p, isSample: false)
    if feedCenter.map({ Geo.distanceKm($0, p) > 0.5 }) ?? true { restartFeed() }
  }

  private func restartFeed() {
    feedTask?.cancel()
    guard visible, let center = location.point else { return }
    feedCenter = center
    let radius = Double(settings.radiusKm)
    let source: AircraftSource
    let lookup: AircraftInfoLookup
    if settings.dataMode == .sample, let s = sampleSources[settings.sampleScene] ?? sampleSources.values.first {
      source = s
      lookup = s
    } else {
      source = liveSource
      lookup = liveLookup
    }
    if nearby.isEmpty { feed = .loading }
    feedTask = Task { [weak self] in
      while !Task.isCancelled {
        await self?.fetchOnce(center: center, radiusKm: radius, source: source, lookup: lookup)
        try? await Task.sleep(for: .seconds(source.minRefreshInterval))
      }
    }
  }

  private func fetchOnce(center: GeoPoint, radiusKm: Double, source: AircraftSource, lookup: AircraftInfoLookup) async {
    switch await source.aircraftNear(center: center, radiusKm: radiusKm) {
    case let .success(aircraft, _, attribution, stale):
      let near = TargetSelector.nearby(user: center, aircraft: aircraft, radiusKm: radiusKm).map(\.aircraft)
      publish(center, near, radiusKm, attribution: attribution, lookup: lookup, stale: stale)
      let enriched = await enrich(near, lookup: lookup)
      guard !Task.isCancelled else { return }
      publish(center, enriched, radiusKm, attribution: attribution, lookup: lookup, stale: stale)
    case let .failure(reason):
      feed = .error(reason)
    }
  }

  private func publish(_ center: GeoPoint, _ aircraft: [Aircraft], _ radiusKm: Double, attribution: String?, lookup: AircraftInfoLookup, stale: FailureReason?) {
    allAircraft = aircraft
    nearby = TargetSelector.nearby(user: center, aircraft: aircraft, radiusKm: radiusKm)
    feed = stale.map { .stale($0) } ?? .ok
    var a: [String] = []
    for s in [attribution, lookup.attribution].compactMap({ $0 }) where !a.contains(s) { a.append(s) }
    attributions = a
    if let p = point.pointing { point.selection = TargetSelector.select(user: center, aircraft: aircraft, pointing: p) }
  }

  private func clearAircraft() {
    feedTask?.cancel()
    allAircraft = []
    nearby = []
    attributions = []
    point.selection = nil
  }

  func aircraft(_ icao24: String) -> SeenAircraft? { nearby.first { $0.aircraft.icao24 == icao24 } }

  // MARK: Compass

  private func onPhoneAim(_ magnetic: Pointing, _ unreliable: Bool) {
    let p = Pointing(bearingDeg: Geo.normalizeDeg(magnetic.bearingDeg + locationService.declinationDeg), elevationDeg: magnetic.elevationDeg)
    point.pointing = p
    point.compassUnreliable = unreliable
    point.selection = location.point.map { TargetSelector.select(user: $0, aircraft: allAircraft, pointing: p) }
  }

  // MARK: Ask

  /// "What's that plane?": glasses head direction if available, otherwise the phone's; spoken on the
  /// glasses when connected, otherwise on the iPhone.
  func ask(_ trigger: AskTrigger = .phoneButton) {
    Task {
      let answer: Answer
      if let user = location.point {
        let pointing = glasses.headPointing ?? point.pointing
        let selection: Selection
        if let p = pointing {
          selection = TargetSelector.select(user: user, aircraft: allAircraft, pointing: p)
        } else if let nearest = TargetSelector.nearby(user: user, aircraft: allAircraft.filter { !$0.onGround }, radiusKm: .infinity).first {
          selection = .hit(nearest, offsetDeg: 0)
        } else {
          selection = .noAircraft
        }
        answer = AnswerBuilder.answer(selection, unit: settings.unit)
      } else {
        answer = Answer(spoken: "I don't know where you are yet. Turn on location and try again.", card: nil)
      }
      lastAnswer = answer
      if glasses.status.isConnected { await glasses.deliver(answer) } else { await phoneVoice.speak(answer.spoken) }
    }
  }

  // MARK: Simulated glasses

  func setSimulatedGlasses(_ on: Bool, model: SimulatedModel? = nil) {
    settings.simulatedGlasses = on
    if let model { settings.simulatedModel = model }
    if on {
      simulated.enable(settings.simulatedModel)
      setSimHead(simHead)
    } else {
      simulated.disable()
    }
  }

  func setSimHead(_ head: Pointing) {
    simHead = head
    simulated.setHead(magneticBearingDeg: Geo.normalizeDeg(head.bearingDeg - locationService.declinationDeg), elevationDeg: head.elevationDeg)
  }

  func simLookAtNearest() {
    if let s = nearby.first(where: { !$0.aircraft.onGround }) {
      setSimHead(Pointing(bearingDeg: s.look.bearingDeg, elevationDeg: s.look.elevationDeg))
    }
  }
}
