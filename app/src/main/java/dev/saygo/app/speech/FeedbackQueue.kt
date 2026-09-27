package dev.saygo.app.speech

/** Main-thread queue: only the latest result may speak once the engine is ready. */
internal class FeedbackQueue(
    private val enabled: () -> Boolean,
    private val speak: (String) -> Unit,
    private val stop: () -> Unit,
) {
    private var ready = false
    private var pending: String? = null

    fun offer(text: String) {
        if (!enabled()) return
        pending = text
        flush()
    }

    fun engineReady(success: Boolean) {
        ready = success
        if (success) flush() else pending = null
    }

    fun cancel() {
        pending = null
        stop()
    }

    private fun flush() {
        if (!ready) return
        val text = pending ?: return
        pending = null
        if (enabled()) speak(text)
    }
}
