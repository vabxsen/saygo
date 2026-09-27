package dev.saygo.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActionFeedback(val title: String = "Your next move, in a few words.", val success: Boolean = true, val sequence: Long = 0)

/** Session-only result. Raw audio, transcripts, and screen contents are never persisted. */
object SessionState {
    private val mutableFeedback = MutableStateFlow(ActionFeedback())
    val feedback = mutableFeedback.asStateFlow()
    fun report(message: String, success: Boolean = true) { mutableFeedback.value = ActionFeedback(message, success, mutableFeedback.value.sequence + 1) }
}

class Preferences(context: Context) {
    private val storage = context.getSharedPreferences("saygo_preferences", Context.MODE_PRIVATE)
    var speechConsent: Boolean
        get() = storage.getBoolean("speech_disclosure_v1", false)
        set(value) { storage.edit { putBoolean("speech_disclosure_v1", value) } }
    var microphonePermissionRequested: Boolean
        get() = storage.getBoolean("microphone_permission_requested", false)
        set(value) { storage.edit { putBoolean("microphone_permission_requested", value) } }
    var controlConsent: Boolean
        get() = storage.getBoolean("control_disclosure_v1", false)
        set(value) { storage.edit { putBoolean("control_disclosure_v1", value) } }
    var showBubble: Boolean
        get() = storage.getBoolean("show_bubble", true)
        set(value) { storage.edit { putBoolean("show_bubble", value) } }
    var spokenFeedback: Boolean
        get() = storage.getBoolean("spoken_feedback", false)
        set(value) { storage.edit { putBoolean("spoken_feedback", value) } }
}
