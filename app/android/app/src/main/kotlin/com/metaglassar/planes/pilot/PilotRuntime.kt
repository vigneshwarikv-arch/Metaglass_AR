package com.metaglassar.planes.pilot

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.metaglassar.planes.LocationSource
import com.metaglassar.planes.SettingsStore
import com.metaglassar.planes.core.AnswerCard
import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.core.Units
import com.metaglassar.planes.core.pilot.Airfield
import com.metaglassar.planes.core.pilot.Airfields
import com.metaglassar.planes.core.pilot.AltitudeDatum
import com.metaglassar.planes.core.pilot.CalloutConfig
import com.metaglassar.planes.core.pilot.Callout
import com.metaglassar.planes.core.pilot.DirectToState
import com.metaglassar.planes.core.pilot.GpsFix
import com.metaglassar.planes.core.pilot.Navigation
import com.metaglassar.planes.core.pilot.NearestAirfield
import com.metaglassar.planes.core.pilot.Ownship
import com.metaglassar.planes.core.pilot.PilotText
import com.metaglassar.planes.core.pilot.PositionSource
import com.metaglassar.planes.core.pilot.TrackEstimator
import com.metaglassar.planes.core.pilot.TrafficAdvisor
import com.metaglassar.planes.core.pilot.TrafficRules
import com.metaglassar.planes.core.pilot.TrafficTarget
import com.metaglassar.planes.data.AirportDatabase
import com.metaglassar.planes.data.gdl90.Gdl90Feed
import com.metaglassar.planes.data.gdl90.Gdl90Scenario
import com.metaglassar.planes.data.gdl90.Gdl90Session
import com.metaglassar.planes.data.gdl90.Gdl90UdpReceiver
import com.metaglassar.planes.data.gdl90.ReceiverLink
import com.metaglassar.planes.data.gdl90.ReceiverSnapshot
import com.metaglassar.planes.data.gdl90.SimulatedReceiver
import com.metaglassar.planes.glasses.GlassesCommand
import com.metaglassar.planes.glasses.GlassesConnector
import com.metaglassar.planes.glasses.GlassesStatus
import com.metaglassar.planes.glasses.SpokenOutput
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PhoneGps { NO_PERMISSION, WAITING, OK }

enum class ReceiverMode { OFF, WIFI, SIMULATED }

/** What the pilot screen shows. */
data class PilotState(
    val active: Boolean = false,
    /** The ownship used for everything: receiver GPS when it has a fix, otherwise the phone's. */
    val ownship: Ownship? = null,
    val phoneGps: PhoneGps = PhoneGps.WAITING,
    val receiverMode: ReceiverMode = ReceiverMode.OFF,
    val receiver: ReceiverSnapshot = ReceiverSnapshot.EMPTY,
    val receiverError: String? = null,
    val destination: Airfield? = null,
    val directTo: DirectToState? = null,
    val nearest: NearestAirfield? = null,
    val traffic: List<TrafficTarget> = emptyList(),
    val lastCallout: Callout? = null,
) {
  val receiverConnected: Boolean get() = receiver.link == ReceiverLink.CONNECTED

  /** Traffic and nearest airport come with a receiver (decided for Phase 1; no payments yet). */
  val receiverFeatures: Boolean get() = receiverConnected
}

/**
 * Pilot mode engine loop, app-wide so it keeps running with the screen off (inside [PilotService]).
 * Data connectors: phone GPS, GDL 90 receiver (Wi-Fi or simulated). Output connectors: phone screen
 * (via [state]), phone/glasses voice, Ray-Ban Display card. A SUPPLEMENTARY aid only.
 */
class PilotRuntime(
    private val app: Application,
    private val settings: SettingsStore,
    private val glasses: GlassesConnector,
    private val phoneVoice: () -> SpokenOutput,
    private val location: LocationSource,
) {
  private val scope =
      CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e -> Log.e(TAG, "pilot loop", e) })
  /** Session, advisor and tracker are touched only on this single thread. */
  @OptIn(ExperimentalCoroutinesApi::class) private val engine = Dispatchers.Default.limitedParallelism(1)

  private val _state = MutableStateFlow(PilotState())
  val state: StateFlow<PilotState> = _state.asStateFlow()

  val airports: List<Airfield> by lazy { AirportDatabase.load() }
  val scenarios: List<Gdl90Scenario> by lazy { Gdl90Scenario.loadAll() }

  private val session = Gdl90Session()
  private val advisor = TrafficAdvisor()
  private val tracker = TrackEstimator()
  @Volatile private var phoneOwnship: Ownship? = null
  private var job: Job? = null
  private var lastCard: AnswerCard? = null
  private var lastCardAtMs = 0L

  fun start() {
    if (job != null) return
    _state.value = PilotState(active = true)
    job = scope.launch { run() }
    PilotService.start(app)
  }

  /** One tap (or "pilot mode off") stops everything: loop, receiver, callouts, notification. */
  fun stop() {
    val j = job ?: return
    job = null
    j.cancel()
    PilotService.stop(app)
    _state.value = PilotState(active = false)
    scope.launch {
      if (glasses.status.value is GlassesStatus.Connected) glasses.showCard(AnswerCard("Pilot mode off", null, null))
      lastCard = null
    }
  }

  fun setDestination(a: Airfield?) = settings.update { it.copy(pilot = it.pilot.copy(destination = a?.ident)) }

  /** Answer to "what's that plane?" / "Hey Meta, start" while pilot mode is on. */
  fun speakSummary() {
    val s = state.value
    speak(PilotText.summarySpoken(s.ownship, s.directTo, s.nearest, s.traffic.firstOrNull()))
  }

  private suspend fun run() = coroutineScope {
    launch(engine) { airports.size } // load the airport list off the main thread
    launch { runPhoneGps() }
    launch {
      settings.settings
          .map { p -> Triple(p.pilot.receiverEnabled, p.pilot.simulatedReceiver, p.pilot.simulatedScenario) }
          .distinctUntilChanged()
          .collectLatest { (wifi, simulated, scenario) -> runReceiver(wifi, simulated, scenario) }
    }
    launch {
      glasses.commands.collect {
        Log.i(TAG, "glasses command $it")
        if (it == GlassesCommand.PILOT_MODE_OFF) {
          speak("Pilot mode off.")
          stop()
        }
      }
    }
    while (isActive) {
      withContext(engine) { tick() }
      delay(1_000)
    }
  }

  // region data connectors

  private suspend fun runPhoneGps() {
    while (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
      _state.update { it.copy(phoneGps = PhoneGps.NO_PERMISSION) }
      delay(2_000)
    }
    _state.update { it.copy(phoneGps = PhoneGps.WAITING) }
    location.gpsFixes().collect { loc ->
      val own = withContext(engine) { phoneOwnship(loc) }
      phoneOwnship = own
      _state.update { it.copy(phoneGps = PhoneGps.OK) }
    }
  }

  private fun phoneOwnship(loc: Location): Ownship {
    val fix =
        GpsFix(
            position = GeoPoint(loc.latitude, loc.longitude),
            timeMs = loc.time,
            speedMs = if (loc.hasSpeed()) loc.speed.toDouble() else null,
            courseDeg = if (loc.hasBearing() && loc.hasSpeed() && loc.speed > 1f) loc.bearing.toDouble() else null,
        )
    val est = tracker.update(fix)
    // Android gives height above the WGS-84 ellipsoid; Android 14+ can also give MSL.
    val msl = Build.VERSION.SDK_INT >= 34 && loc.hasMslAltitude()
    val altM = if (msl) loc.mslAltitudeMeters else if (loc.hasAltitude()) loc.altitude else null
    return Ownship(
        position = fix.position,
        groundspeedKt = est?.groundspeedKt,
        trackDeg = est?.trackDeg,
        gpsAltitudeFt = altM?.let(Units::feet),
        gpsAltitudeDatum = if (msl) AltitudeDatum.MSL else AltitudeDatum.ELLIPSOID,
        source = PositionSource.PHONE,
        timeMs = System.currentTimeMillis(),
    )
  }

  private suspend fun runReceiver(wifi: Boolean, simulated: Boolean, scenarioId: String) {
    withContext(engine) {
      session.reset()
      advisor.reset()
    }
    val feed: Gdl90Feed?
    val mode: ReceiverMode
    when {
      simulated -> {
        val scenario = scenarios.firstOrNull { it.id == scenarioId } ?: scenarios.first()
        feed = SimulatedReceiver(scenario)
        mode = ReceiverMode.SIMULATED
      }
      wifi -> {
        feed = Gdl90UdpReceiver()
        mode = ReceiverMode.WIFI
      }
      else -> {
        feed = null
        mode = ReceiverMode.OFF
      }
    }
    _state.update { it.copy(receiverMode = mode, receiverError = null, receiver = ReceiverSnapshot.EMPTY) }
    feed ?: return
    // Some phones drop broadcast datagrams while the screen is off unless a multicast lock is held.
    val lock =
        if (mode == ReceiverMode.WIFI) {
          app.getSystemService(WifiManager::class.java)?.createMulticastLock("pilot-gdl90")?.apply {
            setReferenceCounted(false)
            acquire()
          }
        } else null
    try {
      feed.datagrams().collect { d -> withContext(engine) { session.ingest(d) } }
    } catch (e: java.net.BindException) {
      _state.update { it.copy(receiverError = "UDP port 4000 is in use by another app.") }
    } catch (e: java.io.IOException) {
      _state.update { it.copy(receiverError = e.message ?: "Receiver error") }
    } finally {
      lock?.release()
    }
  }

  // endregion

  /** Once a second, on [engine]: pick the ownship, work out guidance and traffic, drive the outputs. */
  private fun tick() {
    val p = settings.settings.value.pilot
    advisor.config = CalloutConfig(maxRangeNm = p.calloutRangeNm, maxAltitudeDiffFt = p.calloutAltitudeBandFt.toDouble())
    val snap = session.snapshot()
    val now = System.currentTimeMillis()
    val phone = phoneOwnship?.takeIf { now - it.timeMs <= PHONE_FIX_MAX_AGE_MS }
    val own = snap.ownship?.takeIf { snap.gpsValid } ?: phone
    val dest = p.destination?.let { id -> airports.firstOrNull { it.ident == id } }
    val direct =
        if (own != null && dest != null)
            DirectToState(dest, Navigation.directTo(own.position, dest.position, own.groundspeedKt, own.trackDeg))
        else null
    val receiverFeatures = snap.link == ReceiverLink.CONNECTED && own != null
    val nearest = if (receiverFeatures) Airfields.nearest(own!!.position, airports).firstOrNull() else null
    val traffic = if (receiverFeatures) TrafficRules.relevant(own!!, snap.traffic, advisor.config) else emptyList()
    val callout = if (receiverFeatures) advisor.next(own!!, snap.traffic, now) else null

    _state.update {
      it.copy(
          ownship = own,
          receiver = snap,
          destination = dest,
          directTo = direct,
          nearest = nearest,
          traffic = traffic,
          lastCallout = callout ?: it.lastCallout,
      )
    }

    if (callout != null && p.voiceCallouts) speak(callout.spoken)
    val g = glasses.status.value
    if (g is GlassesStatus.Connected && g.hasDisplay) {
      val card = PilotText.card(own, direct, nearest, traffic.firstOrNull())
      if (card != lastCard && now - lastCardAtMs >= CARD_MIN_INTERVAL_MS) {
        lastCard = card
        lastCardAtMs = now
        scope.launch { glasses.showCard(card) }
      }
    }
  }

  private fun speak(text: String) {
    scope.launch {
      if (glasses.status.value is GlassesStatus.Connected) glasses.speak(text) else phoneVoice().speak(text)
    }
  }

  private fun granted(p: String) = ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED

  private companion object {
    const val TAG = "PilotRuntime"
    const val PHONE_FIX_MAX_AGE_MS = 5_000L
    /** Don't flood the Display: at most one card update this often. */
    const val CARD_MIN_INTERVAL_MS = 2_000L
  }
}
