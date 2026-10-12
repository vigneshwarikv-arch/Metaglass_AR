import Foundation
import MWDATMockDevice
import MWDATMotion
import UIKit

enum SimulatedModel: String, CaseIterable, Codable {
  case rayBanMeta, rayBanDisplay

  var label: String { self == .rayBanMeta ? "Ray-Ban Meta" : "Meta Ray-Ban Display" }
}

/// Simulated Meta glasses (DAT MockDeviceKit) for testing without hardware. The app's real DatGlasses
/// connector talks to them exactly as it would to real glasses.
@MainActor
final class SimulatedGlasses {
  private var glasses: (any MockGlasses)?

  var isOn: Bool { glasses != nil }

  func enable(_ model: SimulatedModel) {
    disable()
    MockDeviceKit.shared.enable()
    guard let g = try? MockDeviceKit.shared.pairGlasses(model: model == .rayBanDisplay ? .metaRayBanDisplay : .rayBanMeta) else {
      return
    }
    g.powerOn()
    g.unfold()
    g.don()
    // Speech comes from our buttons, not the iPhone microphone.
    g.services.speech.setTranscriptionSource(.injected)
    glasses = g
    setHead(magneticBearingDeg: 0, elevationDeg: 15)
  }

  func disable() {
    guard glasses != nil else { return }
    glasses = nil
    MockDeviceKit.shared.disable()
  }

  /// Head facing `magneticBearingDeg`, tilted up `elevationDeg`, in the glasses' axes (+X up, +Y right, +Z forward).
  func setHead(magneticBearingDeg: Double, elevationDeg: Double) {
    guard let g = glasses else { return }
    let b = magneticBearingDeg * .pi / 180, e = elevationDeg * .pi / 180
    let gravity = 9.80665, field = 40.0
    let up = MWDATMockDevice.Vector3(x: Float(gravity * cos(e)), y: 0, z: Float(gravity * sin(e)))
    let north = MWDATMockDevice.Vector3(
      x: Float(-field * cos(b) * sin(e)), y: Float(-field * sin(b)), z: Float(field * cos(b) * cos(e)))
    let samples = (0..<10).map { i in
      MotionSample(
        timestampNs: Int64(i) * 100_000_000,
        accelerometer: up,
        gyroscope: .init(x: 0, y: 0, z: 0),
        magnetometer: north,
        source: .glasses)
    }
    g.services.motion.setMotionFeed(samples)
  }

  func sayWhatsThatPlane() {
    glasses?.services.speech.simulateTranscription(text: "what's that plane", isFinal: true, confidence: 1)
  }

  func voiceLaunch() {
    _ = glasses?.services.voiceInvocation.sendLaunchAppAction()
  }

  /// What the simulated Ray-Ban Display shows.
  func displayPreview() -> UIView? { glasses?.services.display.createPreviewView() }
}
