import Foundation

/*
 * GDL 90 decoding and encoding (port of Android data/gdl90/Gdl90.kt), verified against:
 *  - Garmin "GDL 90 Data Interface Specification", 560-1058-00 Rev A (June 5, 2007), cited as "GDL90 §x.y".
 *    Public copy: https://www.faa.gov/sites/faa.gov/files/air_traffic/technology/adsb/archival/GDL90_Public_ICD_RevA.PDF
 *  - ForeFlight "GDL 90 Extended Specification" (ID message 0x65): https://www.foreflight.com/connect/spec/
 * Used by Stratux and Stratus 4 over Wi-Fi UDP, port 4000.
 */

public enum Gdl90 {
  public static let defaultPort: UInt16 = 4000
  public static let flag: UInt8 = 0x7E
  public static let escape: UInt8 = 0x7D

  // Message IDs (GDL90 §3, Table 2; decimal in the spec).
  public static let idHeartbeat: UInt8 = 0
  public static let idOwnship: UInt8 = 10
  public static let idOwnshipGeoAltitude: UInt8 = 11
  public static let idTraffic: UInt8 = 20
  /// ForeFlight extension.
  public static let idForeFlight: UInt8 = 0x65

  /// Latitude/longitude resolution: 180 / 2^23 degrees (GDL90 §3.5.1.3).
  public static let latLonResolution = 180.0 / Double(1 << 23)

  /// GDL90 §2.2.3: CRC-CCITT, polynomial 0x1021, initial value 0, table-driven.
  private static let crcTable: [UInt16] = (0..<256).map { i in
    var crc = UInt16(i) << 8
    for _ in 0..<8 { crc = (crc << 1) ^ ((crc & 0x8000) != 0 ? 0x1021 : 0) }
    return crc
  }

  /// FCS over `bytes` (message ID + data, after removing byte-stuffing).
  public static func crc<C: Collection>(_ bytes: C) -> UInt16 where C.Element == UInt8 {
    var crc: UInt16 = 0
    for b in bytes { crc = crcTable[Int(crc >> 8)] ^ (crc << 8) ^ UInt16(b) }
    return crc
  }

  /// Wraps a clear message (ID + data): appends the FCS LSB first, byte-stuffs 0x7D/0x7E, adds the flags (GDL90 §2.2.1).
  public static func frame(_ message: [UInt8]) -> [UInt8] {
    let c = crc(message)
    let clear = message + [UInt8(c & 0xFF), UInt8(c >> 8)]
    var out: [UInt8] = [flag]
    out.reserveCapacity(clear.count + 8)
    for b in clear {
      if b == flag || b == escape {
        out.append(escape)
        out.append(b ^ 0x20)
      } else {
        out.append(b)
      }
    }
    out.append(flag)
    return out
  }
}

/// Splits a byte stream into GDL 90 messages (GDL90 §2.2.1 "steps for message reception"): bytes between
/// flags, undo byte-stuffing, check the FCS and drop the frame if it doesn't match. Keeps state between
/// calls, so a frame split across UDP datagrams still decodes.
public struct Gdl90Framer: Sendable {
  /// Longest message is Uplink Data, 436 bytes (GDL90 §3.3).
  private static let maxFrame = 600
  private var buffer: [UInt8] = []
  private var inFrame = false
  private var escaping = false
  public private(set) var goodFrames = 0
  public private(set) var badFrames = 0

  public init() {}

  /// Returns the clear messages (message ID + data, without FCS) that completed in `bytes`.
  public mutating func feed<C: Collection>(_ bytes: C) -> [[UInt8]] where C.Element == UInt8 {
    var out: [[UInt8]] = []
    for b in bytes {
      if b == Gdl90.flag {
        if inFrame, !buffer.isEmpty, let m = finish() { out.append(m) }
        buffer.removeAll(keepingCapacity: true)
        escaping = false
        inFrame = true
      } else if !inFrame {
        continue
      } else if escaping {
        buffer.append(b ^ 0x20)
        escaping = false
      } else if b == Gdl90.escape {
        escaping = true
      } else {
        buffer.append(b)
      }
      if buffer.count > Self.maxFrame {  // garbage: wait for the next flag
        buffer.removeAll(keepingCapacity: true)
        inFrame = false
      }
    }
    return out
  }

  private mutating func finish() -> [UInt8]? {
    guard buffer.count >= 3 else {
      badFrames += 1
      return nil
    }
    let n = buffer.count - 2
    let received = UInt16(buffer[n]) | (UInt16(buffer[n + 1]) << 8)  // LSB first
    guard Gdl90.crc(buffer[0..<n]) == received else {
      badFrames += 1
      return nil
    }
    goodFrames += 1
    return Array(buffer[0..<n])
  }
}

/// A decoded GDL 90 message. Unavailable values are nil, never 0.
public enum Gdl90Message: Equatable, Sendable {
  case heartbeat(Heartbeat)
  case report(Gdl90Report)
  case ownshipGeoAltitude(GeoAltitude)
  case foreFlightId(ForeFlightId)
  /// Any other message ID (uplink weather, Stratux status, AHRS…): recognised and ignored.
  case other(id: UInt8, length: Int)

  /// GDL90 §3.1.
  public struct Heartbeat: Equatable, Sendable {
    public var gpsPositionValid: Bool
    public var maintenanceRequired: Bool
    public var gpsBatteryLow: Bool
    public var uatInitialized: Bool
    public var utcOk: Bool
    /// Seconds since 0000Z (17 bits).
    public var timestampSec: Int
    public var uplinkCount: Int
    public var basicLongCount: Int
  }

  /// GDL90 §3.8: height above the WGS-84 ellipsoid unless the ForeFlight ID message says MSL.
  public struct GeoAltitude: Equatable, Sendable {
    public var altitudeFt: Int
    public var verticalWarning: Bool
    /// nil when not available (0x7FFF).
    public var vfomM: Int?
  }

  /// ForeFlight ID message (0x65, sub-ID 0).
  public struct ForeFlightId: Equatable, Sendable {
    public var version: Int
    public var serial: String?
    public var name: String
    public var longName: String
    /// Capabilities bit 0: geometric altitude datum is MSL (else WGS-84 ellipsoid).
    public var geoAltitudeIsMsl: Bool
  }
}

/// Ownship (ID 10, GDL90 §3.4) and Traffic (ID 20, §3.5) reports share one layout (§3.5.1).
public struct Gdl90Report: Equatable, Sendable {
  public var isOwnship: Bool
  public var trafficAlert: Bool
  /// 0 ADS-B ICAO, 1 ADS-B self-assigned, 2 TIS-B ICAO, 3 TIS-B track file, 4 surface vehicle, 5 ground beacon.
  public var addressType: Int
  public var address: Int
  /// nil when there is no valid position (latitude, longitude and NIC all zero, §3.5.1.3).
  public var latDeg: Double?
  public var lonDeg: Double?
  /// Pressure altitude (29.92 inHg); nil when 0xFFF (§3.5.1.4).
  public var pressureAltitudeFt: Int?
  public var airborne: Bool
  public var extrapolated: Bool
  /// 0 not valid, 1 true track, 2 magnetic heading, 3 true heading (§3.5.1.5).
  public var trackType: Int
  public var nic: Int
  public var nacp: Int
  public var horizontalVelocityKt: Int?
  public var verticalVelocityFpm: Int?
  public var trackDeg: Double?
  public var emitterCategory: Int
  public var callsign: String?
  public var emergencyCode: Int

  /// "ab4549"; non-ICAO addresses get a prefix so they can never match an ICAO address.
  public var id: String {
    let hex = String(address, radix: 16)
    let padded = String(repeating: "0", count: max(0, 6 - hex.count)) + hex
    return addressType == 0 || addressType == 2 ? padded : "t\(addressType)-\(padded)"
  }
}

public enum Gdl90Decoder {
  public static let heartbeatLength = 7
  public static let reportLength = 28
  public static let geoAltitudeLength = 5
  public static let foreFlightIdLength = 39

  /// Decodes one clear message (from `Gdl90Framer`). nil for a malformed known message.
  public static func decode(_ m: [UInt8]) -> Gdl90Message? {
    guard let id = m.first else { return nil }
    switch id {
    case Gdl90.idHeartbeat:
      return m.count >= heartbeatLength ? .heartbeat(heartbeat(m)) : nil
    case Gdl90.idOwnship, Gdl90.idTraffic:
      return m.count >= reportLength ? .report(report(m, ownship: id == Gdl90.idOwnship)) : nil
    case Gdl90.idOwnshipGeoAltitude:
      return m.count >= geoAltitudeLength ? .ownshipGeoAltitude(geoAltitude(m)) : nil
    case Gdl90.idForeFlight:
      guard m.count >= 2, m[1] == 0 else { return .other(id: id, length: m.count) }
      return m.count >= foreFlightIdLength ? .foreFlightId(foreFlightId(m)) : nil
    default:
      return .other(id: id, length: m.count)
    }
  }

  /// GDL90 §3.1, Table 3.
  private static func heartbeat(_ m: [UInt8]) -> Gdl90Message.Heartbeat {
    let s1 = Int(m[1]), s2 = Int(m[2])
    // Bit 16 in status byte 2 bit 7; bits 15-0 LS byte first (§3.1.3).
    let ts = ((s2 & 0x80) << 9) | (Int(m[4]) << 8) | Int(m[3])
    let c1 = Int(m[5]), c2 = Int(m[6])
    return Gdl90Message.Heartbeat(
      gpsPositionValid: s1 & 0x80 != 0, maintenanceRequired: s1 & 0x40 != 0, gpsBatteryLow: s1 & 0x08 != 0,
      uatInitialized: s1 & 0x01 != 0, utcOk: s2 & 0x01 != 0, timestampSec: ts, uplinkCount: c1 >> 3,
      basicLongCount: ((c1 & 0x03) << 8) | c2)
  }

  /// GDL90 §3.5.1, Figure 2: st aa aa aa ll ll ll nn nn nn dd dm ia hh hv vv tt ee cc×8 px.
  private static func report(_ m: [UInt8], ownship: Bool) -> Gdl90Report {
    let b = m.map(Int.init)
    let latRaw = s24(b, 5), lonRaw = s24(b, 8)
    let altRaw = (b[11] << 4) | (b[12] >> 4)
    let misc = b[12] & 0x0F
    let nic = b[13] >> 4
    let hvel = (b[14] << 4) | (b[15] >> 4)
    let vvelRaw = ((b[15] & 0x0F) << 8) | b[16]
    let trackType = misc & 0x03
    let noPosition = latRaw == 0 && lonRaw == 0 && nic == 0  // §3.4, §3.5.1.3
    let cs = String(decoding: m[19..<27], as: UTF8.self).trimmingCharacters(in: .whitespaces)
    return Gdl90Report(
      isOwnship: ownship, trafficAlert: (b[1] >> 4) == 1, addressType: b[1] & 0x0F, address: (b[2] << 16) | (b[3] << 8) | b[4],
      latDeg: noPosition ? nil : Double(latRaw) * Gdl90.latLonResolution,
      lonDeg: noPosition ? nil : Double(lonRaw) * Gdl90.latLonResolution,
      // §3.5.1.4: 25 ft resolution, offset −1,000 ft; 0xFFF = invalid.
      pressureAltitudeFt: altRaw == 0xFFF ? nil : altRaw * 25 - 1_000,
      airborne: misc & 0x08 != 0, extrapolated: misc & 0x04 != 0, trackType: trackType, nic: nic, nacp: b[13] & 0x0F,
      // §3.5.1.7: 1 kt; 0xFFF = no horizontal velocity.
      horizontalVelocityKt: hvel == 0xFFF ? nil : hvel,
      verticalVelocityFpm: verticalVelocity(vvelRaw),
      // §3.5.1.9: 360/256°; meaningless when the misc field says "tt not valid".
      trackDeg: trackType == 0 ? nil : Double(b[17]) * 360 / 256,
      emitterCategory: b[18], callsign: cs.isEmpty ? nil : cs, emergencyCode: b[27] >> 4)
  }

  /// §3.5.1.8: 12-bit signed, 64 fpm units; 0x800 = unavailable; 0x1FF...0x7FF and 0x801...0xE01 unused.
  private static func verticalVelocity(_ raw: Int) -> Int? {
    if raw == 0x800 || (0x1FF...0x7FF).contains(raw) || (0x801...0xE01).contains(raw) { return nil }
    return (raw & 0x800 != 0 ? raw - 0x1000 : raw) * 64
  }

  /// GDL90 §3.8, Table 16.
  private static func geoAltitude(_ m: [UInt8]) -> Gdl90Message.GeoAltitude {
    let alt = Int(Int16(bitPattern: UInt16(m[1]) << 8 | UInt16(m[2]))) * 5
    let metrics = Int(m[3]) << 8 | Int(m[4])
    let vfom = metrics & 0x7FFF
    return Gdl90Message.GeoAltitude(altitudeFt: alt, verticalWarning: metrics & 0x8000 != 0, vfomM: vfom == 0x7FFF ? nil : vfom)
  }

  /// ForeFlight extended spec: ID message, big-endian.
  private static func foreFlightId(_ m: [UInt8]) -> Gdl90Message.ForeFlightId {
    let serialBytes = m[3..<11]
    let serial = serialBytes.allSatisfy { $0 == 0xFF } ? nil : serialBytes.map { String(format: "%02x", $0) }.joined()
    let caps = Int(m[35]) << 24 | Int(m[36]) << 16 | Int(m[37]) << 8 | Int(m[38])
    return Gdl90Message.ForeFlightId(
      version: Int(m[2]), serial: serial, name: text(m[11..<19]), longName: text(m[19..<35]), geoAltitudeIsMsl: caps & 0x01 != 0)
  }

  private static func text(_ s: ArraySlice<UInt8>) -> String {
    String(decoding: s, as: UTF8.self).trimmingCharacters(in: CharacterSet(charactersIn: "\u{0} "))
  }

  private static func s24(_ b: [Int], _ i: Int) -> Int {
    let v = (b[i] << 16) | (b[i + 1] << 8) | b[i + 2]
    return v & 0x800000 != 0 ? v - 0x1000000 : v
  }
}

/// Builds GDL 90 messages (clear, unframed). Used by the simulated receiver and tests; mirror of `Gdl90Decoder`.
public enum Gdl90Encoder {
  public static func heartbeat(gpsValid: Bool, timestampSec: Int, utcOk: Bool = true) -> [UInt8] {
    let s1 = (gpsValid ? 0x80 : 0) | 0x01  // UAT initialized is always set (§3.1.1 h)
    let s2 = (((timestampSec >> 16) & 0x01) << 7) | (utcOk ? 0x01 : 0)
    return [Gdl90.idHeartbeat, UInt8(s1), UInt8(s2), UInt8(timestampSec & 0xFF), UInt8((timestampSec >> 8) & 0xFF), 0, 0]
  }

  public static func report(
    ownship: Bool, address: Int, latDeg: Double?, lonDeg: Double?, pressureAltitudeFt: Double?, airborne: Bool = true,
    trackDeg: Double? = nil, horizontalVelocityKt: Double? = nil, verticalVelocityFpm: Double? = nil, callsign: String = "",
    addressType: Int = 0, nic: Int = 9, nacp: Int = 9, emitterCategory: Int = 1, trafficAlert: Bool = false
  ) -> [UInt8] {
    var lat = 0, lon = 0
    let hasPos = latDeg != nil && lonDeg != nil
    if let la = latDeg, let lo = lonDeg {
      lat = roundHalfUp(la / Gdl90.latLonResolution) & 0xFFFFFF
      lon = roundHalfUp(lo / Gdl90.latLonResolution) & 0xFFFFFF
    }
    let alt = pressureAltitudeFt.map { min(max(roundHalfUp(($0 + 1_000) / 25), 0), 0xFFE) } ?? 0xFFF
    let trackType = trackDeg != nil ? 1 : 0
    let misc = (airborne ? 0x08 : 0) | trackType
    let hvel = horizontalVelocityKt.map { min(max(roundHalfUp($0), 0), 0xFFE) } ?? 0xFFF
    let vvel = verticalVelocityFpm.map { min(max(roundHalfUp($0 / 64), -0x1FE), 0x1FE) & 0xFFF } ?? 0x800
    let tt = trackDeg.map { roundHalfUp($0 * 256 / 360) & 0xFF } ?? 0
    let cs = Array((callsign.uppercased() + String(repeating: " ", count: 8)).utf8.prefix(8))
    var m = [UInt8](repeating: 0, count: Gdl90Decoder.reportLength)
    m[0] = ownship ? Gdl90.idOwnship : Gdl90.idTraffic
    m[1] = UInt8(((trafficAlert ? 1 : 0) << 4) | (addressType & 0x0F))
    put24(&m, 2, address)
    put24(&m, 5, lat)
    put24(&m, 8, lon)
    m[11] = UInt8(alt >> 4)
    m[12] = UInt8(((alt & 0x0F) << 4) | misc)
    m[13] = UInt8(((hasPos ? nic : 0) << 4) | nacp)
    m[14] = UInt8(hvel >> 4)
    m[15] = UInt8(((hvel & 0x0F) << 4) | (vvel >> 8))
    m[16] = UInt8(vvel & 0xFF)
    m[17] = UInt8(tt)
    m[18] = UInt8(emitterCategory)
    m.replaceSubrange(19..<27, with: cs)
    m[27] = 0
    return m
  }

  public static func geoAltitude(_ altitudeFt: Double, vfomM: Int? = 10) -> [UInt8] {
    let alt = UInt16(bitPattern: Int16(min(max(roundHalfUp(altitudeFt / 5), Int(Int16.min)), Int(Int16.max))))
    let metrics = vfomM.map { min(max($0, 0), 0x7FFE) } ?? 0x7FFF
    return [Gdl90.idOwnshipGeoAltitude, UInt8(alt >> 8), UInt8(alt & 0xFF), UInt8(metrics >> 8), UInt8(metrics & 0xFF)]
  }

  public static func foreFlightId(name: String, longName: String, geoAltitudeIsMsl: Bool) -> [UInt8] {
    var m = [UInt8](repeating: 0, count: Gdl90Decoder.foreFlightIdLength)
    m[0] = Gdl90.idForeFlight
    m[1] = 0
    m[2] = 1
    for i in 3...10 { m[i] = 0xFF }  // serial: invalid
    for (i, c) in name.utf8.prefix(8).enumerated() { m[11 + i] = c }
    for (i, c) in longName.utf8.prefix(16).enumerated() { m[19 + i] = c }
    m[38] = geoAltitudeIsMsl ? 1 : 0
    return m
  }

  private static func put24(_ m: inout [UInt8], _ i: Int, _ v: Int) {
    m[i] = UInt8((v >> 16) & 0xFF)
    m[i + 1] = UInt8((v >> 8) & 0xFF)
    m[i + 2] = UInt8(v & 0xFF)
  }

  /// Rounds half up, like Kotlin's roundToInt, so both apps produce identical bytes.
  private static func roundHalfUp(_ x: Double) -> Int { Int((x + 0.5).rounded(.down)) }
}
