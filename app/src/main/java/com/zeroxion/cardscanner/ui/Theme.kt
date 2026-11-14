package com.zeroxion.cardscanner.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

// ---------------------------------------------------------------- Farben der Energie-Typen

/** Elektro – Hauptfarbe für Knöpfe, Preise und den Scan-Rahmen. */
val Electric = Color(0xFFFFD23F)
val Fire = Color(0xFFFF5D5D)
val Water = Color(0xFF4DA3FF)
val Grass = Color(0xFF4ADE80)
val Psychic = Color(0xFFC084FC)

val Gold = Electric
val Violet = Psychic
val Mint = Grass
val Night = Color(0xFF060918)

/** Wert-Kachel: Feuer-Rot über Psycho-Magenta zu Wasser-Indigo. */
val HeroBrush = Brush.linearGradient(
    listOf(Color(0xFFE63946), Color(0xFFB5179E), Color(0xFF4F46E5)),
)

private val typeBrushes = listOf(
    Brush.linearGradient(listOf(Color(0xFFFF8A4C), Color(0xFFE63946))), // Feuer
    Brush.linearGradient(listOf(Color(0xFF4DA3FF), Color(0xFF3346E0))), // Wasser
    Brush.linearGradient(listOf(Color(0xFF4ADE80), Color(0xFF0F9F6E))), // Pflanze
    Brush.linearGradient(listOf(Color(0xFFFFD23F), Color(0xFFF08A00))), // Elektro
    Brush.linearGradient(listOf(Color(0xFFE879F9), Color(0xFF7C3AED))), // Psycho
)

/** Fester Energie-Farbverlauf je Schlüssel, z. B. für Binder ohne Coverfoto. */
fun typeBrushFor(key: String): Brush = typeBrushes[Math.floorMod(key.hashCode(), typeBrushes.size)]

// ---------------------------------------------------------------- Glas

val GlassBorder = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.18f)),
)

/**
 * Milchglas-Fläche: leicht aufgehellt, mit hellem Rand oben links und dunklerem unten rechts.
 * [strength] > 1 macht das Glas deckender (z. B. über unruhigem Inhalt).
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    strength: Float = 1f,
    tint: Color = Color.White,
): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(tint.copy(alpha = 0.12f * strength), tint.copy(alpha = 0.045f * strength)),
        ),
        shape,
    )
    .border(1.dp, GlassBorder, shape)

/** Deckendes Glas über scrollendem Inhalt, wo kein echter Weichzeichner möglich ist. */
fun Modifier.frostedPanel(shape: Shape): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(listOf(Color(0xE6141A3A), Color(0xF20A0E24))),
        shape,
    )
    .border(1.dp, GlassBorder, shape)

/** Echte Unschärfe hinter der Navigationsleiste (ab Android 12, sonst getöntes Glas). */
val GlassHaze = HazeStyle(
    backgroundColor = Night,
    tints = listOf(HazeTint(Color(0xFF141A3A).copy(alpha = 0.55f))),
    blurRadius = 28.dp,
    noiseFactor = 0.08f,
)

/** Langsam über die Fläche wandernder Glanz wie bei einer Holo-Karte. */
@Composable
fun Modifier.holoShimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "holo")
    val progress by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "holoProgress",
    )
    return this.drawWithContent {
        drawContent()
        val x = size.width * progress
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                start = Offset(x - size.width * 0.35f, 0f),
                end = Offset(x + size.width * 0.15f, size.height),
            ),
        )
    }
}

/** Dunkler Hintergrund mit weichen Energie-Farbflecken – darüber liegen die Glasflächen. */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0C1235), Night, Color(0xFF070A1C))))
        val r = maxOf(size.width, size.height) * 0.55f
        glow(Fire, Offset(size.width * -0.05f, size.height * 0.04f), r, 0.34f)
        glow(Water, Offset(size.width * 1.05f, size.height * 0.30f), r, 0.28f)
        glow(Psychic, Offset(size.width * 0.05f, size.height * 0.72f), r * 0.9f, 0.22f)
        glow(Electric, Offset(size.width * 0.95f, size.height * 1.02f), r * 0.8f, 0.18f)
    }
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius),
        radius = radius,
        center = center,
    )
}

// ---------------------------------------------------------------- Bausteine im Glas-Stil

@Composable
fun glassFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
    focusedContainerColor = Color.White.copy(alpha = 0.10f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
    focusedBorderColor = Electric,
)

@Composable
fun glassChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(alpha = 0.07f),
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = Electric,
    selectedLabelColor = Color(0xFF231A00),
)

@Composable
fun glassChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = Color.White.copy(alpha = 0.16f),
    selectedBorderColor = Color.Transparent,
)

// ---------------------------------------------------------------- Theme

private val Scheme = darkColorScheme(
    primary = Electric,
    onPrimary = Color(0xFF231A00),
    primaryContainer = Color(0xFF3A2F00),
    onPrimaryContainer = Color(0xFFFFE58A),
    secondary = Water,
    onSecondary = Color(0xFF001A3A),
    secondaryContainer = Color(0xFF1C2E66),
    onSecondaryContainer = Color(0xFFD6E6FF),
    tertiary = Grass,
    onTertiary = Color(0xFF00210F),
    background = Night,
    onBackground = Color(0xFFF1F3FF),
    surface = Night,
    onSurface = Color(0xFFF1F3FF),
    surfaceVariant = Color(0xFF1C2350),
    onSurfaceVariant = Color(0xFFAEB6DA),
    surfaceContainerLowest = Color(0xFF05071A),
    surfaceContainerLow = Color(0xFF0D1230),
    surfaceContainer = Color(0xFF121838),
    surfaceContainerHigh = Color(0xFF1A2147),
    surfaceContainerHighest = Color(0xFF232B57),
    outline = Color(0xFF3D4678),
    outlineVariant = Color(0xFF262E58),
    error = Fire,
    onError = Color(0xFF2B0008),
)

private val Base = Typography()

private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp),
)

@Composable
fun CardscannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
