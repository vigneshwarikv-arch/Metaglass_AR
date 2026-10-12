import Foundation
import MWDATCore
import MWDATDisplay
import MWDATMotion
import MWDATSpeech
import PlanesCore

enum AskTrigger { case voiceLaunch, spokenQuestion, phoneButton }

enum GlassesStatus: Equatable {
  case notRegistered
  case registering
  case noGlasses
  case connecting
  case connected(name: String?, hasDisplay: Bool, headTracking: Bool, listening: Bool)
  case error(String)

  var isConnected: Bool { if case .connected = self { return true } else { return false } }
}

/// Meta glasses via the Wearables Device Access Toolkit (DAT) 1.0 for iOS. Same flow as the Android
/// DatGlassesConnector: register with Meta AI → session when glasses connect → Motion (head
/// direction), Speech ("what's that plane?") and, on Ray-Ban Display, Display (answer card).
/// "Hey Meta, start [BrandName]" (Voice Invocations) also counts as a question.
/// Motion, Speech and Voice Invocations are experimental in DAT 1.0 (dev and release-channel testing only).
@MainActor
final class DatGlasses: ObservableObject {
  @Published private(set) var status: GlassesStatus = .notRegistered
  /// Where the wearer's head points (true north), or nil without head tracking.
  @Published private(set) var headPointing: Pointing?

  var onAsk: ((AskTrigger) -> Void)?
  var declinationDeg: () -> Double = { 0 }

  private let voice = Voice()
  private let headAim = HeadAim()
  private var started = false
  private var registered = false
  private var tasks: [Task<Void, Never>] = []
  private var session: DeviceSession?
  private var sessionTasks: [Task<Void, Never>] = []
  private var tokens = ListenerTokenBag()
  private var display: Display?
  private var displayReady = false
  private var listening = false
  private var deviceName: String?
  private var deviceHasDisplay = false
  private var connectedDevice: DeviceIdentifier?
  private var voiceStream: VoiceInvocationsStream?
  private let voiceTokens = ListenerTokenBag()
  private var lastAsk = Date.distantPast

  /// Starts observing registration and devices. Safe to call repeatedly.
  func start() {
    guard !started else { return }
    started = true
    tasks.append(
      Task { [weak self] in
        for await state in Wearables.shared.registrationStateStream() {
          self?.onRegistration(state)
        }
      })
    tasks.append(
      Task { [weak self] in
        for await ids in Wearables.shared.devicesStream() {
          self?.onDevices(ids)
        }
      })
  }

  func startRegistration() async {
    start()
    do { try await Wearables.shared.startRegistration() } catch { status = .error(error.localizedDescription) }
  }

  func startUnregistration() async {
    do { try await Wearables.shared.startUnregistration() } catch { status = .error(error.localizedDescription) }
  }

  /// Glasses microphone permission (opens Meta AI). Call from a button tap.
  func requestMicrophone() async {
    _ = try? await Wearables.shared.requestPermission(.microphone)
    if let s = session { await attachSpeech(s) }
  }

  // MARK: Registration and devices

  private func onRegistration(_ state: RegistrationState) {
    registered = state == .registered
    switch state {
    case .registered:
      maybeStartSession()
    case .registering:
      status = .registering
    default:
      stopSession()
      status = .notRegistered
    }
  }

  private func onDevices(_ ids: [DeviceIdentifier]) {
    let connected = ids.first { id in Wearables.shared.deviceForIdentifier(id)?.linkState == .connected }
    connectedDevice = connected
    if let id = connected, let device = Wearables.shared.deviceForIdentifier(id) {
      deviceName = device.nameOrId()
      deviceHasDisplay = device.supportsDisplay()
      startVoiceInvocations(device: id)
    }
    maybeStartSession()
  }

  // MARK: Session

  private func maybeStartSession() {
    guard session == nil, registered else { return }
    guard connectedDevice != nil else {
      status = .noGlasses
      return
    }
    status = .connecting
    do {
      let s = try Wearables.shared.createSession(deviceSelector: AutoDeviceSelector(wearables: Wearables.shared))
      session = s
      sessionTasks.append(
        Task { [weak self] in
          for await state in s.stateStream() {
            switch state {
            case .started: await self?.onSessionStarted(s)
            case .stopped: self?.onSessionStopped()
            default: break
            }
          }
        })
      sessionTasks.append(
        Task { [weak self] in
          for await error in s.errorStream() {
            self?.status = .error(error.localizedDescription)
          }
        })
      try s.start()
    } catch {
      status = .error(error.localizedDescription)
      session = nil
    }
  }

  private func onSessionStarted(_ s: DeviceSession) async {
    attachMotion(s)
    await attachSpeech(s)
    if deviceHasDisplay { attachDisplay(s) }
    publishConnected()
  }

  private func onSessionStopped() {
    stopSession()
    Task { [weak self] in
      try? await Task.sleep(for: .seconds(2))
      self?.maybeStartSession()
    }
  }

  private func stopSession() {
    sessionTasks.forEach { $0.cancel() }
    sessionTasks.removeAll()
    tokens = ListenerTokenBag()
    session?.stop()
    session = nil
    display = nil
    displayReady = false
    listening = false
    headPointing = nil
  }

  private func publishConnected() {
    status = .connected(name: deviceName, hasDisplay: displayReady, headTracking: headPointing != nil, listening: listening)
  }

  // MARK: Capabilities

  private func attachMotion(_ s: DeviceSession) {
    guard let motion = try? s.addMotion(configuration: MotionConfiguration(samplingRate: .hz10)) else { return }
    let samples = motion.samples
    sessionTasks.append(
      Task { [weak self] in
        for await sample in samples {
          self?.onMotion(sample)
        }
      })
    motion.start()
  }

  private func onMotion(_ sample: MotionSample) {
    guard sample.source == .glasses, let a = sample.accelerometer, let m = sample.magnetometer else { return }
    guard
      let p = headAim.pointing(
        up: Vec3(Double(a.x), Double(a.y), Double(a.z)), field: Vec3(Double(m.x), Double(m.y), Double(m.z)),
        declinationDeg: declinationDeg())
    else { return }
    let first = headPointing == nil
    headPointing = p
    if first { publishConnected() }
  }

  private func attachSpeech(_ s: DeviceSession) async {
    guard !listening else { return }
    guard (try? await Wearables.shared.checkPermissionStatus(.microphone)) == .granted else { return }
    guard let speech = try? s.addSpeech() else { return }
    speech.transcriptionPublisher.listen { [weak self] result in
      guard result.isFinal, QuestionMatcher.isPlaneQuestion(result.text) else { return }
      Task { @MainActor in self?.askOnce(.spokenQuestion) }
    }.store(in: tokens)
    speech.start()
    listening = true
    publishConnected()
  }

  private func attachDisplay(_ s: DeviceSession) {
    guard let d = try? s.addDisplay() else { return }
    display = d
    d.statePublisher.listen { [weak self] state in
      Task { @MainActor in
        self?.displayReady = state == .started
        self?.publishConnected()
      }
    }.store(in: tokens)
    d.start()
  }

  // MARK: Voice ("Hey Meta, start [BrandName]")

  private func startVoiceInvocations(device: DeviceIdentifier) {
    do {
      if voiceStream == nil {
        let stream = try VoiceInvocationsStream(wearables: Wearables.shared)
        stream.invocationsPublisher.listen { [weak self] invocation in
          guard let launch = invocation as? LaunchApp else { return }
          // Every invocation must be answered exactly once.
          Task { _ = await launch.responseHandle.sendSuccess(actionOutput: nil) }
          Task { @MainActor in self?.askOnce(.voiceLaunch) }
        }.store(in: voiceTokens)
        voiceStream = stream
      }
      try voiceStream?.start(deviceIdentifier: device)
    } catch {
      print("Voice invocations unavailable: \(error)")
    }
  }

  private func askOnce(_ trigger: AskTrigger) {
    guard Date().timeIntervalSince(lastAsk) > 3 else { return }
    lastAsk = Date()
    onAsk?(trigger)
  }

  // MARK: Output

  func deliver(_ answer: Answer) async {
    if let d = display, displayReady, let card = answer.card {
      try? await DisplayCard.send(card, to: d)
    }
    await voice.speak(answer.spoken)
  }
}

/// Recognises "what's that plane?" and close variants in a transcript.
enum QuestionMatcher {
  static func isPlaneQuestion(_ text: String) -> Bool {
    text.lowercased().range(
      of: #"\b(what|which|whats|what's)\b.{0,12}\b(plane|airplane|aeroplane|aircraft|flight|jet)\b"#, options: .regularExpression) != nil
  }
}
