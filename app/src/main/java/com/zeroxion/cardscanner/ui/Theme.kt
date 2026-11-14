package com.zeroxion.cardscanner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Gold = Color(0xFFFFD23F)
val Violet = Color(0xFF8B7BFF)
val Mint = Color(0xFF3DE0C2)
val Night = Color(0xFF0A0C16)

val HeroBrush = Brush.linearGradient(
    listOf(Color(0xFF6A4BFF), Color(0xFF3566FF), Color(0xFF14B8A6)),
)

private val Scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF231A00),
    primaryContainer = Color(0xFF3A2F00),
    onPrimaryContainer = Color(0xFFFFE58A),
    secondary = Violet,
    onSecondary = Color(0xFF0F0A33),
    secondaryContainer = Color(0xFF2A2466),
    onSecondaryContainer = Color(0xFFDAD5FF),
    tertiary = Mint,
    onTertiary = Color(0xFF00201A),
    background = Night,
    onBackground = Color(0xFFECEEF8),
    surface = Night,
    onSurface = Color(0xFFECEEF8),
    surfaceVariant = Color(0xFF1B2036),
    onSurfaceVariant = Color(0xFFA9B0CC),
    surfaceContainerLowest = Color(0xFF070910),
    surfaceContainerLow = Color(0xFF10131F),
    surfaceContainer = Color(0xFF141829),
    surfaceContainerHigh = Color(0xFF1B2036),
    surfaceContainerHighest = Color(0xFF232945),
    outline = Color(0xFF3A4163),
    outlineVariant = Color(0xFF262C48),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF2B0008),
)

private val Base = Typography()

private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp),
)

@Composable
fun CardscannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
