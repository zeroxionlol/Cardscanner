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
    /** Wie [fraction], aber tolerant gegenüber typischen Lesefehlern (O statt 0, l/I statt 1). */
    private val looseFraction =
        Regex("""(?<![A-Za-z0-9])([0-9OoIl|]{1,3})\s*/\s*([0-9OoIl|]{2,3})(?![A-Za-z0-9])""")
    private val promo =
        Regex("""(?<![A-Z0-9])(SWSH|SVP|SM|XY|BW|HGSS|DP)\s?(\d{1,3})(?![0-9])""")
    private val hitPoints =
        Regex("""(?i)\b(?:KP|HP|PV|PS)\s*\d{2,3}\b|\b\d{2,3}\s*(?:KP|HP|PV|PS)\b""")
    private val stage =
        Regex("""(?i)\b(?:BASIS|BASIC|DE BASE|BASE|PHASE\s*\d|STAGE\s*\d|STUFE\s*\d|NIVEAU\s*\d)\b""")
    /** Entwicklungsstufen auf japanischen, koreanischen und chinesischen Karten. */
    private val asianStage =
        Regex("""たね|[12１２]\s*進化|기본|[12]\s*진화|基礎|基础|[12一二]\s*[階阶]\s*[進进]化""")
    private val evolves =
        Regex("""(?i)^\s*(entwickelt sich|evolves from|weiterentwicklung|évolution d|evolution d)""")
    private val spaces = Regex("""\s+""")
    private val idPattern = Regex("""^([A-Z]*)0*(\d+)$""")
    private val labels = setOf(
        "trainer", "item", "artikel", "unterstützer", "unterstuetzer", "supporter",
        "stadion", "stadium", "energie", "energy", "pokémon", "pokemon",
        "ausrüstung", "pokémon-ausrüstung", "tool", "tera", "ex", "v", "vmax", "vstar", "gx",
    )
    private val suffixes = setOf("ex", "v", "vmax", "vstar", "gx", "v-union", "break", "lv.x")

    /**
     * @param lines Text des ganzen Fotos
     * @param bottomLines derselbe untere Rand, vergrößert gelesen (bevorzugt für die Kartennummer)
     */
    fun parse(lines: List<OcrLine>, bottomLines: List<OcrLine> = emptyList()): ParsedCard {
        val usable = lines.filter { it.text.isNotBlank() }
        val usableBottom = bottomLines.filter { it.text.isNotBlank() }
        if (usable.isEmpty() && usableBottom.isEmpty()) return ParsedCard(emptyList(), null, null)

        val all = usable + usableBottom
        val minTop = all.minOf { it.top }
        val maxBottom = all.maxOf { it.bottom }
        val span = (maxBottom - minTop).coerceAtLeast(1)

        // Kartennummer: erst im vergrößerten unteren Rand suchen, dann im ganzen Foto – jeweils von unten.
        val numberSources = listOf(
            usableBottom.sortedByDescending { it.centerY },
            usable.sortedByDescending { it.centerY },
        )
        var localId: String? = null
        var total: Int? = null
        for (source in numberSources) {
            val hit = source.firstNotNullOfOrNull { fraction.find(it.text) } ?: continue
            localId = fixDigits(hit.groupValues[1].uppercase())
            total = hit.groupValues[2].filter { it.isDigit() }.toIntOrNull()
            break
        }
        if (localId == null) {
            for (source in numberSources) {
                val hit = source.firstNotNullOfOrNull { line ->
                    looseFraction.findAll(line.text).firstOrNull { m ->
                        m.groupValues[1].any { it.isDigit() } && m.groupValues[2].any { it.isDigit() }
                    }
                } ?: continue
                localId = asDigits(hit.groupValues[1])
                total = asDigits(hit.groupValues[2]).toIntOrNull()
                break
            }
        }
        if (localId == null) {
            for (source in numberSources) {
                val hit = source
                    .filter { it.centerY > minTop + span / 2 }
                    .firstNotNullOfOrNull { promo.find(it.text.uppercase()) } ?: continue
                localId = hit.groupValues[1] + hit.groupValues[2]
                break
            }
        }

        val topZone = usable.filter { it.centerY <= minTop + span * 0.25 }
        val pool = topZone.ifEmpty { usable }
        val names = pool
            .sortedByDescending { it.height }
            .map { cleanName(it.text) }
            .filter { name -> name.count { it.isLetter() } >= minLetters(name) }
            .distinct()
            .take(3)

        return ParsedCard(names, localId, total)
    }

    private fun asDigits(token: String): String =
        token.map { c ->
            when (c) {
                'O', 'o' -> '0'
                'I', 'l', '|' -> '1'
                else -> c
            }
        }.joinToString("")

    /** "I17" oder "O49" sind verlesene Ziffern, "TG12" oder "SV107" echte Kennungen. */
    private fun fixDigits(id: String): String {
        val prefix = id.takeWhile { !it.isDigit() }
        return if (prefix.isNotEmpty() && prefix.all { it == 'O' || it == 'I' || it == 'L' }) {
            prefix.map { if (it == 'O') '0' else '1' }.joinToString("") + id.drop(prefix.length)
        } else id
    }

    /** Namen in japanischer, koreanischer oder chinesischer Schrift sind oft nur zwei Zeichen lang. */
    private fun minLetters(text: String): Int = if (text.any { it.code >= 0x2E80 }) 2 else 3

    /** Entfernt KP-Angaben, Entwicklungsstufen und Sonderzeichen aus einer Namenszeile. */
    fun cleanName(raw: String): String {
        if (evolves.containsMatchIn(raw)) return ""
        var s = hitPoints.replace(raw, " ")
        s = stage.replace(s, " ")
        s = asianStage.replace(s, " ")
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
        return listOf(base, longest, name).filter { it.length >= minLetters(it) }.distinct()
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
