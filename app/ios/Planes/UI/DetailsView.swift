import PlanesCore
import SwiftUI

struct DetailsView: View {
  @EnvironmentObject var model: AppModel
  let icao24: String

  var body: some View {
    Group {
      if let seen = model.aircraft(icao24) {
        details(seen)
      } else {
        EmptyStateView(title: "This plane is out of range", message: "It has flown out of the area or stopped broadcasting.")
      }
    }
    .navigationTitle("Plane details")
    .navigationBarTitleDisplayMode(.inline)
  }

  private func details(_ seen: SeenAircraft) -> some View {
    let a = seen.aircraft
    let unit = model.settings.unit
    let climb: String? = a.verticalRateMs.map { vr in
      let fpm = Int((vr * 196.85).rounded())
      return fpm > 200 ? "Climbing \(Units.groupThousands(fpm)) ft/min" : fpm < -200 ? "Descending \(Units.groupThousands(-fpm)) ft/min" : "Level"
    }
    return List {
      Section {
        Text(a.titleText).font(.title2.bold())
        HStack {
          airport(a.info.route?.origin, label: "From", alignment: .leading)
          Spacer()
          Image(systemName: "arrow.right").foregroundStyle(Color.sky)
          Spacer()
          airport(a.info.route?.destination, label: "To", alignment: .trailing)
        }
      }
      Section {
        fact("Flight / callsign", a.callsign)
        fact("Airline", a.info.airline)
        fact("Aircraft type", a.info.typeName ?? a.info.typeCode)
        fact("Registration", a.info.registration)
        fact("Altitude", a.onGround ? "On the ground" : Units.altitudeText(a.altitudeM))
        fact("Speed", Units.speedText(a.speedMs, unit))
        fact("Heading", a.trackDeg.map { "\(Int($0.rounded()))° \(CompassPoint.of($0).short)" })
        fact("Climb / descent", climb)
        fact(
          "From you",
          "\(Units.distanceText(seen.look.groundDistanceKm, unit)) \(CompassPoint.of(seen.look.bearingDeg).spoken), \(Int(seen.look.elevationDeg.rounded()))° up")
        fact("Transponder", a.icao24.uppercased())
      }
      Section {
        Text("Routes come from community data and can occasionally be out of date.").font(.footnote).foregroundStyle(.secondary)
        AttributionView(lines: model.attributions)
      }
    }
  }

  private func airport(_ ap: Airport?, label: String, alignment: HorizontalAlignment) -> some View {
    VStack(alignment: alignment) {
      Text(label).font(.caption).foregroundStyle(.secondary)
      Text(ap?.code ?? "Unknown").font(.title2.bold())
      Text(ap?.city ?? ap?.name ?? "").font(.caption)
    }
  }

  private func fact(_ label: String, _ value: String?) -> some View {
    HStack {
      Text(label).foregroundStyle(.secondary)
      Spacer()
      Text(value ?? "Unknown").fontWeight(value == nil ? .regular : .semibold).foregroundStyle(value == nil ? .secondary : .primary)
    }
  }
}
