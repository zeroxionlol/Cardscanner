package com.zeroxion.cardscanner.scan

/** Eine erkannte Textzeile mit ihrer Lage im Foto (Pixel). Fehlt die waagerechte Lage, sind beide Werte 0. */
data class OcrLine(
    val text: String,
    val top: Int,
    val bottom: Int,
    val left: Int = 0,
    val right: Int = 0,
) {
    val height: Int get() = bottom - top
    val centerY: Int get() = (top + bottom) / 2
}

/**
 * Was sich aus dem Kartentext ablesen ließ.
 *
 * @param setCode aufgedrucktes Set-Kürzel neben der Nummer ("MEP", "SVI")
 * @param dexId Pokédex-Nummer aus der Zeile unter dem Bild ("Nr. 0103")
 */
data class ParsedCard(
    val names: List<String>,
    val localId: String?,
    val setTotal: Int?,
    val setCode: String? = null,
    val dexId: Int? = null,
) {
    val isEmpty: Boolean get() = names.isEmpty() && localId == null

    /** Nummer samt Set (über Setgröße oder Kürzel): damit ist die Karte in der Regel eindeutig. */
    val hasSetNumber: Boolean get() = localId != null && (setTotal != null || setCode != null)
}

/**
 * Liest aus dem erkannten Text einer Karte den Namen (große Zeile oben)
 * und die Kartennummer unten – als "136/189" oder, bei Promos und Energien
 * ohne Setgröße, als Set-Kürzel mit Nummer ("MEP DE 094").
 * Reines Kotlin ohne Android, damit es sich einfach testen lässt.
 */
object CardTextParser {
    private val fraction =
        Regex("""(?<![A-Za-z0-9])([A-Za-z]{0,3}\d{1,3})\s*/\s*([A-Za-z]{0,3}\d{2,3})(?![0-9])""")
    /** Wie [fraction], aber tolerant gegenüber typischen Lesefehlern (O statt 0, l/I statt 1). */
    private val looseFraction =
        Regex("""(?<![A-Za-z0-9])([0-9OoIl|]{1,3})\s*/\s*([0-9OoIl|]{2,3})(?![A-Za-z0-9])""")
    /** Promos bis Schwert & Schild: das Kürzel gehört zur Nummer ("SWSH123"). */
    private val promo =
        Regex("""(?<![A-Z0-9])(SWSH|SM|XY|BW|HGSS|DP)\s?(\d{1,3})(?![0-9])""")
    /**
     * Aufdruck seit Karmesin & Purpur: Set-Kürzel, Sprache, Nummer – "MEP DE 094", "OBF DE 125/197".
     * Promos und Energien haben keine Setgröße; dort führt nur das Kürzel zum Set.
     */
    private val coded = Regex(
        """(?<![A-Za-z0-9])([A-Z0-9]{2,4})\s*(?:DE|EN|FR|IT|ES|PT)\s*([0-9OoIl|]{1,3})""" +
            """(?:\s*/\s*[0-9OoIl|]{2,3})?(?![A-Za-z0-9])""",
    )
    /** Wie [coded], wenn das winzige Sprachkürzel fehlt oder verlesen wurde ("MEP 094", "MEE oE 016"). */
    private val codedLoose = Regex(
        """(?<![A-Za-z0-9])([A-Z][A-Z0-9]{2,3})\s+(?:[A-Za-z0-9]{1,2}\s+)?(\d{3})(?![A-Za-z0-9]|\s*/)""",
    )
    /** Allein stehende dreistellige Nummer in der untersten Zeile ("016 Illustr. …"). */
    private val bareNumber = Regex("""(?<![A-Za-z0-9/.,])(\d{3})(?![A-Za-z0-9/.,%])""")
    /** Pokédex-Nummer: "Nr. 0103", "NO. 0103", "N° 0103". */
    private val dexNumber = Regex("""(?i)(?<![A-Za-z])N[ro°º]\s*[.,:]?\s*([0-9O]{3,4})(?![0-9])""")
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
        val lowerHalf = minTop + span / 2
        val lastLines = minTop + span * 0.86
        var localId: String? = null
        var total: Int? = null
        var setCode: String? = null

        // 1) "136/189": Nummer und Setgröße.
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

        // 2) "MEP DE 094": Set-Kürzel mit Nummer. Das Kürzel hilft auch neben einer Setgröße.
        val codedHit = numberSources.firstNotNullOfOrNull { source ->
            source.filter { it.centerY > lowerHalf }.firstNotNullOfOrNull { line ->
                coded.findAll(line.text).firstOrNull { isSetCode(it.groupValues[1]) && it.groupValues[2].any(Char::isDigit) }
            }
        }
        if (codedHit != null) {
            setCode = codedHit.groupValues[1]
            if (localId == null) localId = asDigits(codedHit.groupValues[2])
        }

        // 3) Ältere Promos: "SWSH123".
        if (localId == null) {
            for (source in numberSources) {
                val hit = source
                    .filter { it.centerY > lowerHalf }
                    .firstNotNullOfOrNull { promo.find(it.text.uppercase()) } ?: continue
                localId = hit.groupValues[1] + hit.groupValues[2]
                break
            }
        }

        // 4) Kürzel und Nummer ohne lesbare Sprache, zuletzt die Nummer allein – nur in den untersten Zeilen.
        if (localId == null) {
            val hit = numberSources.firstNotNullOfOrNull { source ->
                source.filter { it.centerY >= lastLines }.firstNotNullOfOrNull { line ->
                    codedLoose.findAll(line.text).firstOrNull { isSetCode(it.groupValues[1]) }
                }
            }
            if (hit != null) {
                setCode = hit.groupValues[1]
                localId = hit.groupValues[2]
            }
        }
        if (localId == null) {
            localId = numberSources.firstNotNullOfOrNull { source ->
                source.filter { it.centerY >= lastLines && '©' !in it.text }
                    .firstNotNullOfOrNull { bareNumber.find(it.text) }
            }?.groupValues?.get(1)
        }

        val dexId = (usable.sortedBy { it.centerY } + usableBottom)
            .firstNotNullOfOrNull { dexNumber.find(it.text) }
            ?.let { it.groupValues[1].replace('O', '0', ignoreCase = true).toIntOrNull() }
            ?.takeIf { it in 1..1999 }

        val topZone = usable.filter { it.centerY <= minTop + span * 0.25 }
        val pool = topZone.ifEmpty { usable }
        val names = pool
            .sortedByDescending { it.height }
            .map { cleanName(it.text) }
            .filter { name -> name.count { it.isLetter() } >= minLetters(name) }
            .distinct()
            .take(3)

        return ParsedCard(names, localId, total, setCode, dexId)
    }

    /** Wörter, die wie ein Set-Kürzel aussehen, aber keins sind (KP-Angaben, Regelkästen …). */
    private val notSetCodes = setOf("KP", "HP", "PV", "PS", "NR", "NO", "GEW", "GX", "EX", "VMAX", "TAG", "TEAM")

    private fun isSetCode(code: String): Boolean =
        code.any { it.isLetter() } && code !in notSetCodes && promo.matchEntire(code + "1") == null

    /**
     * Schreibweisen, unter denen ein gelesenes Kürzel in der Datenbank stehen kann. Das Regelzeichen
     * davor (ein Buchstabe im Kästchen) klebt beim Lesen manchmal am Kürzel: "JMEP" statt "MEP".
     */
    fun setCodeCandidates(code: String): List<String> {
        val upper = code.trim().uppercase()
        return listOfNotNull(upper, upper.drop(1).takeIf { upper.length == 4 && upper[0].isLetter() })
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
