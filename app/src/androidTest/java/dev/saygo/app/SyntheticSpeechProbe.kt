package dev.saygo.app

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.saygo.app.commands.CommandExecutor
import dev.saygo.app.commands.CommandParser
import dev.saygo.app.data.SessionState
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicitly invoked probe, outside the JUnit suite. Uses real ASR, never callback injection.
 * File input bypasses the microphone and SpeechSession UI; it is not a live-speech test.
 */
class SyntheticSpeechProbe : Instrumentation() {
    private var caseName = "open-youtube"
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        caseName = arguments?.getString("case") ?: caseName
        start()
    }

    override fun onStart() {
        val output = Bundle()
        val trace = mutableListOf<String>()
        var recognizer: SpeechRecognizer? = null
        var audio: ParcelFileDescriptor? = null
        var source: File? = null
        var writer: Thread? = null
        var writeEnd: ParcelFileDescriptor? = null
        var passed = false
        try {
            if (Build.VERSION.SDK_INT < 33) error("Audio-source recognition requires Android 13+")
            val expected = mapOf(
                "open-youtube" to "open youtube",
                "google-search" to "search google for android accessibility",
                "youtube-search" to "search youtube for cooking",
                "cancel" to "cancel",
            )[caseName] ?: error("Unknown synthetic case")
            val automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            targetContext.startActivity(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val foregroundDeadline = SystemClock.uptimeMillis() + 10_000
            while (automation.rootInActiveWindow?.packageName?.toString() != targetContext.packageName && SystemClock.uptimeMillis() < foregroundDeadline) SystemClock.sleep(50)
            check(automation.rootInActiveWindow?.packageName?.toString() == targetContext.packageName) { "saygo is not foreground" }
            source = File(targetContext.cacheDir, "synthetic-speech-probe.pcm")
            context.assets.open("synthetic-speech/$caseName.pcm").use { input -> source.outputStream().use(input::copyTo) }
            val pipe = ParcelFileDescriptor.createPipe()
            audio = pipe[0]
            writeEnd = pipe[1]
            val finished = CountDownLatch(1)
            var transcript: String? = null
            var failure: Int? = null
            runOnMainSync {
                val onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(targetContext)
                trace += "onDevice=$onDevice"
                val activeRecognizer = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(targetContext) else SpeechRecognizer.createSpeechRecognizer(targetContext)
                recognizer = activeRecognizer
                activeRecognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { trace += "ready" }
                    override fun onBeginningOfSpeech() { trace += "speech-began" }
                    override fun onEndOfSpeech() { trace += "speech-ended" }
                    override fun onResults(results: Bundle?) {
                        trace += "resultKeys=${results?.keySet()}"
                        transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        trace += "recognized=$transcript"
                        finished.countDown()
                    }
                    override fun onError(error: Int) { failure = error; trace += "error=$error"; finished.countDown() }
                    override fun onSegmentResults(segment: Bundle) {
                        val text = segment.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        trace += "segment=$text keys=${segment.keySet()}"
                        if (!text.isNullOrBlank()) transcript = listOfNotNull(transcript, text).joinToString(" ")
                    }
                    override fun onEndOfSegmentedSession() { trace += "segmented-session-ended"; finished.countDown() }
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                activeRecognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audio)
                    putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                })
            }
            writer = Thread {
                try {
                    source.inputStream().use { input ->
                        ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { sink ->
                            val frame = ByteArray(640)
                            while (!Thread.currentThread().isInterrupted) {
                                val count = input.read(frame)
                                if (count < 0) break
                                sink.write(frame, 0, count)
                                Thread.sleep(20)
                            }
                        }
                    }
                } catch (_: java.io.IOException) {
                    // A recognizer may close its input after the utterance, before trailing silence.
                } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }.apply { isDaemon = true; start() }
            check(finished.await(20, TimeUnit.SECONDS)) { "Real speech provider timed out" }
            check(failure == null) { "Real speech provider error $failure" }
            check(!transcript.isNullOrBlank()) { "Speech provider returned no transcript" }
            val parsed = CommandParser.parse(checkNotNull(transcript).lowercase(Locale.ROOT))
            check(parsed == CommandParser.parse(expected)) { "Unexpected recognition: $transcript" }
            runOnMainSync { CommandExecutor(targetContext).execute(checkNotNull(parsed), targetContext.packageName) }
            trace += "feedback=${SessionState.feedback.value.title}"
            check(SessionState.feedback.value.success) { SessionState.feedback.value.title }
            passed = true
        } catch (failure: Exception) {
            trace += "failure=${failure.message}"
        } finally {
            runOnMainSync { recognizer?.cancel(); recognizer?.destroy() }
            writer?.interrupt()
            writeEnd?.close()
            audio?.close()
            source?.delete()
            output.putString("case", caseName)
            output.putString("trace", trace.joinToString("\n"))
            output.putBoolean("passed", passed)
            finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, output)
        }
    }
}
