import Foundation

/// An aircraft together with where it appears from the user's position.
public struct SeenAircraft: Equatable, Sendable, Identifiable {
  public var aircraft: Aircraft
  public var look: LookAngle
  public var id: String { aircraft.icao24 }

  public init(aircraft: Aircraft, look: LookAngle) {
    self.aircraft = aircraft
    self.look = look
  }
}

public enum TurnDirection: Equatable, Sendable { case left, right, up, down }

/// Result of "which plane am I pointing at?".
public enum Selection: Equatable, Sendable {
  case hit(SeenAircraft, offsetDeg: Double)
  case miss(nearest: SeenAircraft, turn: TurnDirection, turnDeg: Double)
  case noAircraft
}

public enum TargetSelector {
  /// Half-width of the "pointing cone". Phone compasses are often off by 5–10°.
  public static let defaultToleranceDeg = 12.0
  /// Planes this far below the horizon are hidden by terrain and buildings.
  public static let minElevationDeg = -1.0

  /// Every aircraft within `radiusKm` of `user`, nearest first.
  public static func nearby(user: GeoPoint, aircraft: [Aircraft], radiusKm: Double) -> [SeenAircraft] {
    aircraft
      .map { SeenAircraft(aircraft: $0, look: Geo.lookAngle(observer: user, target: $0.position3d)) }
      .filter { $0.look.groundDistanceKm <= radiusKm }
      .sorted { $0.look.groundDistanceKm < $1.look.groundDistanceKm }
  }

  /// Picks the airborne aircraft closest to `pointing`; ties within a degree go to the nearer one.
  public static func select(
    user: GeoPoint, aircraft: [Aircraft], pointing: Pointing, toleranceDeg: Double = defaultToleranceDeg
  ) -> Selection {
    let candidates =
      aircraft
      .filter { !$0.onGround && $0.altitudeM != nil }
      .map { SeenAircraft(aircraft: $0, look: Geo.lookAngle(observer: user, target: $0.position3d)) }
      .filter { $0.look.elevationDeg >= minElevationDeg }
    guard !candidates.isEmpty else { return .noAircraft }

    let scored =
      candidates
      .map { ($0, Geo.angularSeparationDeg(pointing, Pointing(bearingDeg: $0.look.bearingDeg, elevationDeg: $0.look.elevationDeg))) }
      .sorted { a, b in
        let ra = a.1.rounded(), rb = b.1.rounded()
        return ra != rb ? ra < rb : a.0.look.groundDistanceKm < b.0.look.groundDistanceKm
      }
    let (best, offset) = scored[0]
    if offset <= toleranceDeg { return .hit(best, offsetDeg: offset) }

    let dBearing = Geo.signedBearingDiff(from: pointing.bearingDeg, to: best.look.bearingDeg)
    let dElevation = best.look.elevationDeg - pointing.elevationDeg
    if abs(dBearing) >= abs(dElevation) {
      return .miss(nearest: best, turn: dBearing >= 0 ? .right : .left, turnDeg: abs(dBearing))
    }
    return .miss(nearest: best, turn: dElevation >= 0 ? .up : .down, turnDeg: abs(dElevation))
  }
}
