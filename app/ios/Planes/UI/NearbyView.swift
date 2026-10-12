import PlanesCore
import SwiftUI

struct NearbyView: View {
  @EnvironmentObject var model: AppModel

  var body: some View {
    content.navigationTitle("Nearby")
  }

  @ViewBuilder private var content: some View {
    switch model.location {
    case .needsPermission:
      EmptyStateView(
        title: "Allow location to see planes near you",
        message: "Your location is only used on this iPhone to work out which planes are around you.",
        primary: ("Allow location", model.requestLocation),
        secondary: ("Try sample data", { model.settings.dataMode = .sample }))
    case .denied:
      EmptyStateView(
        title: "Location is off for this app",
        message: "Turn it on in the iPhone Settings app, or try the sample data.",
        primary: ("Try sample data", { model.settings.dataMode = .sample }))
    case .searching:
      ProgressView("Finding your location…").frame(maxHeight: .infinity)
    case .known:
      list
    }
  }

  @ViewBuilder private var list: some View {
    if model.nearby.isEmpty {
      switch model.feed {
      case .loading: ProgressView("Looking for planes…").frame(maxHeight: .infinity)
      case let .error(reason):
        EmptyStateView(
          title: "Can't load planes", message: reason.message, primary: ("Try again", model.retry),
          secondary: ("Try sample data", { model.settings.dataMode = .sample }))
      default:
        EmptyStateView(
          title: "No planes within \(Units.distanceText(Double(model.settings.radiusKm), model.settings.unit))",
          message: "The sky is quiet right now. We'll keep checking.")
      }
    } else {
      List {
        if case let .stale(reason) = model.feed {
          Text(reason.message + " Showing the last data.").font(.footnote).foregroundStyle(.secondary)
        }
        RadarMap(nearby: model.nearby, radiusKm: Double(model.settings.radiusKm), unit: model.settings.unit)
          .listRowInsets(EdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8))
        Section("\(model.nearby.count) planes · nearest first") {
          ForEach(model.nearby) { seen in
            NavigationLink(value: seen.aircraft.icao24) { AircraftRow(seen: seen, unit: model.settings.unit) }
          }
        }
        AttributionView(lines: model.attributions)
      }
      .navigationDestination(for: String.self) { DetailsView(icao24: $0) }
    }
  }
}

struct AircraftRow: View {
  let seen: SeenAircraft
  let unit: DistanceUnit

  var body: some View {
    let a = seen.aircraft
    HStack {
      VStack(alignment: .leading, spacing: 2) {
        Text(a.titleText).font(.subheadline.weight(.semibold))
        Text(a.subtitleText).font(.caption).foregroundStyle(.secondary)
        Text(
          [a.onGround ? "On the ground" : Units.altitudeText(a.altitudeM), a.onGround ? nil : Units.speedText(a.speedMs, unit)]
            .compactMap { $0 }.joined(separator: " · ")
        ).font(.caption).foregroundStyle(.secondary)
      }
      Spacer()
      VStack(alignment: .trailing) {
        Text(Units.distanceText(seen.look.groundDistanceKm, unit)).font(.subheadline)
        Text(CompassPoint.of(seen.look.bearingDeg).short).font(.caption).foregroundStyle(Color.sky)
      }
    }
  }
}

/// North-up map: you in the centre, rings at half and full radius, planes pointing where they fly.
struct RadarMap: View {
  let nearby: [SeenAircraft]
  let radiusKm: Double
  let unit: DistanceUnit

  var body: some View {
    Canvas { ctx, size in
      let r = min(size.width, size.height) / 2 * 0.92
      let c = CGPoint(x: size.width / 2, y: size.height / 2)
      ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(Color(.secondarySystemBackground)))
      for f in [1.0, 0.5] {
        ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r * f, y: c.y - r * f, width: 2 * r * f, height: 2 * r * f)), with: .color(.gray.opacity(0.3)))
      }
      ctx.draw(Text("N").font(.caption.bold()).foregroundColor(.sky), at: CGPoint(x: c.x, y: c.y - r + 10))
      ctx.draw(Text(Units.distanceText(radiusKm / 2, unit)).font(.caption2).foregroundColor(.secondary), at: CGPoint(x: c.x + 24, y: c.y + r / 2 + 10))
      ctx.fill(Path(ellipseIn: CGRect(x: c.x - 5, y: c.y - 5, width: 10, height: 10)), with: .color(.sky))
      for seen in nearby {
        let f = min(seen.look.groundDistanceKm / radiusKm, 1)
        let b = seen.look.bearingDeg * .pi / 180
        let p = CGPoint(x: c.x + r * f * sin(b), y: c.y - r * f * cos(b))
        var plane = Path()
        plane.move(to: CGPoint(x: 0, y: -7))
        plane.addLine(to: CGPoint(x: 5, y: 5))
        plane.addLine(to: CGPoint(x: 0, y: 2))
        plane.addLine(to: CGPoint(x: -5, y: 5))
        plane.closeSubpath()
        let t = CGAffineTransform(translationX: p.x, y: p.y).rotated(by: (seen.aircraft.trackDeg ?? 0) * .pi / 180)
        ctx.fill(plane.applying(t), with: .color(seen.aircraft.onGround ? .secondary : .primary))
        if let cs = seen.aircraft.callsign {
          ctx.draw(Text(cs).font(.system(size: 10)).foregroundColor(.secondary), at: CGPoint(x: p.x + 8, y: p.y - 6), anchor: .leading)
        }
      }
    }
    .aspectRatio(1, contentMode: .fit)
    .accessibilityLabel("Map of \(nearby.count) planes around you, north up")
  }
}
