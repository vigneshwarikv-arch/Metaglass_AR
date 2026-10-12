import PlanesCore
import SwiftUI

private let halfFovDeg = 35.0

struct PointView: View {
  @EnvironmentObject var model: AppModel

  var body: some View {
    Group {
      if model.location.point == nil {
        EmptyStateView(
          title: "Location needed", message: "Point to identify works out which plane is in front of you from where you are.",
          primary: ("Allow location", model.requestLocation))
      } else if !model.compassAvailable {
        EmptyStateView(title: "No compass", message: "Point to identify needs a compass. You can still use the Nearby list.")
      } else {
        PointContent(point: model.point)
      }
    }
    .navigationTitle("Point")
    .navigationDestination(for: String.self) { DetailsView(icao24: $0) }
  }
}

private struct PointContent: View {
  @EnvironmentObject var model: AppModel
  @ObservedObject var point: PointState

  var body: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: 12) {
        Text("Hold your iPhone up and point the back of it at a plane.").font(.subheadline).foregroundStyle(.secondary)
        SkyView(nearby: model.nearby, pointing: point.pointing, selection: point.selection)
        if let p = point.pointing {
          Text("Facing \(Int(p.bearingDeg.rounded()))° \(CompassPoint.of(p.bearingDeg).short) · \(Int(abs(p.elevationDeg).rounded()))° \(p.elevationDeg >= 0 ? "up" : "down")")
            .font(.headline)
        } else {
          Text("Reading compass…").font(.headline)
        }
        if point.compassUnreliable {
          Text("Compass needs calibrating: move your iPhone in a figure-of-eight a few times.").font(.footnote).foregroundStyle(Color.beacon)
        }
        result
        Button {
          model.ask()
        } label: {
          Text(model.glasses.status.isConnected ? "Ask: answer on my glasses" : "Ask: say the answer").frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        AttributionView(lines: model.attributions)
      }
      .padding()
    }
  }

  @ViewBuilder private var result: some View {
    switch point.selection {
    case let .hit(seen, _):
      ResultCard(lead: "That's", seen: seen, unit: model.settings.unit)
    case let .miss(seen, turn, deg):
      let d = Int(deg.rounded())
      let text = [TurnDirection.left: "Turn left about \(d)°", .right: "Turn right about \(d)°", .up: "Tilt up about \(d)°", .down: "Tilt down about \(d)°"][turn]!
      ResultCard(lead: "No plane that way. \(text) for the nearest:", seen: seen, unit: model.settings.unit)
    case .noAircraft:
      Text("No planes in the sky near you right now.").foregroundStyle(.secondary)
    case nil:
      EmptyView()
    }
  }
}

private struct ResultCard: View {
  let lead: String
  let seen: SeenAircraft
  let unit: DistanceUnit

  var body: some View {
    VStack(alignment: .leading, spacing: 4) {
      Text(lead).font(.subheadline).foregroundStyle(.secondary)
      Text(seen.aircraft.titleText).font(.title2.bold())
      Text(seen.aircraft.subtitleText)
      Text(
        [Units.altitudeText(seen.aircraft.altitudeM), "\(Units.distanceText(seen.look.groundDistanceKm, unit)) \(CompassPoint.of(seen.look.bearingDeg).short)"]
          .compactMap { $0 }.joined(separator: " · ")
      ).foregroundStyle(.secondary)
      NavigationLink("Plane details", value: seen.aircraft.icao24).padding(.top, 4)
    }
    .padding()
    .frame(maxWidth: .infinity, alignment: .leading)
    .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))
  }
}

/// What's in front of the iPhone: crosshair = where it points, ring = the ±12° aiming zone.
private struct SkyView: View {
  let nearby: [SeenAircraft]
  let pointing: Pointing?
  let selection: Selection?

  var body: some View {
    Canvas { ctx, size in
      let c = CGPoint(x: size.width / 2, y: size.height / 2)
      let pxPerDeg = size.width / 2 / halfFovDeg
      let ring = TargetSelector.defaultToleranceDeg * pxPerDeg
      let ringRect = CGRect(x: c.x - ring, y: c.y - ring, width: 2 * ring, height: 2 * ring)
      ctx.fill(Path(ellipseIn: ringRect), with: .color(Color.sky.opacity(0.12)))
      ctx.stroke(Path(ellipseIn: ringRect), with: .color(.sky), lineWidth: 1.5)
      var cross = Path()
      cross.move(to: CGPoint(x: c.x - 14, y: c.y))
      cross.addLine(to: CGPoint(x: c.x + 14, y: c.y))
      cross.move(to: CGPoint(x: c.x, y: c.y - 14))
      cross.addLine(to: CGPoint(x: c.x, y: c.y + 14))
      ctx.stroke(cross, with: .color(.sky), lineWidth: 2)
      guard let pointing else { return }
      let horizonY = c.y + pointing.elevationDeg * pxPerDeg
      if horizonY > 0 && horizonY < size.height {
        var h = Path()
        h.move(to: CGPoint(x: 0, y: horizonY))
        h.addLine(to: CGPoint(x: size.width, y: horizonY))
        ctx.stroke(h, with: .color(.gray.opacity(0.4)))
        ctx.draw(Text("horizon").font(.caption2).foregroundColor(.secondary), at: CGPoint(x: 8, y: horizonY + 8), anchor: .leading)
      }
      var highlight: String?
      if case let .hit(s, _) = selection { highlight = s.aircraft.icao24 }
      for seen in nearby where !seen.aircraft.onGround {
        let dx = Geo.signedBearingDiff(from: pointing.bearingDeg, to: seen.look.bearingDeg)
        let dy = seen.look.elevationDeg - pointing.elevationDeg
        guard abs(dx) < halfFovDeg * 1.4, abs(dy) < halfFovDeg * 1.4 else { continue }
        let p = CGPoint(x: c.x + dx * pxPerDeg, y: c.y - dy * pxPerDeg)
        let hit = seen.aircraft.icao24 == highlight
        let rad: CGFloat = hit ? 9 : 6
        ctx.fill(Path(ellipseIn: CGRect(x: p.x - rad, y: p.y - rad, width: 2 * rad, height: 2 * rad)), with: .color(hit ? .beacon : .primary))
        ctx.draw(
          Text(seen.aircraft.callsign ?? seen.aircraft.icao24.uppercased()).font(.caption2.weight(hit ? .bold : .regular))
            .foregroundColor(hit ? .beacon : .secondary), at: CGPoint(x: p.x + 10, y: p.y - 8), anchor: .leading)
      }
    }
    .aspectRatio(1.3, contentMode: .fit)
    .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
    .clipShape(RoundedRectangle(cornerRadius: 16))
    .accessibilityLabel("Sky view in the direction the iPhone points")
  }
}
