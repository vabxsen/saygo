package dev.saygo.app.control

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import dev.saygo.app.commands.DeviceAction
import dev.saygo.app.data.SessionState

/** Global controls run only after the service validates consent and the unlocked screen. */
internal object DeviceControls {
    /** Returns whether Android accepted a panel request; volume changes are never repeated. */
    fun execute(service: AccessibilityService, action: DeviceAction): Boolean {
        return try {
            when (action) {
                DeviceAction.NOTIFICATIONS, DeviceAction.QUICK_SETTINGS -> {
                    val notifications = action == DeviceAction.NOTIFICATIONS
                    val accepted = service.performGlobalAction(if (notifications)
                        AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                    else AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                    val label = if (notifications) "Notifications" else "Quick Settings"
                    SessionState.report(if (accepted) "$label requested." else "$label isn’t available here.", accepted)
                    accepted
                }
                else -> { adjustMedia(service.getSystemService(AudioManager::class.java), action); false }
            }
        } catch (_: SecurityException) {
            SessionState.report("Android blocked this control. Check your device’s settings.", false)
            false
        }
    }

    private fun adjustMedia(audio: AudioManager, action: DeviceAction) {
        if (audio.isVolumeFixed) {
            SessionState.report("This device uses fixed volume. Use the connected device’s controls.", false)
            return
        }
        val stream = AudioManager.STREAM_MUSIC
        val volume = audio.getStreamVolume(stream)
        val muted = audio.isStreamMute(stream)
        val direction: Int
        val message: String
        when (action) {
            DeviceAction.MEDIA_UP -> {
                if (!muted && volume >= audio.getStreamMaxVolume(stream)) {
                    SessionState.report("Media volume is already at maximum."); return
                }
                direction = AudioManager.ADJUST_RAISE; message = "Media volume up requested."
            }
            DeviceAction.MEDIA_DOWN -> {
                if (volume <= audio.getStreamMinVolume(stream)) {
                    SessionState.report("Media volume is already at minimum."); return
                }
                direction = AudioManager.ADJUST_LOWER; message = "Media volume down requested."
            }
            DeviceAction.MEDIA_MUTE -> {
                if (muted) { SessionState.report("Media is already muted."); return }
                direction = AudioManager.ADJUST_MUTE; message = "Media mute requested."
            }
            DeviceAction.MEDIA_UNMUTE -> {
                if (!muted) { SessionState.report("Media is already unmuted."); return }
                direction = AudioManager.ADJUST_UNMUTE; message = "Media unmute requested."
            }
            else -> return
        }
        // Do not change ring/alarm streams or request Do Not Disturb access.
        audio.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
        // Audio routing and device policy can affect the eventual volume. Do not claim
        // audible output solely because Android accepted this synchronous request.
        SessionState.report(message)
    }
}
