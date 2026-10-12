import Foundation

/// Three components in a sensor's own axes.
public struct Vec3: Equatable, Sendable {
  public var x: Double, y: Double, z: Double

  public init(_ x: Double, _ y: Double, _ z: Double) {
    self.x = x
    self.y = y
    self.z = z
  }

  public func dot(_ o: Vec3) -> Double { x * o.x + y * o.y + z * o.z }
  public func cross(_ o: Vec3) -> Vec3 { Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x) }
  public var length: Double { sqrt(dot(self)) }

  public var normalized: Vec3? {
    let l = length
    return l > 1e-9 ? Vec3(x / l, y / l, z / l) : nil
  }

  /// This vector with its component along unit vector `axis` removed.
  public func perpendicular(to axis: Vec3) -> Vec3? {
    let along = dot(axis)
    let v = Vec3(x - along * axis.x, y - along * axis.y, z - along * axis.z)
    return v.length > 1e-9 ? v : nil
  }
}

/// Turns one IMU reading (gravity reaction + magnetic field) into where a device is looking.
///
/// Glasses axes (default): +X up, +Y lateral, +Z forward, as measured by Meta's iOS BirdSpotter
/// sample on a worn pair. The phone uses the same maths with forward = −Z (the back camera).
/// TODO(glasses): confirm on our pair — look level → ~0°, look up → positive.
public struct HeadAim: Sendable {
  public let forward: Vec3

  public init(forward: Vec3 = Vec3(0, 0, 1)) { self.forward = forward }

  /// Degrees above the horizon, or nil in free fall. `up` is the accelerometer reading (reaction to gravity).
  public func elevationDeg(up acceleration: Vec3) -> Double? {
    guard let up = acceleration.normalized else { return nil }
    return asin(min(max(forward.dot(up), -1), 1)) * 180 / .pi
  }

  /// Degrees clockwise from magnetic north, or nil when undefined (e.g. looking straight up).
  public func magneticBearingDeg(up acceleration: Vec3, field: Vec3) -> Double? {
    guard let up = acceleration.normalized,
      let north = field.perpendicular(to: up)?.normalized,
      let aim = forward.perpendicular(to: up)?.normalized
    else { return nil }
    let east = north.cross(up)
    return Geo.normalizeDeg(atan2(aim.dot(east), aim.dot(north)) * 180 / .pi)
  }

  /// Direction relative to TRUE north; `declinationDeg` is east-positive.
  public func pointing(up: Vec3, field: Vec3, declinationDeg: Double) -> Pointing? {
    guard let e = elevationDeg(up: up), let b = magneticBearingDeg(up: up, field: field) else { return nil }
    return Pointing(bearingDeg: Geo.normalizeDeg(b + declinationDeg), elevationDeg: e)
  }
}
