package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class BareTelemetryColors(
    val fpsOptimal: Color,
    val gpuAccent: Color,
    val thermalWarn: Color,
    val criticalPulse: Color,
    val cardBorder: Color,
    val hudBackground: Color
)

val LocalTelemetryColors = staticCompositionLocalOf {
    BareTelemetryColors(
        fpsOptimal = VoltLime,
        gpuAccent = CyberCyan,
        thermalWarn = SignalAmber,
        criticalPulse = CrimsonPulse,
        cardBorder = CarbonBorder,
        hudBackground = Color(0xD90A0C10)
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = VoltLime,
    onPrimary = OnVoltLime,
    primaryContainer = VoltLimeContainer,
    onPrimaryContainer = VoltLime,
    secondary = CyberCyan,
    onSecondary = OnCyberCyan,
    secondaryContainer = CyberCyanContainer,
    onSecondaryContainer = CyberCyan,
    tertiary = SignalAmber,
    onTertiary = OnSignalAmber,
    tertiaryContainer = SignalAmberContainer,
    onTertiaryContainer = SignalAmber,
    error = CrimsonPulse,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonPulse,
    background = CarbonVoid,
    onBackground = TextPrimaryDark,
    surface = CarbonSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CarbonElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = CarbonVoid,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = CarbonElevated,
    surfaceContainerHigh = CarbonCard,
    surfaceContainerHighest = Color(0xFF242D40),
    outline = CarbonBorder,
    outlineVariant = Color(0xFF1F2636)
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = OnLightPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF163200),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = Color(0xFF00242B),
    tertiary = Color(0xFF9A5B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE5BE),
    onTertiaryContainer = Color(0xFF331C00),
    error = Color(0xFFC81E3D),
    onError = Color.White,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = Color(0xFFE2E8F0)
)

private val BareShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun BareTurboTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val telemetryColors = if (darkTheme) {
        BareTelemetryColors(
            fpsOptimal = VoltLime,
            gpuAccent = CyberCyan,
            thermalWarn = SignalAmber,
            criticalPulse = CrimsonPulse,
            cardBorder = CarbonBorder,
            hudBackground = Color(0xDE0A0C10)
        )
    } else {
        BareTelemetryColors(
            fpsOptimal = LightPrimary,
            gpuAccent = LightSecondary,
            thermalWarn = Color(0xFFB45309),
            criticalPulse = Color(0xFFC81E3D),
            cardBorder = LightBorder,
            hudBackground = Color(0xE810141C)
        )
    }

    CompositionLocalProvider(LocalTelemetryColors provides telemetryColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = BareShapes,
            content = content
        )
    }
}
