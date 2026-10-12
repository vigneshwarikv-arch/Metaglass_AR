package com.metaglassar.planes.glasses

import android.app.Activity
import com.metaglassar.planes.core.AnswerOutput
import com.metaglassar.planes.core.Pointing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Connection state shown in Settings. */
sealed interface GlassesStatus {
  /** DAT could not start (e.g. Meta AI app missing). */
  data class Unavailable(val reason: String) : GlassesStatus
  /** App not yet connected to Meta AI. The user taps "Connect glasses". */
  data object NotRegistered : GlassesStatus
  data object Registering : GlassesStatus
  /** Registered, but no glasses connected right now. */
  data object NoGlasses : GlassesStatus
  data object Connecting : GlassesStatus
  data class Connected(
      val deviceName: String?,
      val hasDisplay: Boolean,
      val headTracking: Boolean,
      val listening: Boolean,
  ) : GlassesStatus
  data class Error(val message: String) : GlassesStatus
}

/** Why the user wants an answer right now. */
enum class AskTrigger {
  /** "Hey Meta, start [BrandName]" — Meta AI launched/foregrounded the app. */
  VOICE_LAUNCH,
  /** Speech on the glasses heard "what's that plane". */
  SPOKEN_QUESTION,
  /** The phone's "Ask" button (testing without saying anything). */
  PHONE_BUTTON,
}

/**
 * Output connector for smart glasses. The app talks only to this interface; the DAT implementation
 * is [DatGlassesConnector]. Another brand of glasses = another implementation.
 */
interface GlassesConnector : AnswerOutput {
  val status: StateFlow<GlassesStatus>

  /** Where the wearer's head points (true north), or null without head tracking. */
  val headPointing: StateFlow<Pointing?>

  /** Emits each time the wearer asks "what's that plane?". */
  val askRequests: Flow<AskTrigger>

  /** Starts the glasses SDK. Call once Bluetooth permission is granted (or for simulated glasses). */
  fun start()

  /** Connect the app to Meta AI (opens the Meta AI app). */
  fun startRegistration(activity: Activity)

  fun startUnregistration(activity: Activity)

  /**
   * Call after the glasses microphone permission changes (the Activity requests it with
   * `Wearables.RequestPermissionContract()` from a user tap), so speech listening can start.
   */
  fun onPermissionsChanged()

  /** Call when the activity receives an intent, so "Hey Meta, start [BrandName]" launches count as a question. */
  fun onIntent(intent: android.content.Intent)
}
