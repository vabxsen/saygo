package dev.saygo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.saygo.app.control.PhoneControlService
import dev.saygo.app.data.Preferences
import dev.saygo.app.ui.SaygoApp
import dev.saygo.app.ui.SaygoTheme

class MainActivity : ComponentActivity() {
    private var microphoneGranted by mutableStateOf(false)
    private val requestMicrophone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneGranted = granted
        if (granted) openVoice()
        else dev.saygo.app.data.SessionState.report("Microphone permission is off. You can enable it in Android app settings.", false)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaygoTheme {
                SaygoApp(
                    microphoneGranted = microphoneGranted,
                    preferences = Preferences(this),
                    onSpeak = ::speakOrRequestPermission,
                    onAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onAppSettings = ::openAppSettings,
                    onBubbleChanged = { PhoneControlService.current?.refreshBubble() },
                    onSpokenFeedbackChanged = { if (!it) (application as SaygoApplication).stopSpeaking() },
                    onDisableControls = { PhoneControlService.current?.disableSelf() },
                )
            }
        }
    }
    override fun onResume() {
        super.onResume()
        microphoneGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    }

    private fun speakOrRequestPermission() {
        val prefs = Preferences(this)
        when {
            microphoneGranted -> openVoice()
            prefs.microphonePermissionRequested && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                dev.saygo.app.data.SessionState.report("Allow microphone access in Permissions, then return to saygo.", false)
                openAppSettings()
            }
            else -> {
                prefs.microphonePermissionRequested = true
                requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun openVoice() {
        startActivity(Intent(this, VoiceActivity::class.java).putExtra(VoiceActivity.EXTRA_ORIGIN, packageName))
    }
}
