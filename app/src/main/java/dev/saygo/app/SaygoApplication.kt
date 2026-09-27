package dev.saygo.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Toast
import dev.saygo.app.data.Preferences
import dev.saygo.app.data.SessionState
import dev.saygo.app.speech.FeedbackQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.Locale

class SaygoApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var speech: TextToSpeech? = null
    private val feedback by lazy {
        FeedbackQueue(
            enabled = { Preferences(this).spokenFeedback },
            speak = { speech?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "saygo-result") },
            stop = { speech?.stop() },
        )
    }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            SessionState.feedback.drop(1).collect { result ->
                Toast.makeText(this@SaygoApplication, result.title, Toast.LENGTH_LONG).show()
                if (Preferences(this@SaygoApplication).spokenFeedback) {
                    feedback.offer(result.title)
                    if (speech == null) {
                        speech = TextToSpeech(this@SaygoApplication) { status ->
                            // Failure may be reported before the constructor returns.
                            Handler(Looper.getMainLooper()).post {
                                val ready = status == TextToSpeech.SUCCESS && (speech?.setLanguage(Locale.US) ?: -1) >= 0
                                feedback.engineReady(ready)
                                if (!ready) { speech?.shutdown(); speech = null }
                            }
                        }
                    }
                }
            }
        }
    }

    fun stopSpeaking() { feedback.cancel() }
}
