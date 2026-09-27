package dev.saygo.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class CommandExample(val phrase: String, val detail: String)

@Composable
internal fun BrandHeader(onSetup: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("saygo", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f))
        IconButton(onClick = onSetup) {
            Icon(Icons.Outlined.Settings, "Open setup", Modifier.size(27.dp))
        }
    }
}

@Composable
internal fun SaygoNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.5f
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            Rule()
            Row(Modifier.navigationBarsPadding().widthIn(max = 560.dp).fillMaxWidth()
                .selectableGroup().padding(horizontal = 12.dp, vertical = 12.dp)) {
                listOf("Home", "Commands", "Setup").forEachIndexed { index, label ->
                    val active = index == selected
                    val icon = when (index) {
                        0 -> if (active) Icons.Rounded.Home else Icons.Outlined.Home
                        1 -> Icons.AutoMirrored.Rounded.List
                        else -> Icons.Outlined.Settings
                    }
                    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(Modifier.weight(if (largeText && index == 1) 1.6f else 1f).clip(RoundedCornerShape(12.dp))
                        .selectable(active, role = Role.Tab, onClick = { onSelect(index) })
                        .heightIn(min = 60.dp).padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
                        Icon(icon, null, Modifier.size(27.dp), tint = tint)
                        Text(label, style = MaterialTheme.typography.labelMedium, color = tint,
                            textAlign = TextAlign.Center, softWrap = false, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MicrophoneDisc(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, tween(120), label = "microphonePress")
    val disc = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
    val action = if (onClick == null) disc else disc.clickable(interactionSource = interaction,
        indication = ripple(), role = Role.Button, onClick = onClick)
        .semantics { contentDescription = "Tap to speak a command" }
    Box(action.padding(11.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().border(3.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = .28f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Mic, null, Modifier.fillMaxSize(.50f), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
internal fun VoiceAction(ready: Boolean, onSpeak: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        MicrophoneDisc(Modifier.size(172.dp), onSpeak)
        Spacer(Modifier.height(16.dp))
        Text("Tap to speak", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(5.dp))
        Text(if (ready) "Listening starts when you tap." else "Tap to enable microphone access.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

@Composable
internal fun HomeContent(ready: Boolean, onSpeak: () -> Unit, onCommands: () -> Unit,
    modifier: Modifier = Modifier, feedback: String? = null, feedbackSuccess: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("What shall\nwe do?", style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(10.dp))
            Text("Your next move, just a word away.", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(32.dp))
        VoiceAction(ready, onSpeak)
        Spacer(Modifier.height(36.dp))
        Column {
            SectionLabel("Try saying")
            Spacer(Modifier.height(14.dp))
            HomeCommandRow("Open YouTube", Icons.Outlined.SmartDisplay, onCommands)
            Rule()
            HomeCommandRow("Search Google for coffee", Icons.Rounded.Search, onCommands)
            if (feedback != null) {
                Text(feedback, Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (feedbackSuccess) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun HomeCommandRow(phrase: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
        .clickable(role = Role.Button, onClickLabel = "View command guide", onClick = onClick)
        .heightIn(min = 74.dp).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(29.dp))
        Text(phrase, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
internal fun CommandRow(example: CommandExample) {
    Column(Modifier.fillMaxWidth().padding(vertical = 17.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("“" + example.phrase + "”", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(example.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ActionRow(title: String, detail: String, actionLabel: String, onClick: () -> Unit, status: String? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick)
            .semantics { contentDescription = actionLabel }.padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (status != null) Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SettingSwitch(title: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, null, colors = SwitchDefaults.colors(
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline))
    }
}

@Composable
internal fun Rule() { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
