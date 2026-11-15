package com.zeroxion.cardscanner.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Nur feste Farbwerte hier: Ein Verweis auf Werte aus Theme.kt würde beim Start eine
// gegenseitige Initialisierung auslösen (Theme.kt nutzt seinerseits diese Klasse).

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
        glows = listOf(Color(0xFFFF5D5D), Color(0xFF4DA3FF), Color(0xFFC084FC), Color(0xFFFFD23F)),
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

