import Foundation

/// Where something is as seen by an observer.
public struct LookAngle: Equatable, Sendable {
  /// Degrees clockwise from true north, 0 until 360.
  public var bearingDeg: Double
  /// Degrees above the horizon (negative = below).
  public var elevationDeg: Double
  /// Distance along the ground in kilometres.
  public var groundDistanceKm: Double
}

/// A direction the user is pointing (phone) or looking (glasses).
public struct Pointing: Equatable, Sendable {
  public var bearingDeg: Double
  public var elevationDeg: Double

  public init(bearingDeg: Double, elevationDeg: Double) {
    self.bearingDeg = bearingDeg
    self.elevationDeg = elevationDeg
  }
}

public struct BoundingBox: Equatable, Sendable {
  public var latMin: Double
  public var lonMin: Double
  public var latMax: Double
  public var lonMax: Double
}

/// Spherical-Earth geometry; same maths as the Android Geo.kt.
public enum Geo {
  public static let earthRadiusM = 6_371_000.0

  public static func distanceKm(_ a: GeoPoint, _ b: GeoPoint) -> Double {
    centralAngleRad(a, b) * earthRadiusM / 1000
  }

  public static func bearingDeg(from: GeoPoint, to: GeoPoint) -> Double {
    let lat1 = rad(from.lat), lat2 = rad(to.lat), dLon = rad(to.lon - from.lon)
    let y = sin(dLon) * cos(lat2)
    let x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return normalizeDeg(deg(atan2(y, x)))
  }

  /// Angle above the observer's horizon at which `target` appears, including Earth's curvature.
  public static func elevationDeg(observer: GeoPoint, target: GeoPoint) -> Double {
    let theta = centralAngleRad(observer, target)
    let rObs = earthRadiusM + observer.altitudeM
    let rTgt = earthRadiusM + target.altitudeM
    let up = rTgt * cos(theta) - rObs
    let out = rTgt * sin(theta)
    return deg(atan2(up, out))
  }

  public static func lookAngle(observer: GeoPoint, target: GeoPoint) -> LookAngle {
    LookAngle(
      bearingDeg: bearingDeg(from: observer, to: target),
      elevationDeg: elevationDeg(observer: observer, target: target),
      groundDistanceKm: distanceKm(observer, target))
  }

  /// Angle between two (bearing, elevation) directions, in degrees.
  public static func angularSeparationDeg(_ a: Pointing, _ b: Pointing) -> Double {
    let e1 = rad(a.elevationDeg), e2 = rad(b.elevationDeg), dAz = rad(a.bearingDeg - b.bearingDeg)
    let c = sin(e1) * sin(e2) + cos(e1) * cos(e2) * cos(dAz)
    return deg(acos(min(max(c, -1), 1)))
  }

  /// Signed difference `to − from` in −180...180 (positive = clockwise / to the right).
  public static func signedBearingDiff(from: Double, to: Double) -> Double {
    var d = (to - from).truncatingRemainder(dividingBy: 360)
    if d > 180 { d -= 360 }
    if d <= -180 { d += 360 }
    return d
  }

  /// A point `distanceKm` away from `from` along `bearingDeg` (same altitude).
  public static func destination(from: GeoPoint, bearingDeg: Double, distanceKm: Double) -> GeoPoint {
    let delta = distanceKm * 1000 / earthRadiusM
    let brg = rad(bearingDeg), lat1 = rad(from.lat), lon1 = rad(from.lon)
    let lat2 = asin(sin(lat1) * cos(delta) + cos(lat1) * sin(delta) * cos(brg))
    let lon2 = lon1 + atan2(sin(brg) * sin(delta) * cos(lat1), cos(delta) - sin(lat1) * sin(lat2))
    let lon = (deg(lon2) + 540).truncatingRemainder(dividingBy: 360) - 180
    return GeoPoint(lat: deg(lat2), lon: lon, altitudeM: from.altitudeM)
  }

  public static func boundingBox(center: GeoPoint, radiusKm: Double) -> BoundingBox {
    let dLat = deg(radiusKm * 1000 / earthRadiusM)
    let cosLat = max(cos(rad(center.lat)), 0.01)
    let dLon = min(dLat / cosLat, 180)
    return BoundingBox(
      latMin: max(center.lat - dLat, -90), lonMin: center.lon - dLon,
      latMax: min(center.lat + dLat, 90), lonMax: center.lon + dLon)
  }

  public static func normalizeDeg(_ d: Double) -> Double {
    let r = d.truncatingRemainder(dividingBy: 360)
    return r < 0 ? r + 360 : r
  }

  static func centralAngleRad(_ a: GeoPoint, _ b: GeoPoint) -> Double {
    let dLat = rad(b.lat - a.lat), dLon = rad(b.lon - a.lon)
    let s1 = sin(dLat / 2), s2 = sin(dLon / 2)
    let h = s1 * s1 + cos(rad(a.lat)) * cos(rad(b.lat)) * s2 * s2
    return 2 * atan2(sqrt(h), sqrt(abs(1 - h)))
  }

  static func rad(_ d: Double) -> Double { d * .pi / 180 }
  static func deg(_ r: Double) -> Double { r * 180 / .pi }
}
