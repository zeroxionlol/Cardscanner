package com.zeroxion.cardscanner.data

/** Schrift, die die Texterkennung lesen muss. */
enum class Script { LATIN, JAPANESE, KOREAN, CHINESE }

/**
 * Eine wählbare Kartensprache beim Scannen.
 *
 * @param apiLangs Sprachkürzel der Kartendatenbank, in dieser Reihenfolge durchsucht
 * @param western Karten der internationalen Sets (gleiche Set-Kennungen wie Englisch)
 */
data class ScanLang(
    val code: String,
    val label: String,
    val title: String,
    val apiLangs: List<String>,
    val script: Script,
    val western: Boolean,
) {
    /** Sprachen für die Namenssuche: bei westlichen Karten zusätzlich Englisch. */
    val searchLangs: List<String>
        get() = if (western && "en" !in apiLangs) apiLangs + "en" else apiLangs
}

object ScanLangs {
    val all: List<ScanLang> = listOf(
        ScanLang("de", "DE", "Deutsch", listOf("de"), Script.LATIN, western = true),
        ScanLang("en", "EN", "Englisch", listOf("en"), Script.LATIN, western = true),
        ScanLang("fr", "FR", "Französisch", listOf("fr"), Script.LATIN, western = true),
        ScanLang("ja", "JP", "Japanisch", listOf("ja"), Script.JAPANESE, western = false),
        ScanLang("ko", "KR", "Koreanisch", listOf("ko"), Script.KOREAN, western = false),
        // Chinesisch: erst traditionell (Taiwan/Hongkong), dann vereinfacht.
        ScanLang("zh", "CH", "Chinesisch", listOf("zh-tw", "zh-cn"), Script.CHINESE, western = false),
    )

    fun byCode(code: String?): ScanLang = all.firstOrNull { it.code == code } ?: all.first()

    private val asianApiLangs = setOf("ja", "ko", "zh-tw", "zh-cn", "id", "th")

    /** Asiatische Karten haben eigene Sets mit eigenen Kennungen. */
    fun isAsian(apiLang: String): Boolean = apiLang in asianApiLangs
}
