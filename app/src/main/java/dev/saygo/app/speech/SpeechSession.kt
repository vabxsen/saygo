package dev.saygo.app.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** One user-initiated listening session; never loops or restarts on an error. */
class SpeechSession(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) : RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private val timeout = Runnable { fail("Listening timed out. Tap the microphone to try again.") }

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            fail("No speech service found. Install or enable an Android speech recognition provider.")
            return
        }
        try {
            recognizer = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(this)
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            })
            handler.postDelayed(timeout, 15_000)
        } catch (_: SecurityException) {
            fail("Microphone access is off. Enable it in Android settings.")
        } catch (_: UnsupportedOperationException) {
            fail("Speech recognition isn’t supported by this device.")
        }
    }

    fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacks(timeout)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun fail(message: String) {
        if (closed) return
        close()
        onError(message)
    }

    override fun onReadyForSpeech(params: Bundle?) { if (!closed) onStatus("Listening…") }
    override fun onBeginningOfSpeech() { if (!closed) onStatus("I’m listening…") }
    override fun onEndOfSpeech() { if (!closed) onStatus("One moment…") }
    override fun onResults(results: Bundle?) {
        if (closed) return
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (text.isNullOrBlank()) fail("I didn’t catch that. Try a short command.")
        else { close(); onResult(text) }
    }
    override fun onError(error: Int) = fail(when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn’t catch that. Try again somewhere quieter."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone access is off. Check Android settings."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Your speech provider needs a connection. Check your internet and try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech provider is busy. Wait a moment and try again."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "English recognition isn’t available. Download English in your speech provider’s settings."
        SpeechRecognizer.ERROR_AUDIO -> "The microphone is unavailable. Close other recording apps and try again."
        else -> "Speech recognition stopped. Tap to try again."
    })
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
