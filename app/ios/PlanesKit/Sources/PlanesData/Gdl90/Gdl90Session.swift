import Foundation
import PlanesCore

/// Receiver connection, as shown on the pilot screen.
public enum ReceiverLink: Sendable {
  /// Nothing received yet, or nothing for `Gdl90Session.linkTimeoutMs`.
  case noData
  /// Heartbeats arriving.
  case connected
}

/// Everything pilot mode needs from the receiver, at one moment (port of Android ReceiverSnapshot).
public struct ReceiverSnapshot: Equatable, Sendable {
  public var link: ReceiverLink
  /// Receiver's own GPS has a valid fix and a recent ownship position.
  public var gpsValid: Bool
  public var ownship: Ownship?
  /// Traffic in the common model; altitudeM is PRESSURE altitude, as GDL 90 reports it.
  public var traffic: [Aircraft]
  /// From the ForeFlight ID message (Stratux sends "Stratux"), if any.
  public var deviceName: String?
  public var goodFrames: Int
  public var badFrames: Int

  public static let empty = ReceiverSnapshot(link: .noData, gpsValid: false, ownship: nil, traffic: [], deviceName: nil, goodFrames: 0, badFrames: 0)
}

/// Turns raw GDL 90 datagrams into a `ReceiverSnapshot`. Pure and single-threaded: feed it from one place
/// (the UDP receiver or the simulator) and read `snapshot()` about once a second.
public struct Gdl90Session: Sendable {
  public static let linkTimeoutMs: Int64 = 3_000
  /// Traffic not updated for this long is dropped (the GDL 90 itself extrapolates briefly).
  public static let trafficTimeoutMs: Int64 = 10_000

  private let clockMs: @Sendable () -> Int64
  private var framer = Gdl90Framer()
  private var lastMessageMs: Int64?
  private var lastHeartbeat: (Gdl90Message.Heartbeat, Int64)?
  private var lastOwnship: (Gdl90Report, Int64)?
  private var lastGeoAltitude: (Gdl90Message.GeoAltitude, Int64)?
  private var foreFlightId: Gdl90Message.ForeFlightId?
  private var traffic: [String: (Gdl90Report, Int64)] = [:]
  public private(set) var unknownMessages = 0

  public init(clockMs: @escaping @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }) {
    self.clockMs = clockMs
  }

  public mutating func reset() {
    lastMessageMs = nil
    lastHeartbeat = nil
    lastOwnship = nil
    lastGeoAltitude = nil
    foreFlightId = nil
    traffic = [:]
  }

  /// Feeds one UDP datagram (any number of frames).
  public mutating func ingest<C: Collection>(_ datagram: C) where C.Element == UInt8 {
    let now = clockMs()
    for m in framer.feed(datagram) {
      guard let msg = Gdl90Decoder.decode(m) else { continue }
      lastMessageMs = now
      switch msg {
      case let .heartbeat(h): lastHeartbeat = (h, now)
      case let .report(r):
        if r.isOwnship { lastOwnship = (r, now) } else { traffic[r.id] = (r, now) }
      case let .ownshipGeoAltitude(g): lastGeoAltitude = (g, now)
      case let .foreFlightId(id): foreFlightId = id
      case .other: unknownMessages += 1
      }
    }
  }

  public mutating func snapshot() -> ReceiverSnapshot {
    let now = clockMs()
    traffic = traffic.filter { now - $0.value.1 <= Self.trafficTimeoutMs }
    let link: ReceiverLink = lastMessageMs.map { now - $0 <= Self.linkTimeoutMs } == true ? .connected : .noData
    let own = ownship(now)
    let heartbeatGps = lastHeartbeat.map { now - $0.1 <= Self.linkTimeoutMs && $0.0.gpsPositionValid } ?? false
    let name = foreFlightId?.name.trimmingCharacters(in: .whitespaces)
    return ReceiverSnapshot(
      link: link, gpsValid: heartbeatGps && own != nil, ownship: own,
      traffic: traffic.values.sorted { $0.0.id < $1.0.id }.compactMap { $0.0.toAircraft(receivedAtMs: $0.1) },
      deviceName: (name?.isEmpty ?? true) ? nil : name, goodFrames: framer.goodFrames, badFrames: framer.badFrames)
  }

  private func ownship(_ now: Int64) -> Ownship? {
    guard let last = lastOwnship, now - last.1 <= Self.linkTimeoutMs, let lat = last.0.latDeg, let lon = last.0.lonDeg else { return nil }
    let (r, at) = last
    let geo = lastGeoAltitude.flatMap { now - $0.1 <= Self.linkTimeoutMs ? $0.0 : nil }
    let gs = r.horizontalVelocityKt.map(Double.init)
    // Only a TRUE track or heading is usable as our track; magnetic heading isn't.
    let trueDirection = (r.trackType == 1 || r.trackType == 3) ? r.trackDeg : nil
    return Ownship(
      position: GeoPoint(lat: lat, lon: lon), groundspeedKt: gs,
      trackDeg: (gs ?? 0) >= TrackEstimator.minTrackSpeedKt ? trueDirection : nil,
      gpsAltitudeFt: geo.map { Double($0.altitudeFt) },
      gpsAltitudeDatum: foreFlightId?.geoAltitudeIsMsl == true ? .msl : .ellipsoid,
      // Stratux fills this with GPS MSL altitude when it has no pressure sensor; still the best reference
      // to compare with traffic pressure altitude. Never displayed as an altimeter.
      pressureAltitudeFt: r.pressureAltitudeFt.map(Double.init), verticalRateFpm: r.verticalVelocityFpm.map(Double.init),
      icaoAddress: r.id, nic: r.nic, nacp: r.nacp, source: .receiver, timeMs: at)
  }
}

extension Gdl90Report {
  /// Traffic report → common Aircraft model. nil without a valid position (unknown, not 0,0).
  public func toAircraft(receivedAtMs: Int64) -> Aircraft? {
    guard let lat = latDeg, let lon = lonDeg else { return nil }
    return Aircraft(
      icao24: id, callsign: callsign, position: GeoPoint(lat: lat, lon: lon),
      altitudeM: pressureAltitudeFt.map { NavUnits.feetToMetres(Double($0)) },
      speedMs: horizontalVelocityKt.map { NavUnits.ms(Double($0)) }, trackDeg: trackDeg,
      verticalRateMs: verticalVelocityFpm.map { Double($0) / NavUnits.fpmPerMs }, onGround: !airborne,
      lastSeenEpochSec: Int(receivedAtMs / 1000))
  }
}
