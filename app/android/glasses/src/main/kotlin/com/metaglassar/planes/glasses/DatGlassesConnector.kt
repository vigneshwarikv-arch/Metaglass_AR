package com.metaglassar.planes.glasses

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.core.voiceinvocations.isVoiceInvocationsIntent
import com.meta.wearable.dat.core.voiceinvocations.startVoiceInvocationsStream
import com.meta.wearable.dat.core.voiceinvocations.types.actions.LaunchApp
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.display.views.FlexBoxBackground
import com.meta.wearable.dat.display.views.TextColor
import com.meta.wearable.dat.display.views.TextStyle
import com.meta.wearable.dat.motion.Motion
import com.meta.wearable.dat.motion.addMotion
import com.meta.wearable.dat.motion.types.MotionConfiguration
import com.meta.wearable.dat.motion.types.MotionSample
import com.meta.wearable.dat.motion.types.MotionSamplingRate
import com.meta.wearable.dat.motion.types.MotionSource
import com.meta.wearable.dat.speech.Speech
import com.meta.wearable.dat.speech.addSpeech
import com.metaglassar.planes.core.Answer
import com.metaglassar.planes.core.HeadAim
import com.metaglassar.planes.core.Pointing
import com.metaglassar.planes.core.Vec3
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Meta glasses via the Wearables Device Access Toolkit (DAT) 1.0, following Meta's docs and the
 * BirdSpotter / DisplayAccess samples in facebook/meta-wearables-dat-android.
 *
 * Flow: register with Meta AI → when glasses are connected, open a DeviceSession → once STARTED,
 * attach Motion (head direction), Speech (hears "what's that plane?") and, on Ray-Ban Display,
 * Display (answer card). Voice Invocations ("Hey Meta, start [BrandName]") also count as a question.
 *
 * Motion, Speech and Voice Invocations are EXPERIMENTAL in DAT 1.0: they work for development and
 * release-channel testing, but must be enabled for the app in the Wearables Developer Center and
 * cannot ship to production release channels yet.
 */
class DatGlassesConnector(
    context: Context,
    /** Local magnetic declination in degrees (east positive), to turn magnetic into true bearings. */
    private val declinationDeg: () -> Double,
) : GlassesConnector {

  private val appContext = context.applicationContext
  // A glasses failure must never take down the phone app: log it and show it in Settings.
  private val scope =
      CoroutineScope(
          SupervisorJob() +
              Dispatchers.Main.immediate +
              CoroutineExceptionHandler { _, e ->
                Log.e(TAG, "glasses connector error", e)
                _status.value = GlassesStatus.Error(e.message ?: e.javaClass.simpleName)
              })
  private val headAim = HeadAim()
  private val spoken by lazy { SpokenOutput(appContext) }

  private val _status = MutableStateFlow<GlassesStatus>(GlassesStatus.NotRegistered)
  override val status: StateFlow<GlassesStatus> = _status.asStateFlow()

  private val _headPointing = MutableStateFlow<Pointing?>(null)
  override val headPointing: StateFlow<Pointing?> = _headPointing.asStateFlow()

  private val _askRequests = MutableSharedFlow<AskTrigger>(extraBufferCapacity = 4)
  override val askRequests: SharedFlow<AskTrigger> = _askRequests.asSharedFlow()

  private var session: DeviceSession? = null
  private var sessionJobs = mutableListOf<Job>()
  private var motion: Motion? = null
  private var speech: Speech? = null
  private var display: Display? = null
  private var displayReady = false
  private var lastQuestionAtMs = 0L
  private var deviceName: String? = null
  private var deviceHasDisplay = false
  private var voiceJob: Job? = null
  private val metadataJobs = mutableMapOf<DeviceIdentifier, Job>()
  private val connectedDevices = mutableSetOf<DeviceIdentifier>()
  private var started = false

  /**
   * Starts DAT. Meta's docs: request runtime permissions (BLUETOOTH_CONNECT) before initializing,
   * otherwise DAT can't see paired glasses. So the app calls this once Bluetooth permission is
   * granted, or when simulated glasses are turned on — not at every launch. Safe to call repeatedly.
   */
  override fun start() {
    if (started) return
    started = true
    Wearables.initialize(appContext).onFailure { error, _ ->
      _status.value = GlassesStatus.Unavailable(error.description)
    }
    if (_status.value !is GlassesStatus.Unavailable) {
      scope.launch { observeRegistration() }
      scope.launch { observeDevices() }
    }
  }

  // region registration + devices

  private suspend fun observeRegistration() {
    Wearables.registrationState.collect { state ->
      when (state) {
        RegistrationState.REGISTERED -> {
          if (session == null) _status.value = GlassesStatus.NoGlasses
          startVoiceInvocations()
          maybeStartSession()
        }
        RegistrationState.REGISTERING -> _status.value = GlassesStatus.Registering
        else -> {
          stopSession()
          voiceJob?.cancel()
          voiceJob = null
          _status.value = GlassesStatus.NotRegistered
        }
      }
    }
  }

  private suspend fun observeDevices() {
    Wearables.devices.collect { ids ->
      (metadataJobs.keys - ids.toSet()).forEach { gone ->
        metadataJobs.remove(gone)?.cancel()
        connectedDevices.remove(gone)
      }
      ids.forEach { id ->
        metadataJobs.getOrPut(id) {
          scope.launch {
            Wearables.devicesMetadata[id]?.collect { device ->
              if (device.linkState == LinkState.CONNECTED) {
                connectedDevices += id
                deviceName = device.name
                deviceHasDisplay = device.isDisplayCapable()
              } else {
                connectedDevices -= id
              }
              maybeStartSession()
            }
          }
        }
      }
      maybeStartSession()
    }
  }

  override fun startRegistration(activity: Activity) {
    Wearables.startRegistration(activity)
  }

  override fun startUnregistration(activity: Activity) {
    Wearables.startUnregistration(activity)
  }

  // endregion

  // region session

  private fun maybeStartSession() {
    if (session != null) return
    if (Wearables.registrationState.value != RegistrationState.REGISTERED) return
    if (connectedDevices.isEmpty()) {
      if (_status.value !is GlassesStatus.Error) _status.value = GlassesStatus.NoGlasses
      return
    }
    _status.value = GlassesStatus.Connecting
    Wearables.createSession(AutoDeviceSelector()).fold(
        onSuccess = { newSession ->
          session = newSession
          // Subscribe before start() so no transition is missed.
          sessionJobs += scope.launch {
            newSession.state.collect { state ->
              when (state) {
                DeviceSessionState.STARTED -> onSessionStarted(newSession)
                DeviceSessionState.STOPPED -> onSessionStopped()
                else -> Unit
              }
            }
          }
          sessionJobs += scope.launch {
            newSession.errors.collect { error ->
              Log.w(TAG, "session error: ${error.description}")
              _status.value = GlassesStatus.Error(error.description)
            }
          }
          newSession.start()
        },
        onFailure = { error, _ -> _status.value = GlassesStatus.Error(error.description) },
    )
  }

  private fun onSessionStarted(s: DeviceSession) {
    attachMotion(s)
    scope.launch { attachSpeechIfPermitted(s) }
    if (deviceHasDisplay) attachDisplay(s)
    publishConnected()
  }

  private fun onSessionStopped() {
    // STOPPED is terminal: drop everything and make a new session when glasses come back.
    stopSession()
    scope.launch {
      delay(2_000)
      maybeStartSession()
    }
  }

  private fun stopSession() {
    sessionJobs.forEach { it.cancel() }
    sessionJobs.clear()
    session?.stop()
    session = null
    motion = null
    speech = null
    display = null
    displayReady = false
    _headPointing.value = null
  }

  private fun publishConnected() {
    _status.value =
        GlassesStatus.Connected(
            deviceName = deviceName,
            hasDisplay = displayReady,
            headTracking = _headPointing.value != null,
            listening = speech != null,
        )
  }

  // endregion

  // region capabilities

  private fun attachMotion(s: DeviceSession) {
    s.addMotion(MotionConfiguration(samplingRate = MotionSamplingRate.HZ_10)).fold(
        onSuccess = { m ->
          motion = m
          sessionJobs += scope.launch { m.samples.collect(::onMotion) }
          m.start()
        },
        onFailure = { error, _ -> Log.w(TAG, "motion unavailable: ${error.description}") },
    )
  }

  private fun onMotion(sample: MotionSample) {
    if (sample.source != MotionSource.GLASSES) return // ignore the Neural Band
    val a = sample.accelerometer ?: return
    val m = sample.magnetometer ?: return
    val pointing =
        headAim.pointing(
            Vec3(a.x.toDouble(), a.y.toDouble(), a.z.toDouble()),
            Vec3(m.x.toDouble(), m.y.toDouble(), m.z.toDouble()),
            declinationDeg(),
        ) ?: return
    val first = _headPointing.value == null
    _headPointing.value = pointing
    if (first) publishConnected()
  }

  private suspend fun attachSpeechIfPermitted(s: DeviceSession) {
    if (speech != null) return
    var granted = false
    Wearables.checkPermissionStatus(Permission.MICROPHONE).onSuccess { status ->
      granted = status == PermissionStatus.Granted
    }
    if (!granted) return
    s.addSpeech().fold(
        onSuccess = { sp ->
          speech = sp
          sessionJobs += scope.launch {
            sp.transcriptions.collect { result ->
              if (result != null && result.isFinal && QuestionMatcher.isPlaneQuestion(result.text)) {
                askOnce(AskTrigger.SPOKEN_QUESTION)
              }
            }
          }
          sp.start().onFailure { error, _ -> Log.w(TAG, "speech start failed: ${error.description}") }
          publishConnected()
        },
        onFailure = { error, _ -> Log.w(TAG, "speech unavailable: ${error.description}") },
    )
  }

  override fun onPermissionsChanged() {
    session?.let { s -> scope.launch { attachSpeechIfPermitted(s) } }
  }

  private fun attachDisplay(s: DeviceSession) {
    s.addDisplay().fold(
        onSuccess = { d ->
          display = d
          sessionJobs += scope.launch {
            d.state.collect { state ->
              displayReady = state == DisplayState.STARTED
              publishConnected()
            }
          }
        },
        onFailure = { error, _ -> Log.w(TAG, "display unavailable: ${error.description}") },
    )
  }

  // endregion

  // region voice

  private fun startVoiceInvocations() {
    if (voiceJob != null) return
    voiceJob = scope.launch {
      val stream = Wearables.startVoiceInvocationsStream(AutoDeviceSelector())
      try {
        launch { stream.errors.collect { error -> Log.w(TAG, "voice invocations: ${error.description}") } }
        stream.invocations.collect { invocation ->
          when (invocation) {
            is LaunchApp -> {
              // Every invocation must be answered exactly once.
              invocation.responseHandle.sendSuccess(null)
              askOnce(AskTrigger.VOICE_LAUNCH)
            }
          }
        }
      } finally {
        stream.close()
      }
    }
  }

  override fun onIntent(intent: Intent) {
    // Cold/warm launch from "Hey Meta, start [BrandName]". The stream above usually delivers it
    // too; askOnce() de-duplicates.
    if (isVoiceInvocationsIntent(intent)) askOnce(AskTrigger.VOICE_LAUNCH)
  }

  private fun askOnce(trigger: AskTrigger) {
    val now = System.currentTimeMillis()
    if (now - lastQuestionAtMs < 3_000) return
    lastQuestionAtMs = now
    _askRequests.tryEmit(trigger)
  }

  // endregion

  // region output

  override suspend fun deliver(answer: Answer) {
    val d = display
    val card = answer.card
    if (d != null && displayReady && card != null) {
      d.sendContent {
        flexBox(gap = 8, padding = 24, background = FlexBoxBackground.CARD) {
          text(card.title, style = TextStyle.HEADING)
          card.route?.let { text(it, style = TextStyle.BODY) }
          card.meta?.let { text(it, style = TextStyle.BODY, color = TextColor.SECONDARY) }
        }
      }.onFailure { error, _ -> Log.w(TAG, "display card failed: ${error.description}") }
    }
    spoken.speak(answer.spoken)
  }

  /** Waits until glasses are connected (used by tests/tools); not needed by the app. */
  suspend fun awaitConnected() = status.first { it is GlassesStatus.Connected }

  // endregion

  private companion object {
    const val TAG = "DatGlasses"
  }
}

/** Recognises "what's that plane?" and close variants in a speech transcript. */
object QuestionMatcher {
  private val pattern =
      Regex("""\b(what|which|whats|what's)\b.{0,12}\b(plane|airplane|aeroplane|aircraft|flight|jet)\b""")

  fun isPlaneQuestion(text: String): Boolean = pattern.containsMatchIn(text.lowercase())
}
