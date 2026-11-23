package com.example.ui.theme

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
data class VitRemoteExtendedColors(
    val cardBorder: Color,
    val cardElevated: Color,
    val onlineGreen: Color,
    val onlineGreenContainer: Color,
    val warningAmber: Color,
    val warningAmberContainer: Color,
    val dangerRed: Color,
    val dangerRedContainer: Color,
    val accentCyan: Color,
    val accentCyanContainer: Color,
    val subtleText: Color
)

val LocalVitRemoteColors = staticCompositionLocalOf {
    VitRemoteExtendedColors(
        cardBorder = SubtleBorderDark,
        cardElevated = ElevatedCharcoal,
        onlineGreen = ElectricEmerald,
        onlineGreenContainer = EmeraldContainerDark,
        warningAmber = AmberStatus,
        warningAmberContainer = AmberContainerDark,
        dangerRed = CrimsonAlert,
        dangerRedContainer = CrimsonContainerDark,
        accentCyan = CyanPrimaryDark,
        accentCyanContainer = CyanContainerDark,
        subtleText = CoolSlate
    )
}

private val VitRemoteDarkColorScheme = darkColorScheme(
    primary = ElectricEmerald,
    onPrimary = ObsidianBlack,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldGlow,
    secondary = CyanPrimaryDark,
    onSecondary = ObsidianBlack,
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = ElectricCyan,
    tertiary = AmberStatus,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = CrispWhite,
    surface = DeepCharcoal,
    onSurface = CrispWhite,
    surfaceVariant = ElevatedCharcoal,
    onSurfaceVariant = CoolSlate,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = DeepCharcoal,
    surfaceContainer = ElevatedCharcoal,
    surfaceContainerHigh = SurfaceCharcoal,
    outline = SubtleBorderDark,
    outlineVariant = Color(0xFF1F2430),
    error = CrimsonAlert,
    onError = CrispWhite,
    errorContainer = CrimsonContainerDark,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val VitRemoteLightColorScheme = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = Color(0xFF065F46),
    secondary = CyanPrimaryLight,
    onSecondary = Color.White,
    secondaryContainer = CyanContainerLight,
    onSecondaryContainer = Color(0xFF075985),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = AlabasterBg,
    onBackground = DeepInk,
    surface = PureWhiteCard,
    onSurface = DeepInk,
    surfaceVariant = SoftSlateSurface,
    onSurfaceVariant = SecondaryInk,
    surfaceContainerLowest = PureWhiteCard,
    surfaceContainerLow = AlabasterBg,
    surfaceContainer = PureWhiteCard,
    surfaceContainerHigh = SoftSlateSurface,
    outline = SubtleBorderLight,
    outlineVariant = Color(0xFFE5EAF2),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = CrimsonContainerLight,
    onErrorContainer = Color(0xFF991B1B)
)

private val VitRemoteShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VitRemoteTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VitRemoteDarkColorScheme else VitRemoteLightColorScheme
    val extendedColors = if (darkTheme) {
        VitRemoteExtendedColors(
            cardBorder = SubtleBorderDark,
            cardElevated = ElevatedCharcoal,
            onlineGreen = ElectricEmerald,
            onlineGreenContainer = EmeraldContainerDark,
            warningAmber = AmberStatus,
            warningAmberContainer = AmberContainerDark,
            dangerRed = CrimsonAlert,
            dangerRedContainer = CrimsonContainerDark,
            accentCyan = CyanPrimaryDark,
            accentCyanContainer = CyanContainerDark,
            subtleText = CoolSlate
        )
    } else {
        VitRemoteExtendedColors(
            cardBorder = SubtleBorderLight,
            cardElevated = PureWhiteCard,
            onlineGreen = Color(0xFF059669),
            onlineGreenContainer = EmeraldContainerLight,
            warningAmber = Color(0xFFD97706),
            warningAmberContainer = AmberContainerLight,
            dangerRed = Color(0xFFDC2626),
            dangerRedContainer = CrimsonContainerLight,
            accentCyan = CyanPrimaryLight,
            accentCyanContainer = CyanContainerLight,
            subtleText = SecondaryInk
        )
    }

    CompositionLocalProvider(LocalVitRemoteColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VitRemoteTypography,
            shapes = VitRemoteShapes,
            content = content
        )
    }
}
