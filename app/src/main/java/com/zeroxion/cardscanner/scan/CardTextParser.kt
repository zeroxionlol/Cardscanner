package com.zeroxion.cardscanner.scan

/** Eine erkannte Textzeile mit ihrer vertikalen Lage im Foto (Pixel). */
data class OcrLine(val text: String, val top: Int, val bottom: Int) {
    val height: Int get() = bottom - top
    val centerY: Int get() = (top + bottom) / 2
}

/** Was sich aus dem Kartentext ablesen ließ. */
data class ParsedCard(
    val names: List<String>,
    val localId: String?,
    val setTotal: Int?,
) {
    val isEmpty: Boolean get() = names.isEmpty() && localId == null
}

/**
 * Liest aus dem erkannten Text einer Karte den Namen (große Zeile oben)
 * und die Kartennummer (z. B. "136/189" unten).
 * Reines Kotlin ohne Android, damit es sich einfach testen lässt.
 */
object CardTextParser {
    private val fraction =
        Regex("""(?<![A-Za-z0-9])([A-Za-z]{0,3}\d{1,3})\s*/\s*([A-Za-z]{0,3}\d{2,3})(?![0-9])""")
    private val promo =
        Regex("""(?<![A-Z0-9])(SWSH|SVP|SM|XY|BW|HGSS|DP)\s?(\d{1,3})(?![0-9])""")
    private val hitPoints =
        Regex("""(?i)\b(?:KP|HP|PV|PS)\s*\d{2,3}\b|\b\d{2,3}\s*(?:KP|HP|PV|PS)\b""")
    private val stage =
        Regex("""(?i)\b(?:BASIS|BASIC|PHASE\s*\d|STAGE\s*\d|STUFE\s*\d)\b""")
    private val evolves =
        Regex("""(?i)^\s*(entwickelt sich|evolves from|weiterentwicklung)""")
    private val spaces = Regex("""\s+""")
    private val idPattern = Regex("""^([A-Z]*)0*(\d+)$""")
    private val labels = setOf(
        "trainer", "item", "artikel", "unterstützer", "unterstuetzer", "supporter",
        "stadion", "stadium", "energie", "energy", "pokémon", "pokemon",
        "ausrüstung", "pokémon-ausrüstung", "tool", "tera", "ex", "v", "vmax", "vstar", "gx",
    )
    private val suffixes = setOf("ex", "v", "vmax", "vstar", "gx", "v-union", "break", "lv.x")

    fun parse(lines: List<OcrLine>): ParsedCard {
        val usable = lines.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return ParsedCard(emptyList(), null, null)

        val minTop = usable.minOf { it.top }
        val maxBottom = usable.maxOf { it.bottom }
        val span = (maxBottom - minTop).coerceAtLeast(1)
        val fromBottom = usable.sortedByDescending { it.centerY }

        var localId: String? = null
        var total: Int? = null
        val fractionHit = fromBottom.firstNotNullOfOrNull { fraction.find(it.text) }
        if (fractionHit != null) {
            localId = fractionHit.groupValues[1].uppercase()
            total = fractionHit.groupValues[2].filter { it.isDigit() }.toIntOrNull()
        } else {
            val promoHit = fromBottom
                .filter { it.centerY > minTop + span / 2 }
                .firstNotNullOfOrNull { promo.find(it.text.uppercase()) }
            if (promoHit != null) localId = promoHit.groupValues[1] + promoHit.groupValues[2]
        }

        val topZone = usable.filter { it.centerY <= minTop + span * 0.25 }
        val pool = topZone.ifEmpty { usable }
        val names = pool
            .sortedByDescending { it.height }
            .map { cleanName(it.text) }
            .filter { name -> name.count { it.isLetter() } >= 3 }
            .distinct()
            .take(3)

        return ParsedCard(names, localId, total)
    }

    /** Entfernt KP-Angaben, Entwicklungsstufen und Sonderzeichen aus einer Namenszeile. */
    fun cleanName(raw: String): String {
        if (evolves.containsMatchIn(raw)) return ""
        var s = hitPoints.replace(raw, " ")
        s = stage.replace(s, " ")
        s = buildString {
            for (c in s) {
                val keep = c.isLetter() || c == '-' || c == '\'' || c == '’' || c == '.' ||
                    c == ' ' || c == '♀' || c == '♂'
                append(if (keep) c else ' ')
            }
        }
        s = spaces.replace(s, " ").trim().trim('-', '.', '\'', ' ')
        if (s.lowercase() in labels) return ""
        return s
    }

    /**
     * Suchbegriffe für die Namenssuche. Zuerst der Name ohne Zusatz (ex, V …):
     * so findet "Glurak ex" sowohl "Glurak EX" als auch "Glurak-ex".
     */
    fun searchTerms(name: String): List<String> {
        val words = name.replace('-', ' ').split(' ').filter { it.isNotBlank() }
        val core = words.filterNot { it.lowercase() in suffixes }
        val base = core.joinToString(" ")
        val longest = core.maxByOrNull { it.length } ?: ""
        return listOf(base, longest, name).filter { it.length >= 3 }.distinct()
    }

    /** Vergleicht Kartennummern unabhängig von führenden Nullen und Großschreibung ("004" = "4"). */
    fun sameLocalId(a: String, b: String): Boolean = normalizeId(a) == normalizeId(b)

    private fun normalizeId(id: String): String {
        val upper = id.trim().uppercase()
        val match = idPattern.find(upper) ?: return upper
        return match.groupValues[1] + match.groupValues[2]
    }

    /** Ähnlichkeit zweier Namen von 0 bis 1. */
    fun similarity(a: String, b: String): Double {
        val x = a.lowercase().replace('-', ' ').trim()
        val y = b.lowercase().replace('-', ' ').trim()
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        if (x.contains(y) || y.contains(x)) return 0.8
        val distance = levenshtein(x, y)
        return (1.0 - distance.toDouble() / maxOf(x.length, y.length)).coerceIn(0.0, 1.0)
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }
}
