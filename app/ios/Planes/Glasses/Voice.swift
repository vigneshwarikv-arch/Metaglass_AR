import AVFoundation

/// Speaks answers with iOS text-to-speech. DAT 1.0 has no "speak on the glasses" API; Meta glasses
/// are a Bluetooth audio device, so speech plays in their open-ear speakers while connected.
/// TODO(glasses): confirm on real glasses that the answer is heard in the glasses, not the iPhone.
@MainActor
final class Voice: NSObject, AVSpeechSynthesizerDelegate {
  private let synth = AVSpeechSynthesizer()
  private var done: CheckedContinuation<Void, Never>?

  override init() {
    super.init()
    synth.delegate = self
  }

  func speak(_ text: String) async {
    let session = AVAudioSession.sharedInstance()
    try? session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers, .allowBluetoothA2DP])
    try? session.setActive(true)
    synth.stopSpeaking(at: .immediate)
    let u = AVSpeechUtterance(string: text)
    u.voice = AVSpeechSynthesisVoice(language: "en-IN") ?? AVSpeechSynthesisVoice(language: "en-US")
    await withCheckedContinuation { c in
      done?.resume()
      done = c
      synth.speak(u)
    }
    try? session.setActive(false, options: .notifyOthersOnDeactivation)
  }

  nonisolated func speechSynthesizer(_ s: AVSpeechSynthesizer, didFinish u: AVSpeechUtterance) {
    Task { @MainActor in self.finish() }
  }

  nonisolated func speechSynthesizer(_ s: AVSpeechSynthesizer, didCancel u: AVSpeechUtterance) {
    Task { @MainActor in self.finish() }
  }

  private func finish() {
    done?.resume()
    done = nil
  }
}
