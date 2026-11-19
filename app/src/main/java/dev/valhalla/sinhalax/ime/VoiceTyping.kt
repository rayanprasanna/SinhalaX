package dev.valhalla.sinhalax.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import dev.valhalla.sinhalax.keyboard.Language
import dev.valhalla.sinhalax.keyboard.VoiceInput
import dev.valhalla.sinhalax.keyboard.VoiceState
import dev.valhalla.sinhalax.ui.VoicePermissionActivity

/**
 * Voice typing with Android's own speech recogniser (on most phones, Google's speech service,
 * which supports Sinhala as si-LK). Words heard so far go in as composing text and are committed
 * when the phrase ends. Audio is handled by that service, not by SinhalaX, and only while the
 * voice panel is open and listening.
 *
 * Must be used on the main thread (the IME's callbacks are).
 */
class VoiceTyping(
    private val context: Context,
    /** (text, final): show [text] as the current phrase; final = commit it. */
    private val onText: (String, Boolean) -> Unit,
) : VoiceInput {

    override val state = VoiceState()

    private var recognizer: SpeechRecognizer? = null

    override fun start(language: Language) {
        cancel()
        state.message = null
        state.partial = ""
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            state.message = "Allow the microphone, then tap the mic again."
            VoicePermissionActivity.launch(context)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            state.message = "No voice typing service on this phone. Install or update the Google app."
            return
        }
        val tag = if (language == Language.SINHALA) "si-LK" else "en-US"
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull() ?: run {
            state.message = "Voice typing couldn't start. Try again."
            return
        }
        recognizer = r
        r.setRecognitionListener(Listener(r))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Let people pause to think without the phrase ending too soon.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        state.listening = true
        runCatching { r.startListening(intent) }.onFailure {
            state.listening = false
            state.message = "Voice typing couldn't start. Try again."
            release()
        }
    }

    override fun finish() {
        // Final results still arrive (onResults) after this.
        runCatching { recognizer?.stopListening() }
        state.level = 0f
    }

    override fun cancel() {
        val heard = state.partial
        release()
        if (heard.isNotEmpty()) onText(heard, true) // keep what was already shown
        state.partial = ""
        state.listening = false
        state.level = 0f
    }

    fun destroy() = release()

    private fun release() {
        recognizer?.let { r ->
            runCatching { r.cancel() }
            runCatching { r.destroy() }
        }
        recognizer = null
    }

    private inner class Listener(private val owner: SpeechRecognizer) : RecognitionListener {
        private fun current() = recognizer === owner

        override fun onReadyForSpeech(params: Bundle?) {
            if (!current()) return
            state.listening = true
            state.message = null
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            if (!current()) return
            state.level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (!current()) return
            state.level = 0f
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!current()) return
            val text = partialResults.firstResult() ?: return
            state.partial = text
            onText(text, false)
        }

        override fun onResults(results: Bundle?) {
            if (!current()) return
            val text = results.firstResult() ?: state.partial
            if (text.isNotEmpty()) onText(text, true)
            state.partial = ""
            state.listening = false
            state.level = 0f
            release()
        }

        override fun onError(error: Int) {
            if (!current()) return
            val heard = state.partial
            if (heard.isNotEmpty()) onText(heard, true)
            state.partial = ""
            state.listening = false
            state.level = 0f
            state.message = messageFor(error)
            release()
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Tap the mic and try again."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice typing needs internet."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow the microphone for SinhalaX in Settings."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The voice service is busy. Try again."
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE ->
            "This language isn't available in your phone's voice service. Update the Google app."
        else -> "Voice typing stopped. Tap the mic to try again."
    }

    private companion object {
        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31), as literals.
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
