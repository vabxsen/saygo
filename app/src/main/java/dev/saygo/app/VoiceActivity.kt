package dev.saygo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saygo.app.commands.CommandExecutor
import dev.saygo.app.control.PhoneControlService
import dev.saygo.app.data.Preferences
import dev.saygo.app.speech.SpeechSession
import dev.saygo.app.ui.SaygoTheme
import dev.saygo.app.ui.MicrophoneDisc

class VoiceActivity : ComponentActivity() {
    companion object { const val EXTRA_ORIGIN = "origin_package" }
    private var status by mutableStateOf("Getting ready…")
    private var listening by mutableStateOf(false)
    private var session: SpeechSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        (application as SaygoApplication).stopSpeaking()
        setContent {
            SaygoTheme {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .16f)).safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("saygo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = { session?.close(); finish() }) { Icon(Icons.Rounded.Close, "Cancel listening") }
                            }
                            MicrophoneDisc(Modifier.size(104.dp))
                            Text(status, style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            if (listening) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                                Text("One command at a time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Button(onClick = ::startSession, modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                                    Text("Try again")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (savedInstanceState == null) startSession() else status = "Tap to start a new listening session."
    }

    private fun startSession() {
        if (!Preferences(this).speechConsent || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listening = false
            status = "Set up microphone access on saygo’s Setup screen first."
            return
        }
        session?.close()
        listening = true
        status = "Getting ready…"
        session = SpeechSession(this,
            onStatus = { status = it },
            onError = { listening = false; status = it },
            onResult = { transcript ->
                listening = false
                val executor = CommandExecutor(this)
                val command = executor.parse(transcript)
                if (command == null) {
                    status = "That command isn’t supported yet. Try “Open YouTube”, “Go home”, or “Next reel”."
                } else {
                    executor.execute(command, intent.getStringExtra(EXTRA_ORIGIN))
                    finish()
                }
            },
        ).also { it.start() }
    }

    override fun onStart() { super.onStart(); PhoneControlService.voiceUiVisible = true }
    override fun onStop() {
        session?.close()
        listening = false
        PhoneControlService.voiceUiVisible = false
        if (!isChangingConfigurations && !isFinishing) finish()
        super.onStop()
    }
    override fun onDestroy() { session?.close(); super.onDestroy() }
}





