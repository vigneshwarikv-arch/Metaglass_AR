import CoreLocation
import CoreMotion
import Foundation
import PlanesCore

/// iPhone GPS plus compass declination (true − magnetic), via CLLocationManager.
@MainActor
final class LocationService: NSObject, ObservableObject, CLLocationManagerDelegate {
  private let manager = CLLocationManager()
  @Published private(set) var authorization: CLAuthorizationStatus
  @Published private(set) var location: GeoPoint?
  /// Magnetic declination in degrees, east positive. 0 until the compass reports.
  private(set) var declinationDeg = 0.0

  override init() {
    authorization = manager.authorizationStatus
    super.init()
    manager.delegate = self
    manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    manager.distanceFilter = 100
  }

  func requestPermission() { manager.requestWhenInUseAuthorization() }

  func start() {
    guard authorization == .authorizedWhenInUse || authorization == .authorizedAlways else { return }
    manager.startUpdatingLocation()
    if CLLocationManager.headingAvailable() { manager.startUpdatingHeading() }
  }

  func stop() {
    manager.stopUpdatingLocation()
    manager.stopUpdatingHeading()
  }

  nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    let status = manager.authorizationStatus
    Task { @MainActor in
      self.authorization = status
      self.start()
    }
  }

  nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    guard let l = locations.last else { return }
    let p = GeoPoint(lat: l.coordinate.latitude, lon: l.coordinate.longitude, altitudeM: l.verticalAccuracy >= 0 ? l.altitude : 0)
    Task { @MainActor in self.location = p }
  }

  nonisolated func locationManager(_ manager: CLLocationManager, didUpdateHeading h: CLHeading) {
    guard h.trueHeading >= 0 else { return }
    let d = Geo.signedBearingDiff(from: h.magneticHeading, to: h.trueHeading)
    Task { @MainActor in self.declinationDeg = d }
  }
}

/// Where the back of the iPhone points, from gravity + the calibrated magnetometer, using the same
/// tilt-compensated maths as the glasses (core HeadAim) with forward = −Z (the back camera).
@MainActor
final class PhoneAim {
  private let motion = CMMotionManager()
  private let aim = HeadAim(forward: Vec3(0, 0, -1))
  private var last: Pointing?

  var available: Bool { motion.isDeviceMotionAvailable }

  /// `onChange` gets (magnetic pointing, compassUnreliable), only when the direction moves ≥ 0.5°.
  func start(onChange: @escaping (Pointing, Bool) -> Void) {
    guard motion.isDeviceMotionAvailable, !motion.isDeviceMotionActive else { return }
    motion.deviceMotionUpdateInterval = 1.0 / 20
    motion.startDeviceMotionUpdates(using: .xMagneticNorthZVertical, to: .main) { [weak self] data, _ in
      guard let self, let data else { return }
      let g = data.gravity
      let f = data.magneticField.field
      let up = Vec3(-g.x, -g.y, -g.z)
      guard let p = self.aim.pointing(up: up, field: Vec3(f.x, f.y, f.z), declinationDeg: 0) else { return }
      if let l = self.last, abs(Geo.signedBearingDiff(from: l.bearingDeg, to: p.bearingDeg)) < 0.5,
        abs(l.elevationDeg - p.elevationDeg) < 0.5
      {
        return
      }
      self.last = p
      onChange(p, data.magneticField.accuracy == .uncalibrated || data.magneticField.accuracy == .low)
    }
  }

  func stop() {
    motion.stopDeviceMotionUpdates()
    last = nil
  }
}
