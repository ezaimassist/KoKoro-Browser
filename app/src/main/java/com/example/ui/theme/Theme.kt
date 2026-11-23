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
data class PulseSemanticColors(
    val okGreen: Color,
    val warnAmber: Color,
    val errorRose: Color,
    val privateViolet: Color,
    val privateContainer: Color,
    val border: Color,
    val hudOverlay: Color
)

val LocalPulseSemanticColors = staticCompositionLocalOf {
    PulseSemanticColors(
        okGreen = PulseEmerald,
        warnAmber = PulseAmber,
        errorRose = PulseRose,
        privateViolet = PulsePrivateViolet,
        privateContainer = PulsePrivateContainer,
        border = PulseBorder,
        hudOverlay = Color(0xDF0B0F19)
    )
}

private val PulseDarkColors = darkColorScheme(
    primary = PulseCyan,
    onPrimary = OnPulseCyan,
    primaryContainer = PulseCyanContainer,
    onPrimaryContainer = PulseCyan,
    secondary = PulseCobalt,
    onSecondary = OnPulseCobalt,
    secondaryContainer = PulseCobaltContainer,
    onSecondaryContainer = Color(0xFFDBE7FF),
    tertiary = PulseEmerald,
    onTertiary = Color(0xFF00220D),
    error = PulseRose,
    onError = Color.White,
    background = PulseVoid,
    onBackground = TextPrimaryDark,
    surface = PulseSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = PulseElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = PulseElevated,
    surfaceContainerHigh = PulseCard,
    outline = PulseBorder
)

private val PulseLightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF002026),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = Color(0xFF0A2463),
    tertiary = Color(0xFF15803D),
    onTertiary = Color.White,
    error = Color(0xFFBE123C),
    onError = Color.White,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder
)

private val PulseShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun PulseBrowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) PulseDarkColors else PulseLightColors
    val semantic = if (darkTheme) {
        PulseSemanticColors(
            okGreen = PulseEmerald,
            warnAmber = PulseAmber,
            errorRose = PulseRose,
            privateViolet = PulsePrivateViolet,
            privateContainer = PulsePrivateContainer,
            border = PulseBorder,
            hudOverlay = Color(0xE00B0F19)
        )
    } else {
        PulseSemanticColors(
            okGreen = Color(0xFF15803D),
            warnAmber = Color(0xFFB45309),
            errorRose = Color(0xFFBE123C),
            privateViolet = Color(0xFF7E22CE),
            privateContainer = Color(0xFFF3E8FF),
            border = LightBorder,
            hudOverlay = Color(0xE8111726)
        )
    }

    CompositionLocalProvider(LocalPulseSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PulseTypography,
            shapes = PulseShapes,
            content = content
        )
    }
}
