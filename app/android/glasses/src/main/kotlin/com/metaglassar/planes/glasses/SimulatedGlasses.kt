package com.metaglassar.planes.glasses

import android.content.Context
import android.util.Log
import android.view.View
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitConfig
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import com.meta.wearable.dat.mockdevice.api.speech.MockSpeechSource
import com.meta.wearable.dat.motion.types.MotionSample
import com.meta.wearable.dat.motion.types.MotionSource
import com.meta.wearable.dat.motion.types.Vector3
import kotlin.math.cos
import kotlin.math.sin

enum class SimulatedModel(val label: String) {
  RAY_BAN_META("Ray-Ban Meta"),
  RAY_BAN_DISPLAY("Meta Ray-Ban Display"),
}

/**
 * Simulated Meta glasses (DAT MockDeviceKit), so the whole glasses flow can be tested without
 * hardware — e.g. in India, where Meta AI glasses aren't sold. The app's real [DatGlassesConnector]
 * talks to these exactly as it would to real glasses: registration, session, head motion, speech,
 * voice launch and the Display card all go through DAT.
 */
class SimulatedGlasses(context: Context) {
  private val appContext = context.applicationContext
  private val kit = MockDeviceKit.getInstance(appContext)
  private var glasses: MockGlasses? = null

  val isOn: Boolean get() = kit.isEnabled && glasses != null

  /** Turns the simulator on with freshly paired, powered-on, worn glasses. */
  fun enable(model: SimulatedModel): Boolean {
    disable()
    kit.enable(MockDeviceKitConfig(initiallyRegistered = true, initialPermissionsGranted = true))
    val datModel =
        when (model) {
          SimulatedModel.RAY_BAN_META -> GlassesModel.RAYBAN_META
          SimulatedModel.RAY_BAN_DISPLAY -> GlassesModel.META_RAYBAN_DISPLAY
        }
    val g = kit.pairGlasses(datModel).getOrNull()
    if (g == null) {
      Log.w(TAG, "could not pair simulated glasses")
      return false
    }
    g.powerOn()
    g.unfold()
    g.don()
    // Speech comes from our buttons, not the phone microphone.
    g.services.speech.setTranscriptionSource(MockSpeechSource.INJECTED)
    glasses = g
    setHead(magneticBearingDeg = 0.0, elevationDeg = 15.0)
    return true
  }

  fun disable() {
    glasses = null
    if (kit.isEnabled) kit.disable()
  }

  /**
   * Points the simulated head: [magneticBearingDeg] clockwise from magnetic north, [elevationDeg]
   * above the horizon. Samples use the axis convention in core/HeadAim (+X up, +Y right, +Z forward),
   * so the app reads back exactly this direction.
   */
  fun setHead(magneticBearingDeg: Double, elevationDeg: Double) {
    val g = glasses ?: return
    val b = Math.toRadians(magneticBearingDeg)
    val e = Math.toRadians(elevationDeg)
    val gravity = 9.80665
    val field = 40.0 // µT, horizontal component only
    val accel = Vector3((gravity * cos(e)).toFloat(), 0f, (gravity * sin(e)).toFloat())
    // Magnetic north in the glasses' frame for a head facing b and tilted up e.
    val north =
        Vector3(
            (-field * cos(b) * sin(e)).toFloat(),
            (-field * sin(b)).toFloat(),
            (field * cos(b) * cos(e)).toFloat(),
        )
    val intervalNs = 100_000_000L // 10 Hz
    val samples = List(10) { i -> MotionSample(i * intervalNs, accel, Vector3(0f, 0f, 0f), north, null, MotionSource.GLASSES) }
    g.services.motion.setMotionFeed(samples, true)
  }

  /** As if the wearer said "What's that plane?" (needs the speech capability to be listening). */
  fun sayWhatsThatPlane() {
    glasses?.services?.speech?.simulateTranscription("what's that plane", true, 1f)
  }

  /** As if the wearer said "Hey Meta, start [BrandName]". */
  fun voiceLaunch() {
    glasses?.services?.voiceInvocation?.simulateLaunchAppAction()
  }

  /** A live view of what the simulated Ray-Ban Display shows, or null for glasses without a display. */
  fun displayPreview(context: Context): View? = glasses?.services?.display?.createPreviewView(context)

  private companion object {
    const val TAG = "SimulatedGlasses"
  }
}
