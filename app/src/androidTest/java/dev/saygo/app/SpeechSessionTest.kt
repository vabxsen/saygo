package dev.saygo.app

import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.test.platform.app.InstrumentationRegistry
import dev.saygo.app.speech.SpeechSession
import org.junit.Assert.*
import org.junit.Test

/** Exercises recognition callback/lifecycle contracts, not acoustic accuracy. */
class SpeechSessionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val statuses = mutableListOf<String>()
    private val results = mutableListOf<String>()
    private val errors = mutableListOf<String>()
    private fun session() = SpeechSession(context, statuses::add, results::add, errors::add)
    private fun result(text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }

    @Test fun resultIsDeliveredOnceAndLateCallbacksAreIgnored() {
        instrumentation.runOnMainSync {
            val speech = session()
            speech.onResults(result("Open YouTube"))
            speech.onResults(result("Go home"))
            speech.onError(SpeechRecognizer.ERROR_NETWORK)
            speech.onReadyForSpeech(null)
        }
        assertEquals(listOf("Open YouTube"), results)
        assertTrue(errors.isEmpty())
        assertTrue(statuses.isEmpty())
    }

    @Test fun explicitClosePreventsActionsAndStatusUpdates() {
        instrumentation.runOnMainSync {
            val speech = session()
            speech.close()
            speech.close()
            speech.onResults(result("Go home"))
            speech.onBeginningOfSpeech()
            speech.onEndOfSpeech()
            speech.onError(SpeechRecognizer.ERROR_NO_MATCH)
        }
        assertTrue(results.isEmpty())
        assertTrue(errors.isEmpty())
        assertTrue(statuses.isEmpty())
    }

    @Test fun emptyRecognitionProducesOneRetryableError() {
        instrumentation.runOnMainSync {
            val speech = session()
            speech.onResults(Bundle())
            speech.onResults(result("Go home"))
        }
        assertEquals(1, errors.size)
        assertTrue(results.isEmpty())
    }

    @Test fun speechErrorsHaveActionableMessagesAndNeverDeliverCommands() {
        val cases = mapOf(
            SpeechRecognizer.ERROR_NO_MATCH to "catch",
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT to "catch",
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS to "Microphone",
            SpeechRecognizer.ERROR_NETWORK to "connection",
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT to "connection",
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY to "busy",
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED to "English",
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE to "English",
            SpeechRecognizer.ERROR_AUDIO to "microphone",
            SpeechRecognizer.ERROR_CLIENT to "stopped",
        )
        cases.forEach { (code, hint) ->
            instrumentation.runOnMainSync { session().onError(code) }
            assertTrue(errors.last().contains(hint, ignoreCase = true))
        }
        assertEquals(cases.size, errors.size)
        assertTrue(results.isEmpty())
    }

    @Test fun readinessBeginningAndEndHaveDistinctStatus() {
        instrumentation.runOnMainSync {
            val speech = session()
            speech.onReadyForSpeech(null)
            speech.onBeginningOfSpeech()
            speech.onEndOfSpeech()
            speech.close()
        }
        assertEquals(listOf("Listening…", "I’m listening…", "One moment…"), statuses)
    }
}
