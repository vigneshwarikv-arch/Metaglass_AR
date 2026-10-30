import PlanesCore
import PlanesData
import SwiftUI

/*
 * Pilot mode screens (same content as Android's ui/pilot). Safety rules (CLAUDE.md): supplementary aid
 * only, VFR daytime; GPS altitude always labelled GPS; nothing that looks like an altimeter or airspeed
 * indicator; one-tap off.
 */

private let offRed = Color(red: 0.78, green: 0.16, blue: 0.16)

/// Full-screen pilot mode: the one-time disclaimer, then the pilot screen.
struct PilotRootView: View {
  @ObservedObject var pilot: PilotModel
  let simulated: SimulatedGlasses
  let simulatedGlassesOn: Bool
  let simulatedModel: SimulatedModel
  @Environment(\.dismiss) private var dismiss

  var body: some View {
    NavigationStack {
      if pilot.settings.disclaimerAccepted {
        PilotMainView(pilot: pilot, simulated: simulatedGlassesOn ? simulated : nil, simulatedModel: simulatedModel) {
          pilot.stop()
          dismiss()
        }
        .onAppear { pilot.start() }
      } else {
        PilotDisclaimerView(onAccept: { pilot.settings.disclaimerAccepted = true }, onCancel: { dismiss() })
      }
    }
    // Turned off by voice ("pilot mode off"): leave the screen.
    .onChange(of: pilot.active) { _, isActive in
      if !isActive { dismiss() }
    }
  }
}

struct PilotDisclaimerView: View {
  let onAccept: () -> Void
  let onCancel: () -> Void
  @State private var understood = false

  private let points = [
    "Pilot mode is a supplementary situational-awareness aid. It is NOT a certified or primary instrument and must not be used for navigation or collision avoidance.",
    "VFR and daytime only. Keep looking outside. You remain responsible for see-and-avoid and for following ATC, your charts and your aircraft's instruments.",
    "Traffic comes only from your portable ADS-B receiver. Many aircraft are not shown (no ADS-B Out, out of range, or filtered). No traffic shown does not mean no traffic.",
    "GPS altitude is labelled GPS. It is not your altimeter and can differ from it by hundreds of feet. There is no airspeed.",
    "The phone, receiver or glasses can fail, lag or show wrong data at any time.",
    "Turn pilot mode off with one tap on OFF, or by saying “pilot mode off” to your glasses.",
  ]

  var body: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: 14) {
        Text("Before you use pilot mode").font(.title2.bold())
        ForEach(points, id: \.self) { Text("•  \($0)").font(.body) }
        Toggle("I understand and accept this.", isOn: $understood).fontWeight(.semibold)
        HStack {
          Button("Accept and open pilot mode", action: onAccept).buttonStyle(.borderedProminent).disabled(!understood)
          Button("Cancel", action: onCancel).buttonStyle(.bordered)
        }
      }
      .padding(20)
    }
  }
}

struct PilotMainView: View {
  @ObservedObject var pilot: PilotModel
  let simulated: SimulatedGlasses?
  let simulatedModel: SimulatedModel
  let onOff: () -> Void

  var body: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: 10) {
        header
        receiverRow
        if pilot.phoneGps == .noPermission && pilot.ownship == nil {
          Text("Allow location for this app in iOS Settings to see GPS readouts.").font(.footnote)
        }
        readouts
        directToCard
        nearestCard
        trafficCard
        Text("Not a certified instrument. GPS altitude is not an altimeter.  ·  \(AirportDatabase.attribution)")
          .font(.caption2).foregroundStyle(.secondary)
        if let simulated { SimulatedGlassesPilotPanel(simulated: simulated, model: simulatedModel) }
      }
      .padding(.horizontal, 14)
      .padding(.vertical, 10)
    }
    .toolbar(.hidden, for: .navigationBar)
  }

  private var header: some View {
    HStack {
      VStack(alignment: .leading) {
        Text("PILOT MODE").font(.headline.weight(.black))
        Text("Supplementary aid only · VFR · not certified").font(.caption2).foregroundStyle(.secondary)
      }
      Spacer()
      NavigationLink("Settings") { PilotSettingsView(pilot: pilot) }
      Button(action: onOff) { Text("OFF").fontWeight(.black).padding(.horizontal, 8) }
        .buttonStyle(.borderedProminent).tint(offRed)
    }
  }

  private var receiverRow: some View {
    let r = pilot.receiver
    let dot: Color
    let text: String
    switch (pilot.receiverMode, pilot.receiverConnected) {
    case (.off, _):
      dot = .gray
      text = "ADS-B receiver off"
    case (.simulated, false):
      dot = .beacon
      text = "Simulated receiver: starting…"
    case (.wifi, false):
      dot = .beacon
      text = "Receiver: no data — join its Wi-Fi"
    case (let mode, true):
      dot = .green
      let name = mode == .simulated ? "Simulated receiver" : (r.deviceName ?? "Receiver")
      text = "\(name) · connected · \(r.gpsValid ? "receiver GPS OK" : "receiver has no GPS fix")"
    }
    let source: String
    switch pilot.ownship?.source {
    case .receiver: source = "Position from receiver GPS"
    case .phone: source = "Position from iPhone GPS"
    case nil: source = pilot.phoneGps == .noPermission ? "No position: location not allowed" : "Waiting for GPS…"
    }
    return VStack(alignment: .leading, spacing: 2) {
      HStack(spacing: 8) {
        Circle().fill(dot).frame(width: 10, height: 10)
        Text(text).font(.footnote.weight(.semibold))
      }
      Text(source).font(.footnote).foregroundStyle(.secondary)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(10)
    .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 10))
  }

  private var readouts: some View {
    let own = pilot.ownship
    return VStack(alignment: .leading, spacing: 8) {
      HStack(spacing: 8) {
        Readout(label: "GS", value: own?.groundspeedKt.map { "\(Int($0.rounded()))" } ?? PilotText.dash, unit: "kt")
        Readout(label: "TRK", value: own?.trackDeg.map { NavUnits.degreesText($0) } ?? PilotText.dash, unit: "° true")
      }
      Readout(label: "GPS ALT", value: PilotText.gpsAltitude(own?.gpsAltitudeFt).replacingOccurrences(of: " ft GPS", with: ""), unit: "ft GPS")
      if let own {
        Text(
          own.gpsAltitudeDatum == .ellipsoid
            ? "GPS height above the WGS-84 ellipsoid: can differ from MSL by 100 ft or more. Not your altimeter."
            : "GPS altitude (MSL). Not your altimeter."
        ).font(.caption2).foregroundStyle(.secondary)
      }
    }
  }

  private var directToCard: some View {
    PilotCard {
      if let dest = pilot.destination {
        HStack(spacing: 12) {
          let g = pilot.directTo?.guidance
          // Arrow relative to our track (track-up); north-up bearing when we have no track.
          VStack(spacing: 0) {
            Image(systemName: "location.north.fill").font(.system(size: 40)).foregroundStyle(Color.sky)
              .rotationEffect(.degrees(g?.turnDeg ?? g?.bearingDeg ?? 0))
              .opacity(g == nil ? 0.3 : 1)
            if g != nil && g?.turnDeg == nil { Text("N↑").font(.caption2) }
          }
          .frame(width: 64, height: 64)
          VStack(alignment: .leading, spacing: 2) {
            Text("→ \(dest.ident)").font(.title2.bold())
            Text(dest.name).font(.footnote).lineLimit(1)
            if let g {
              Text("\(TrafficRules.distanceText(g.distanceNm)) · \(PilotText.ete(g.eteMin)) · BRG \(NavUnits.degreesText(g.bearingDeg))°")
                .fontWeight(.semibold)
              if let t = g.turnDeg {
                let n = Int(abs(t).rounded())
                Text(n < 3 ? "On course" : "Turn \(t > 0 ? "right" : "left") \(n)°").font(.footnote)
              }
            } else {
              Text("Waiting for GPS…").font(.footnote)
            }
          }
        }
        HStack {
          NavigationLink("Change") { DestinationPickerView(pilot: pilot) }.buttonStyle(.bordered)
          Button("Clear") { pilot.setDestination(nil) }
        }
      } else {
        Text("Direct-to").fontWeight(.bold)
        Text("Pick your destination airport before takeoff.").font(.footnote)
        NavigationLink("Set destination") { DestinationPickerView(pilot: pilot) }.buttonStyle(.borderedProminent)
      }
    }
  }

  private var nearestCard: some View {
    PilotCard {
      Text("Nearest airport").fontWeight(.bold)
      if let n = pilot.nearest {
        Text(
          "\(n.airfield.ident)  \(TrafficRules.distanceText(n.distanceNm)) \(CompassPoint.of(n.bearingDeg).short) (\(NavUnits.degreesText(n.bearingDeg))°)"
        ).font(.title3.bold())
        Text([n.airfield.name, n.airfield.city].compactMap { $0 }.joined(separator: " · ")).font(.footnote)
      } else if !pilot.receiverConnected {
        Text("Connect an ADS-B receiver to show the nearest airport and traffic.").font(.footnote)
      } else {
        Text("Waiting for GPS…").font(.footnote)
      }
    }
  }

  private var trafficCard: some View {
    PilotCard {
      HStack {
        Text("Traffic").fontWeight(.bold)
        Spacer()
        Text(
          "within \(Int(pilot.settings.calloutRangeNm)) NM, ±\(pilot.settings.calloutAltitudeBandFt) ft · voice \(pilot.settings.voiceCallouts ? "on" : "off")"
        ).font(.caption2).foregroundStyle(.secondary)
      }
      if !pilot.receiverConnected {
        Text("No receiver data, so no traffic is shown. Look outside.").font(.footnote)
      } else {
        if let c = pilot.lastCallout {
          Text("Last callout: “\(c.spoken)”").font(.footnote).foregroundStyle(.secondary)
        }
        if pilot.traffic.isEmpty {
          Text("No traffic shown nearby. That does not mean there is none.").font(.footnote)
        }
        ForEach(Array(pilot.traffic.prefix(5).enumerated()), id: \.element.id) { i, t in
          if i > 0 { Divider() }
          HStack {
            Text(TrafficRules.short(t))
              .font(i == 0 ? .title3.bold() : .body)
              .foregroundStyle(i == 0 && t.converging ? Color.beacon : Color.primary)
            Spacer()
            Text(t.aircraft.callsign ?? t.aircraft.icao24.uppercased()).font(.caption)
          }
        }
      }
    }
  }
}

/// A plain number tile. Deliberately not a dial or tape, so it can't be mistaken for an instrument.
private struct Readout: View {
  let label: String
  let value: String
  let unit: String

  var body: some View {
    VStack(alignment: .leading, spacing: 0) {
      Text(label).font(.caption.bold()).foregroundStyle(.secondary)
      HStack(alignment: .lastTextBaseline, spacing: 6) {
        Text(value).font(.system(size: 44, weight: .bold, design: .monospaced)).minimumScaleFactor(0.6).lineLimit(1)
        Text(unit).font(.subheadline)
      }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(.horizontal, 12)
    .padding(.vertical, 8)
    .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
  }
}

private struct PilotCard<Content: View>: View {
  @ViewBuilder let content: Content

  var body: some View {
    VStack(alignment: .leading, spacing: 6) { content }
      .frame(maxWidth: .infinity, alignment: .leading)
      .padding(12)
      .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
  }
}

struct DestinationPickerView: View {
  @ObservedObject var pilot: PilotModel
  @Environment(\.dismiss) private var dismiss
  @State private var query = ""
  @State private var results: [Airfield] = []

  var body: some View {
    List {
      ForEach(results) { a in
        Button {
          pilot.setDestination(a)
          dismiss()
        } label: {
          VStack(alignment: .leading, spacing: 2) {
            HStack {
              Text(a.ident).fontWeight(.bold).frame(width: 64, alignment: .leading)
              Text(a.name).lineLimit(1)
              Spacer()
              if let here = pilot.ownship?.position {
                Text(TrafficRules.distanceText(NavUnits.nm(Geo.distanceKm(here, a.position)))).font(.caption)
              }
            }
            Text([a.city, a.country, a.iata.map { "IATA \($0)" }].compactMap { $0 }.joined(separator: " · "))
              .font(.caption2).foregroundStyle(.secondary)
          }
        }
        .foregroundStyle(.primary)
      }
      Text(AirportDatabase.attribution).font(.caption2).foregroundStyle(.secondary)
    }
    .navigationTitle("Destination")
    .searchable(text: $query, prompt: "ICAO, FAA, IATA or name (e.g. KPAO)")
    .textInputAutocapitalization(.characters)
    .onChange(of: query) { _, q in results = pilot.search(q) }
  }
}

struct PilotSettingsView: View {
  @ObservedObject var pilot: PilotModel

  var body: some View {
    Form {
      Section("ADS-B receiver (Wi-Fi)") {
        Toggle("Listen for a receiver", isOn: $pilot.settings.receiverEnabled)
        Text("Stratux or Stratus 4: join the receiver's Wi-Fi network, then come back here. The app listens for GDL 90 on UDP port 4000 (iOS asks for Local Network access the first time).")
          .font(.footnote).foregroundStyle(.secondary)
      }
      Section("Simulated receiver") {
        Toggle("Use simulated receiver", isOn: $pilot.settings.simulatedReceiver)
        if pilot.settings.simulatedReceiver {
          Picker("Scenario", selection: $pilot.settings.simulatedScenario) {
            ForEach(pilot.scenarios) { Text($0.name).tag($0.id) }
          }
        }
        Text("For testing on the ground: plays scripted traffic through the same decoder. Turn it off before flight.")
          .font(.footnote).foregroundStyle(.secondary)
      }
      Section("Traffic callouts") {
        Toggle("Speak callouts", isOn: $pilot.settings.voiceCallouts)
        LabeledContent("Range", value: "\(Int(pilot.settings.calloutRangeNm)) NM")
        Slider(value: $pilot.settings.calloutRangeNm, in: 2...10, step: 1)
        LabeledContent("Altitude band", value: "±\(pilot.settings.calloutAltitudeBandFt) ft")
        Slider(
          value: Binding(get: { Double(pilot.settings.calloutAltitudeBandFt) }, set: { pilot.settings.calloutAltitudeBandFt = Int($0) }),
          in: 500...3000, step: 500)
        Text("One callout at a time, closest or converging first. The same aircraft is repeated only when it gets meaningfully closer.")
          .font(.footnote).foregroundStyle(.secondary)
      }
      Section("Safety") {
        Text("Supplementary aid only. VFR, daytime. GPS altitude is not an altimeter. Say “pilot mode off” to your glasses or tap OFF.")
          .font(.footnote)
        Button("Show the disclaimer again") {
          pilot.stop()
          pilot.settings.disclaimerAccepted = false
        }
      }
    }
    .navigationTitle("Pilot settings")
  }
}

/// Testing without hardware: what the simulated Ray-Ban Display shows, and the voice "off".
private struct SimulatedGlassesPilotPanel: View {
  let simulated: SimulatedGlasses
  let model: SimulatedModel

  var body: some View {
    VStack(alignment: .leading, spacing: 6) {
      Text("Simulated glasses").font(.subheadline.bold())
      Button("Say: “Pilot mode off”") { simulated.sayPilotModeOff() }.buttonStyle(.bordered)
      Button("Ask the glasses (pilot summary)") { simulated.sayWhatsThatPlane() }.buttonStyle(.bordered)
      if model == .rayBanDisplay {
        Text("What the Display shows").font(.caption)
        PilotDisplayPreview(view: simulated.displayPreview()).aspectRatio(1, contentMode: .fit)
      }
    }
  }
}

private struct PilotDisplayPreview: UIViewRepresentable {
  let view: UIView?

  func makeUIView(context: Context) -> UIView {
    view ?? {
      let l = UILabel()
      l.text = "Connecting simulated glasses…"
      l.textAlignment = .center
      return l
    }()
  }

  func updateUIView(_ uiView: UIView, context: Context) {}
}
