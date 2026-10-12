import PlanesCore
import SwiftUI

struct SettingsView: View {
  @EnvironmentObject var model: AppModel

  var body: some View {
    Form {
      Section("Units") {
        Picker("Distance", selection: $model.settings.unit) {
          Text("Kilometres").tag(DistanceUnit.kilometres)
          Text("Miles").tag(DistanceUnit.miles)
        }
        .pickerStyle(.segmented)
        Text("Altitude is always in feet, like pilots use.").font(.footnote).foregroundStyle(.secondary)
      }

      Section("Search radius: \(Units.distanceText(Double(model.settings.radiusKm), model.settings.unit))") {
        Slider(
          value: Binding(get: { Double(model.settings.radiusKm) }, set: { model.settings.radiusKm = Int($0) }), in: 10...100, step: 10)
      }

      Section("Flight data") {
        Picker("Source", selection: $model.settings.dataMode) {
          Text("Live").tag(DataMode.live)
          Text("Sample (offline)").tag(DataMode.sample)
        }
        .pickerStyle(.segmented)
        if model.settings.dataMode == .live {
          Text(
            model.hasOpenSkyCredentials
              ? "OpenSky account connected: updates every 10 seconds."
              : "Using OpenSky without an account: updates every 30 seconds, about 400 updates a day."
          ).font(.footnote).foregroundStyle(.secondary)
        } else {
          Picker("Place", selection: $model.settings.sampleScene) {
            ForEach(model.sampleScenes) { Text($0.name).tag($0.id) }
          }
          Text("Made-up flights for testing anywhere. Your location is set to the sample place.").font(.footnote).foregroundStyle(.secondary)
        }
      }

      Section("Meta glasses") {
        Text(statusText).fontWeight(.semibold)
        switch model.glasses.status {
        case .notRegistered, .error:
          Button("Connect glasses") { Task { await model.glasses.startRegistration() } }
        case let .connected(_, _, _, listening):
          if !listening {
            Button("Allow glasses microphone") { Task { await model.glasses.requestMicrophone() } }
          }
          Button("Disconnect", role: .destructive) { Task { await model.glasses.startUnregistration() } }
        default:
          EmptyView()
        }
        Text("Say “Hey Meta, start \(Brand.name)”, or with the microphone allowed, “What's that plane?”. Works with Ray-Ban Meta and Meta Ray-Ban Display.")
          .font(.footnote).foregroundStyle(.secondary)
      }

      SimulatedGlassesSection()

      Text("\(Brand.name) · prototype · not affiliated with Meta or Ray-Ban.").font(.caption2).foregroundStyle(.secondary)
    }
    .navigationTitle("Settings")
  }

  private var statusText: String {
    switch model.glasses.status {
    case .notRegistered: return "Not connected"
    case .registering: return "Connecting to Meta AI…"
    case .noGlasses: return "Connected to Meta AI · glasses not nearby"
    case .connecting: return "Connecting to glasses…"
    case let .connected(name, display, head, listening):
      return ["Connected" + (name.map { " to \($0)" } ?? ""), head ? "head tracking on" : "no head tracking yet", display ? "display card on" : nil, listening ? "listening" : nil]
        .compactMap { $0 }.joined(separator: " · ")
    case let .error(m): return "Problem: \(m)"
    }
  }
}

/// Testing without hardware: Meta's MockDeviceKit pretends to be connected glasses.
private struct SimulatedGlassesSection: View {
  @EnvironmentObject var model: AppModel

  var body: some View {
    Section {
      Toggle(isOn: Binding(get: { model.settings.simulatedGlasses }, set: { model.setSimulatedGlasses($0) })) {
        VStack(alignment: .leading) {
          Text("Simulated glasses")
          Text("For testing without real glasses. Uses Meta's MockDeviceKit.").font(.caption).foregroundStyle(.secondary)
        }
      }
      if model.settings.simulatedGlasses {
        Picker("Model", selection: Binding(get: { model.settings.simulatedModel }, set: { model.setSimulatedGlasses(true, model: $0) })) {
          ForEach(SimulatedModel.allCases, id: \.self) { Text($0.label).tag($0) }
        }
        let head = model.simHead
        Text("Head facing \(Int(head.bearingDeg.rounded()))° \(CompassPoint.of(head.bearingDeg).short) · \(Int(head.elevationDeg.rounded()))° up")
          .fontWeight(.semibold)
        LabeledContent("Turn head") {
          Slider(value: Binding(get: { head.bearingDeg }, set: { model.setSimHead(Pointing(bearingDeg: $0, elevationDeg: head.elevationDeg)) }), in: 0...359)
        }
        LabeledContent("Look up") {
          Slider(value: Binding(get: { head.elevationDeg }, set: { model.setSimHead(Pointing(bearingDeg: head.bearingDeg, elevationDeg: $0)) }), in: -5...85)
        }
        Button("Look at the nearest plane") { model.simLookAtNearest() }
        Button("Say: “What's that plane?”") { model.simulated.sayWhatsThatPlane() }
        Button("Say: “Hey Meta, start \(Brand.name)”") { model.simulated.voiceLaunch() }
        if model.settings.simulatedModel == .rayBanDisplay {
          Text("What the Display shows").font(.caption)
          DisplayPreview(view: model.simulated.displayPreview()).aspectRatio(1, contentMode: .fit)
            .id(model.glasses.status.isConnected)
        }
      }
    }
  }
}

private struct DisplayPreview: UIViewRepresentable {
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
