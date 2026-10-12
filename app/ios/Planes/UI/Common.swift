import PlanesCore
import SwiftUI

extension Color {
  /// Same palette as the website and Android app.
  static let sky = Color(light: 0x1D5FD1, dark: 0x6EA2FF)
  static let beacon = Color(light: 0xE0561B, dark: 0xFF7A3D)

  init(light: UInt32, dark: UInt32) {
    self.init(
      UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
  }
}

extension UIColor {
  convenience init(hex: UInt32) {
    self.init(
      red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
  }
}

extension Aircraft {
  /// "IndiGo" / "Flight AIC540" / "Aircraft 80139E".
  var titleText: String { info.airline ?? callsign.map { "Flight \($0)" } ?? "Aircraft \(icao24.uppercased())" }

  /// "AIC540 · A320neo · DEL → MAA".
  var subtitleText: String {
    let parts: [String?] = [
      info.airline != nil ? callsign : nil,
      AircraftTypes.spokenName(info),
      info.route.map { "\($0.origin?.code ?? "?") → \($0.destination?.code ?? "?")" },
    ]
    let s = parts.compactMap { $0 }.joined(separator: " · ")
    return s.isEmpty ? "Type and route unknown" : s
  }
}

extension FailureReason {
  var message: String {
    switch self {
    case .noInternet: return "You're offline. Check your connection."
    case let .rateLimited(sec): return "Live data limit reached for now" + (sec.map { " — try again in \($0 < 120 ? "\($0) s" : "\($0 / 60) min")." } ?? ".")
    case let .http(code): return "Flight data service error (\(code))."
    case .unauthorized: return "OpenSky login failed. Check the credentials in Secrets.xcconfig."
    case let .other(m): return m
    }
  }
}

struct EmptyStateView: View {
  let title: String
  let message: String
  var primary: (String, () -> Void)?
  var secondary: (String, () -> Void)?

  var body: some View {
    VStack(spacing: 12) {
      Text(title).font(.headline).multilineTextAlignment(.center)
      Text(message).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
      HStack {
        if let p = primary { Button(p.0, action: p.1).buttonStyle(.borderedProminent) }
        if let s = secondary { Button(s.0, action: s.1).buttonStyle(.bordered) }
      }
    }
    .padding(32)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
  }
}

struct AttributionView: View {
  let lines: [String]

  var body: some View {
    VStack(alignment: .leading, spacing: 2) {
      ForEach(lines, id: \.self) { Text($0).font(.caption2).foregroundStyle(.secondary) }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
  }
}

/// The last spoken answer, shown above every screen.
struct AnswerBanner: View {
  let answer: Answer
  let onDismiss: () -> Void

  var body: some View {
    HStack(alignment: .center) {
      Text(answer.spoken).font(.subheadline.weight(.semibold)).foregroundStyle(.white)
      Spacer(minLength: 8)
      Button("OK", action: onDismiss).foregroundStyle(.white)
    }
    .padding(14)
    .background(Color.sky, in: RoundedRectangle(cornerRadius: 14))
    .padding(.horizontal, 12)
  }
}
