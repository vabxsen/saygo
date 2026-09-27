package dev.saygo.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saygo.app.control.PhoneControlService
import dev.saygo.app.data.Preferences
import dev.saygo.app.data.SessionState

private val appCommands = listOf(
    CommandExample("Open Instagram", "Open an installed app by name"),
    CommandExample("Open YouTube", "Launch an installed app"),
    CommandExample("Search Google for coffee nearby", "Search the web"),
    CommandExample("Search YouTube for cooking", "Find videos"),
)
private val controlCommands = listOf(
    CommandExample("Show grid", "Number unlabelled areas of the screen"),
    CommandExample("Zoom cell 5", "Refine the grid inside one numbered cell"),
    CommandExample("Tap cell 5", "Tap the centre of a numbered cell"),
    CommandExample("Long press cell 5", "Touch and hold a numbered cell"),
    CommandExample("Drag cell 1 to cell 9", "Hold, move and release between two grid cells"),
    CommandExample("Grid back / Hide grid", "Undo a zoom or close the grid"),
    CommandExample("Tap Search", "Tap one control by its exact label"),
    CommandExample("Long press a label", "Touch and hold a named control"),
    CommandExample("Type your words", "Insert text in the focused field"),
    CommandExample("Replace text with your words", "Replace the focused field’s contents"),
    CommandExample("Clear text", "Empty the focused field"),
    CommandExample("Select all", "Select text in the focused field"),
    CommandExample("Next reel", "One upward swipe · also “scroll down”"),
    CommandExample("Previous reel", "One downward swipe · also “scroll up”"),
    CommandExample("Swipe left / Swipe right", "One horizontal swipe"),
    CommandExample("Zoom in / Zoom out", "One two-finger gesture at the centre of the app"),
    CommandExample("Go back", "Return to the previous screen"),
    CommandExample("Go home", "Return to your home screen"),
    CommandExample("Recent apps", "Show recent apps"),
    CommandExample("Open notifications", "Show Android’s notification shade"),
    CommandExample("Open quick settings", "Show Android’s device controls"),
    CommandExample("Volume up / Volume down", "Change media volume by one step"),
    CommandExample("Mute media / Unmute media", "Silence or restore media audio"),
)

@Composable
fun SaygoApp(
    microphoneGranted: Boolean,
    preferences: Preferences,
    onSpeak: () -> Unit,
    onAccessibilitySettings: () -> Unit,
    onAppSettings: () -> Unit,
    onBubbleChanged: () -> Unit,
    onSpokenFeedbackChanged: (Boolean) -> Unit,
    onDisableControls: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var speechDisclosure by rememberSaveable { mutableStateOf(false) }
    var controlDisclosure by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var bubble by remember { mutableStateOf(preferences.showBubble) }
    var spoken by remember { mutableStateOf(preferences.spokenFeedback) }
    val connected by PhoneControlService.connected.collectAsStateWithLifecycle()
    val feedback by SessionState.feedback.collectAsStateWithLifecycle()
    val speak = { if (!preferences.speechConsent) speechDisclosure = true else onSpeak() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SaygoNavigation(tab) { tab = it } } },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 16.dp)) {
                BrandHeader { tab = 2 }
            }
            key(tab) {
                BoxWithConstraints(Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f)) {
                    val viewportHeight = maxHeight
                    if (tab == 0) {
                        HomeContent(microphoneGranted, speak, { tab = 1 },
                            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewportHeight)
                                .padding(horizontal = 26.dp).padding(top = 16.dp, bottom = 24.dp),
                            feedback = if (feedback.sequence > 0) feedback.title else null,
                            feedbackSuccess = feedback.success)
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 26.dp).padding(top = 16.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(32.dp)) {
                            when (tab) {
                                1 -> {
                                    PageHeading("Voice commands", "Say one command at a time.")
                                    Column {
                                        SectionLabel("Apps and search")
                                        Spacer(Modifier.height(10.dp))
                                        appCommands.forEachIndexed { index, example ->
                                            CommandRow(example)
                                            if (index < appCommands.lastIndex) Rule()
                                        }
                                    }
                                    Text("Say “Open” followed by the full name of an installed app.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Column {
                                        SectionLabel("Phone controls")
                                        Spacer(Modifier.height(12.dp))
                                        Text("Enable phone controls in Setup. Use the floating microphone over the app you want to control.",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(10.dp))
                                        controlCommands.forEachIndexed { index, example ->
                                            CommandRow(example)
                                            if (index < controlCommands.lastIndex) Rule()
                                        }
                                    }
                                    Column {
                                        SectionLabel("Stop listening")
                                        CommandRow(CommandExample("Cancel", "End listening without an action"))
                                    }
                                    Text("One command at a time. Tap a field before dictating. Labels must match exactly and be unique. Use Show grid for unlabelled controls, then name a cell. The grid expires after a minute and closes when you scroll or change apps. Drag between two cells of the current grid in apps that support dragging. Zoom in and Zoom out work in apps that support pinch gestures. Multi-step actions aren’t supported.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                2 -> {
                                    PageHeading("Setup", "Permissions and preferences")
                                    Column {
                                        SectionLabel("Permissions")
                                        Spacer(Modifier.height(8.dp))
                                        ActionRow("Microphone", "Only listens when you tap",
                                            if (microphoneGranted) "App settings" else "Set up microphone",
                                            if (microphoneGranted) onAppSettings else speak,
                                            if (microphoneGranted) "Enabled" else "Enable")
                                        Rule()
                                        ActionRow("Phone controls", "Optional taps, typing and phone controls",
                                            if (connected) "Review access" else "Set up phone controls",
                                            { controlDisclosure = true }, if (connected) "Enabled" else "Enable")
                                    }
                                    Column {
                                        SectionLabel("Preferences")
                                        Spacer(Modifier.height(8.dp))
                                        SettingSwitch("Floating microphone", "Requires phone controls", bubble) {
                                            bubble = it; preferences.showBubble = it; onBubbleChanged()
                                        }
                                        Rule()
                                        SettingSwitch("Spoken feedback", "Hear action results aloud", spoken) {
                                            spoken = it; preferences.spokenFeedback = it; onSpokenFeedbackChanged(it)
                                        }
                                    }
                                    Column {
                                        SectionLabel("About")
                                        ActionRow("Privacy", "How your data is handled", "Open privacy", { privacy = true })
                                    }
                                    if (connected) {
                                        OutlinedButton(onClick = { preferences.controlConsent = false; onDisableControls() }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Turn off phone controls")
                                        }
                                    }
                                    Rule()
                                    Text("saygo 0.8.0 · English · Android 10+", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (speechDisclosure) AlertDialog(
        onDismissRequest = { speechDisclosure = false },
        icon = { Icon(Icons.Rounded.MicNone, null) }, title = { Text("Microphone access") },
        text = { Text("saygo listens only when you tap the microphone, for up to 15 seconds. It uses on-device recognition when available. Otherwise, your selected Android speech provider may send audio to its servers under its own privacy policy. saygo does not record or save your audio or transcript. Search commands send your query to Google or YouTube. Continue to Android’s microphone permission prompt.", Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { preferences.speechConsent = true; speechDisclosure = false; onSpeak() }) { Text("Agree & continue") } },
        dismissButton = { TextButton(onClick = { speechDisclosure = false }) { Text("Not now") } },
    )
    if (controlDisclosure) AlertDialog(
        onDismissRequest = { controlDisclosure = false },
        icon = { Icon(Icons.Rounded.TouchApp, null) }, title = { Text("Control your phone by voice") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("saygo uses Android’s AccessibilityService to display a floating microphone and an optional numbered grid, and carry out your explicit tap, long-press, text-editing, swipe, drag, pinch-zoom, Back, Home, Recent apps, notification shade, Quick Settings and media volume commands.")
            Text("Android grants access to screen content and interactions. saygo reads the foreground app identity, window bounds, control labels and focused text field when you request a command. This lets it find the button or field you name. Screen content stays on your device and is not saved or sent to saygo servers. Text you dictate is entered into the other app, which handles it under its own privacy policy.")
            Text("One command triggers one predefined action. saygo never chooses or runs a sequence of actions for you. This permission is optional; opening apps and searching work without it. You can turn it off at any time in Setup or Android accessibility settings.")
        } },
        confirmButton = { TextButton(onClick = { preferences.controlConsent = true; controlDisclosure = false; onAccessibilitySettings() }) { Text("Agree & open settings") } },
        dismissButton = { TextButton(onClick = { controlDisclosure = false }) { Text("Not now") } },
    )
    if (privacy) AlertDialog(
        onDismissRequest = { privacy = false }, title = { Text("Your voice, your privacy") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("No account, ads, analytics, or saygo server. We don’t save recordings, transcripts, screen contents, or command history. Preferences and disclosure choices stay on this device; Android backup is disabled.")
            Text("Speech uses an on-device recognizer when available. Otherwise your Android speech provider may process audio online. Google and YouTube receive queries you explicitly ask to search. Their own privacy policies apply.")
            Text("App names are matched locally. Optional phone controls read app identity, window bounds, control labels and the focused text field locally to carry out your command. Dictated text is entered into the app you control. Spoken feedback uses your Android text-to-speech engine.")
            Text("Revoke microphone access in Android app settings, disable phone controls here, or clear app storage to reset your preferences and consent.")
        } },
        confirmButton = { TextButton(onClick = { privacy = false }) { Text("Got it") } },
    )
}
