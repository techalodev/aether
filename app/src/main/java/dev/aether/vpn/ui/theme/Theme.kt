package dev.aether.vpn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.aether.vpn.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF5B4FC8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4DFFF),
    onPrimaryContainer = Color(0xFF170065),
    secondary = Color(0xFF5E5C71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4DFF9),
    onSecondaryContainer = Color(0xFF1B192C),
    tertiary = Color(0xFF7A5368),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8EA),
    onTertiaryContainer = Color(0xFF2F1124),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE5E0EC),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2FA),
    surfaceContainer = Color(0xFFF0ECF4),
    surfaceContainerHigh = Color(0xFFEBE6EE),
    surfaceContainerHighest = Color(0xFFE5E1E9),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC8C5D0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC6BFFF),
    onPrimary = Color(0xFF2D1D9B),
    primaryContainer = Color(0xFF4436AF),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFC8C3DC),
    onSecondary = Color(0xFF302E41),
    secondaryContainer = Color(0xFF474459),
    onSecondaryContainer = Color(0xFFE4DFF9),
    tertiary = Color(0xFFEAB9D2),
    onTertiary = Color(0xFF472639),
    tertiaryContainer = Color(0xFF603B50),
    onTertiaryContainer = Color(0xFFFFD8EA),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1C1B20),
    surfaceContainer = Color(0xFF201F25),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF35343A),
    outline = Color(0xFF928F9A),
    outlineVariant = Color(0xFF47464F),
)

@Composable
fun AetherTheme(mode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = AetherTypography,
        content = content,
    )
}
