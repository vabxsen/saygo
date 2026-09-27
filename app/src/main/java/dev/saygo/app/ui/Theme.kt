package dev.saygo.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF084BDD), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF0FF), onPrimaryContainer = Color(0xFF113275),
    secondary = Color(0xFF084BDD), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF0FF), onSecondaryContainer = Color(0xFF113275),
    background = Color(0xFFFDFDFD), onBackground = Color(0xFF111725),
    surface = Color(0xFFFDFDFD), onSurface = Color(0xFF111725),
    surfaceVariant = Color(0xFFE9EDF5), onSurfaceVariant = Color(0xFF626773),
    outline = Color(0xFF7C8495), outlineVariant = Color(0xFFE7E8ED),
    surfaceTint = Color(0xFF084BDD),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F8FC),
    surfaceContainer = Color(0xFFF0F3FA), surfaceContainerHigh = Color(0xFFE9EEF8),
    surfaceContainerHighest = Color(0xFFE0E7F3),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA9C2FF), onPrimary = Color(0xFF002976),
    primaryContainer = Color(0xFF153978), onPrimaryContainer = Color(0xFFDAE5FF),
    secondary = Color(0xFFA9C2FF), onSecondary = Color(0xFF002976),
    secondaryContainer = Color(0xFF153978), onSecondaryContainer = Color(0xFFDAE5FF),
    background = Color(0xFF10141E), onBackground = Color(0xFFF2F5FC),
    surface = Color(0xFF10141E), onSurface = Color(0xFFF2F5FC),
    surfaceVariant = Color(0xFF2A3245), onSurfaceVariant = Color(0xFFB7BECD),
    outline = Color(0xFF8792A9), outlineVariant = Color(0xFF2C3547),
    surfaceTint = Color(0xFFA9C2FF),
    surfaceContainerLowest = Color(0xFF131925), surfaceContainerLow = Color(0xFF18202F),
    surfaceContainer = Color(0xFF1E2738), surfaceContainerHigh = Color(0xFF263146),
    surfaceContainerHighest = Color(0xFF303E55),
)
private val Type = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 46.sp, letterSpacing = (-1.6).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-.5).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 34.sp, letterSpacing = (-1.4).sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
)
@Composable
fun SaygoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type, content = content)
}
