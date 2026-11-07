package com.metaglassar.planes.data.gdl90

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/** A data connector that delivers raw GDL 90 datagrams: a Wi-Fi ADS-B receiver, or the simulator. */
interface Gdl90Feed {
  val name: String

  /** Datagrams as they arrive. Collecting starts listening; cancelling stops it. */
  fun datagrams(): Flow<ByteArray>
}

/**
 * Listens for GDL 90 on UDP (default port 4000), as sent by Stratux and Stratus 4 over their Wi-Fi.
 *
 * The socket is bound with SO_REUSEADDR so another app on the same phone can bind the port too.
 * What we know about sharing (to confirm with real hardware, see docs/12):
 *  - Stratux sends UDP unicast to every Wi-Fi client it has given an address to, so ForeFlight on an
 *    iPad and this app on a phone each get their own copy: that case should just work.
 *  - Two apps on the SAME phone: broadcast datagrams reach every bound socket, but a unicast datagram
 *    is delivered to only one of them (Linux/Android and iOS both behave this way). So running two
 *    GDL 90 apps on one phone at once is not reliable.
 * On Android, the app should also hold a Wi-Fi MulticastLock so broadcast datagrams aren't filtered
 * while the screen is off (done in the app module).
 */
class Gdl90UdpReceiver(private val port: Int = Gdl90.DEFAULT_PORT) : Gdl90Feed {
  override val name = "ADS-B receiver (Wi-Fi, UDP $port)"

  override fun datagrams(): Flow<ByteArray> =
      flow {
            DatagramSocket(null).use { socket ->
              socket.reuseAddress = true
              socket.broadcast = true
              socket.soTimeout = 1_000 // wake up regularly to notice cancellation
              socket.bind(InetSocketAddress(port))
              val buf = ByteArray(2_048)
              val packet = DatagramPacket(buf, buf.size)
              while (currentCoroutineContext().isActive) {
                try {
                  packet.length = buf.size
                  socket.receive(packet)
                  emit(buf.copyOf(packet.length))
                } catch (_: SocketTimeoutException) {
                  // no data this second
                }
              }
            }
          }
          .flowOn(Dispatchers.IO)
}

/**
 * A pretend receiver that plays a [Gdl90Scenario] once a second — the same bytes a Stratux would send,
 * through the same decoder. For testing anywhere (Settings → Simulated receiver).
 */
class SimulatedReceiver(
    val scenario: Gdl90Scenario,
    private val clockMs: () -> Long = System::currentTimeMillis,
) : Gdl90Feed {
  override val name = "Simulated receiver — ${scenario.name}"

  override fun datagrams(): Flow<ByteArray> = flow {
    val start = clockMs()
    while (currentCoroutineContext().isActive) {
      val now = clockMs()
      emit(scenario.frames((now - start) / 1000.0, utcSecondsOfDay(now)))
      delay(1_000)
    }
  }

  /** Seconds since midnight UTC, for the heartbeat time stamp (epoch time has no leap seconds). */
  private fun utcSecondsOfDay(ms: Long) = ((ms / 1000) % 86_400).toInt()
}
