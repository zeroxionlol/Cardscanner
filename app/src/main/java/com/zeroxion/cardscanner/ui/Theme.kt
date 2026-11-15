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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.compositeOver
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

val Fire = Color(0xFFFF5D5D)
val Water = Color(0xFF4DA3FF)
val Grass = Color(0xFF4ADE80)
val Psychic = Color(0xFFC084FC)

val Night = Color(0xFF060918)

/** Dunkle Schrift auf farbigen Akzentflächen (Nachtblau statt Schwarz). */
val Ink = Color(0xFF0C1235)

/**
 * Farbwelten zum Umschalten. Jede hat eine Akzentfarbe (Knöpfe, Preise, Scan-Rahmen),
 * einen Verlauf für die Wert-Kachel und eigene Farbflecken im Hintergrund.
 */
enum class TypeTheme(
    val key: String,
    val label: String,
    val accent: Color,
    val accentLight: Color,
    val hero: List<Color>,
    val glows: List<Color>,
    val backgroundTop: Color,
) {
    CLASSIC(
        "classic", "Klassik",
        accent = Color(0xFFFFD23F), accentLight = Color(0xFFFFE27A),
        hero = listOf(Color(0xFFE63946), Color(0xFFB5179E), Color(0xFF4F46E5)),
        glows = listOf(Fire, Water, Psychic, Color(0xFFFFD23F)),
        backgroundTop = Color(0xFF0C1235),
    ),
    FIRE(
        "fire", "Feuer",
        accent = Color(0xFFFF8A4C), accentLight = Color(0xFFFFB98F),
        hero = listOf(Color(0xFFB91C3C), Color(0xFFE5482F), Color(0xFFF07A22)),
        glows = listOf(Color(0xFFFF4D4D), Color(0xFFFF8A3D), Color(0xFFE11D74), Color(0xFFFFC23D)),
        backgroundTop = Color(0xFF2A0D16),
    ),
    WATER(
        "water", "Wasser",
        accent = Color(0xFF5CB2FF), accentLight = Color(0xFFA6D4FF),
        hero = listOf(Color(0xFF0284C7), Color(0xFF2563EB), Color(0xFF4338CA)),
        glows = listOf(Color(0xFF3B82F6), Color(0xFF22D3EE), Color(0xFF6366F1), Color(0xFF2DD4BF)),
        backgroundTop = Color(0xFF0A1A3D),
    ),
    GRASS(
        "grass", "Pflanze",
        accent = Color(0xFF5BE38E), accentLight = Color(0xFFA7F3C0),
        hero = listOf(Color(0xFF15803D), Color(0xFF059669), Color(0xFF0F766E)),
        glows = listOf(Color(0xFF22C55E), Color(0xFFA3E635), Color(0xFF14B8A6), Color(0xFFFACC15)),
        backgroundTop = Color(0xFF082419),
    ),
    ELECTRIC(
        "electric", "Elektro",
        accent = Color(0xFFFFD23F), accentLight = Color(0xFFFFE89A),
        hero = listOf(Color(0xFFCA8A04), Color(0xFFD97706), Color(0xFFC2410C)),
        glows = listOf(Color(0xFFFFD23F), Color(0xFFF59E0B), Color(0xFFFDE047), Color(0xFF60A5FA)),
        backgroundTop = Color(0xFF231B05),
    ),
    PSYCHIC(
        "psychic", "Psycho",
        accent = Color(0xFFEE8CFA), accentLight = Color(0xFFF7C5FD),
        hero = listOf(Color(0xFFC026D3), Color(0xFF9333EA), Color(0xFF4F46E5)),
        glows = listOf(Color(0xFFD946EF), Color(0xFF8B5CF6), Color(0xFFF472B6), Color(0xFF6366F1)),
        backgroundTop = Color(0xFF1D0B33),
    ),
    ;

    val heroBrush: Brush get() = Brush.linearGradient(hero)
    val accentBrush: Brush get() = Brush.verticalGradient(listOf(accentLight, accent))

    companion object {
        fun of(key: String?): TypeTheme = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

val LocalTypeTheme = staticCompositionLocalOf { TypeTheme.CLASSIC }

/** Akzentfarbe der gewählten Farbwelt (Knöpfe, Preise, Scan-Rahmen). */
val Gold: Color
    @Composable @ReadOnlyComposable get() = LocalTypeTheme.current.accent
val Electric: Color
    @Composable @ReadOnlyComposable get() = LocalTypeTheme.current.accent

/** Verlauf von Akzent-hell nach Akzent – für Pillen und den aktiven Tab. */
val AccentBrush: Brush
    @Composable @ReadOnlyComposable get() = LocalTypeTheme.current.accentBrush

/** Verlauf der Wert-Kachel in der gewählten Farbwelt. */
val HeroBrush: Brush
    @Composable @ReadOnlyComposable get() = LocalTypeTheme.current.heroBrush

val Violet = Psychic
val Mint = Grass

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
    val theme = LocalTypeTheme.current
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(theme.backgroundTop, Night, Color(0xFF070A1C))))
        val r = maxOf(size.width, size.height) * 0.55f
        val (a, b, c, d) = theme.glows
        glow(a, Offset(size.width * -0.05f, size.height * 0.04f), r, 0.34f)
        glow(b, Offset(size.width * 1.05f, size.height * 0.30f), r, 0.28f)
        glow(c, Offset(size.width * 0.05f, size.height * 0.72f), r * 0.9f, 0.22f)
        glow(d, Offset(size.width * 0.95f, size.height * 1.02f), r * 0.8f, 0.18f)
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
    selectedLabelColor = Ink,
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
    primary = TypeTheme.CLASSIC.accent,
    onPrimary = Ink,
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
fun CardscannerTheme(theme: TypeTheme = TypeTheme.CLASSIC, content: @Composable () -> Unit) {
    val scheme = remember(theme) {
        Scheme.copy(
            primary = theme.accent,
            onPrimary = Ink,
            primaryContainer = theme.accent.copy(alpha = 0.22f).compositeOver(Night),
            onPrimaryContainer = theme.accentLight,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography) {
        // Helle Grundschrift überall – auch in Flächen ohne eigene Hintergrundfarbe.
        CompositionLocalProvider(
            LocalContentColor provides Scheme.onBackground,
            LocalTypeTheme provides theme,
            content = content,
        )
    }
}
