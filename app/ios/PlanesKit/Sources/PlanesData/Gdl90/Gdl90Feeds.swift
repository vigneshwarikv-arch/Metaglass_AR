import Foundation
import Network

/// A data connector that delivers raw GDL 90 datagrams: a Wi-Fi ADS-B receiver, or the simulator.
public protocol Gdl90Feed: Sendable {
  var name: String { get }
  /// Datagrams as they arrive. Iterating starts listening; cancelling the task stops it.
  func datagrams() -> AsyncStream<[UInt8]>
}

/// Listens for GDL 90 on UDP (default port 4000), as sent by Stratux and Stratus 4 over their Wi-Fi.
/// Same notes as Android's Gdl90UdpReceiver:
///  - The port is opened with local-endpoint reuse so another app can bind it too.
///  - Stratux sends UDP unicast to each Wi-Fi client, so ForeFlight on an iPad and this app on an
///    iPhone each get a copy. Two apps on the SAME iPhone: a unicast datagram reaches only one of them.
///  - iOS asks the user for Local Network access the first time (NSLocalNetworkUsageDescription).
///  - Receiving BROADCAST datagrams on iOS needs Apple's multicast entitlement, which we don't have;
///    Stratux uses unicast. TODO(pilot): check Stratus 4 (may broadcast) on a real unit.
public struct Gdl90UdpReceiver: Gdl90Feed {
  public let port: UInt16
  public var name: String { "ADS-B receiver (Wi-Fi, UDP \(port))" }

  public init(port: UInt16 = Gdl90.defaultPort) { self.port = port }

  public func datagrams() -> AsyncStream<[UInt8]> {
    let port = self.port
    return AsyncStream { continuation in
      let queue = DispatchQueue(label: "gdl90.udp")
      let params = NWParameters.udp
      params.allowLocalEndpointReuse = true
      guard let nwPort = NWEndpoint.Port(rawValue: port), let listener = try? NWListener(using: params, on: nwPort) else {
        continuation.finish()
        return
      }
      let connections = ConnectionList()
      // A UDP "connection" here is just one sender (the receiver's IP); read datagrams until it ends.
      listener.newConnectionHandler = { conn in
        connections.add(conn)
        conn.start(queue: queue)
        Self.receive(conn, into: continuation)
      }
      listener.stateUpdateHandler = { state in
        if case .failed = state { continuation.finish() }
      }
      listener.start(queue: queue)
      continuation.onTermination = { _ in
        queue.async {
          listener.cancel()
          connections.cancelAll()
        }
      }
    }
  }

  private static func receive(_ conn: NWConnection, into continuation: AsyncStream<[UInt8]>.Continuation) {
    conn.receiveMessage { data, _, _, error in
      if let data, !data.isEmpty { continuation.yield([UInt8](data)) }
      if error == nil { receive(conn, into: continuation) }
    }
  }
}

/// Connections opened by the listener; only touched on the listener's queue.
private final class ConnectionList: @unchecked Sendable {
  private var list: [NWConnection] = []
  func add(_ c: NWConnection) { list.append(c) }
  func cancelAll() {
    list.forEach { $0.cancel() }
    list.removeAll()
  }
}

/// A pretend receiver that plays a `Gdl90Scenario` once a second — the same bytes a Stratux would send,
/// through the same decoder. For testing anywhere (Settings → Simulated receiver).
public struct SimulatedReceiver: Gdl90Feed {
  public let scenario: Gdl90Scenario
  public var name: String { "Simulated receiver — \(scenario.name)" }

  public init(scenario: Gdl90Scenario) { self.scenario = scenario }

  public func datagrams() -> AsyncStream<[UInt8]> {
    let scenario = self.scenario
    return AsyncStream { continuation in
      let task = Task {
        let start = Date()
        while !Task.isCancelled {
          let now = Date()
          let utc = Int(now.timeIntervalSince1970) % 86_400
          continuation.yield(scenario.frames(now.timeIntervalSince(start), utcSec: utc))
          try? await Task.sleep(nanoseconds: 1_000_000_000)
        }
        continuation.finish()
      }
      continuation.onTermination = { _ in task.cancel() }
    }
  }
}
