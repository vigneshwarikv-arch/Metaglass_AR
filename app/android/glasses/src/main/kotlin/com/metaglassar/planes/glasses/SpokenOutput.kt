package com.metaglassar.planes.glasses

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Speaks answers with Android's built-in text-to-speech.
 *
 * DAT 1.0 has no "speak through the glasses" API; Meta glasses are also a Bluetooth audio device,
 * so phone audio plays in the glasses' open-ear speakers while they are connected.
 * TODO(glasses): confirm on real Ray-Ban Meta / Ray-Ban Display that the answer is heard in the
 * glasses (not the phone speaker) and that it does not interrupt Meta AI.
 */
class SpokenOutput(context: Context) {
  private val ready = CompletableDeferred<Boolean>()
  private val tts: TextToSpeech =
      TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }

  init {
    tts.setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build())
  }

  /** Speaks [text] and returns when finished (or after a timeout). False if TTS is unavailable. */
  suspend fun speak(text: String): Boolean {
    if (withTimeoutOrNull(5_000) { ready.await() } != true) return false
    // Indian English reads "kilometres" and Indian city names well; falls back to the device default.
    if (tts.isLanguageAvailable(Locale("en", "IN")) >= TextToSpeech.LANG_AVAILABLE) tts.language = Locale("en", "IN")
    val id = UUID.randomUUID().toString()
    return withTimeoutOrNull(20_000) {
      suspendCancellableCoroutine { cont ->
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
              override fun onStart(utteranceId: String?) = Unit

              override fun onDone(utteranceId: String?) {
                if (utteranceId == id && cont.isActive) cont.resume(true)
              }

              @Deprecated("Deprecated in Java")
              override fun onError(utteranceId: String?) {
                if (utteranceId == id && cont.isActive) cont.resume(false)
              }
            })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        cont.invokeOnCancellation { tts.stop() }
      }
    } ?: false
  }

  fun shutdown() = tts.shutdown()
}
