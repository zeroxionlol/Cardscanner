package com.zeroxion.cardscanner.data

import kotlin.math.roundToLong

/** Geschätzte Pullrate einer bestimmten Karte. */
data class PullRate(
    /** Im Schnitt nötige Booster für genau diese Karte. */
    val packs: Long,
    /** Karten derselben Seltenheit im Set. */
    val sameRarity: Int,
)

/**
 * Pullraten für englische Booster der Karmesin-&-Purpur- und Mega-Entwicklung-Ära.
 *
 * Die Pokémon Company veröffentlicht keine Ziehwahrscheinlichkeiten. Die Werte stammen aus
 * Zählungen großer Booster-Öffnungen (TCGplayer, mehrere tausend Packs pro Set) und geben an,
 * in wie vielen Boostern im Schnitt eine Karte der jeweiligen Seltenheit steckt.
 * Für eine bestimmte Karte wird das mit der Zahl der Karten dieser Seltenheit im Set multipliziert.
 */
object PullRates {
    /** Booster pro Treffer, je Seltenheit (englische TCGdex-Bezeichnung). */
    private val base = mapOf(
        "Common" to 0.25, // 4 pro Booster
        "Uncommon" to 1.0 / 3, // 3 pro Booster
        "Rare" to 1.3,
        "Double rare" to 6.0,
        "Ultra Rare" to 15.0,
        "Illustration rare" to 13.0,
        "Special illustration rare" to 86.0,
        "Hyper rare" to 150.0,
        "ACE SPEC Rare" to 20.0,
    )

    private val earlySv = mapOf(
        "Double rare" to 7.0,
        "Special illustration rare" to 32.0,
        "Hyper rare" to 54.0,
    )

    /** Abweichungen einzelner Sets. */
    private val bySet: Map<String, Map<String, Double>> = mapOf(
        "sv01" to earlySv,
        "sv02" to earlySv,
        "sv03" to earlySv,
        "sv03.5" to earlySv,
        "sv04" to mapOf("Special illustration rare" to 47.0, "Hyper rare" to 82.0),
        "sv05" to mapOf("Hyper rare" to 139.0),
        "sv06" to mapOf("Hyper rare" to 144.0),
        "sv07" to mapOf("Ultra Rare" to 17.0, "Hyper rare" to 137.0),
        "sv08" to mapOf("Ultra Rare" to 14.8, "Special illustration rare" to 87.0, "Hyper rare" to 189.0),
        "sv08.5" to mapOf("Ultra Rare" to 13.0, "Special illustration rare" to 45.0, "Hyper rare" to 180.0),
        "sv10" to mapOf("Double rare" to 5.0, "Ultra Rare" to 16.0, "Illustration rare" to 12.0),
    )

    private val supportedSet = Regex("""^(sv|me)\d""")

    /** Nur normale Booster-Sets der aktuellen Ären (keine Promos, Energien, Sonderprodukte). */
    fun supports(setId: String): Boolean = supportedSet.containsMatchIn(setId.lowercase())

    /** Booster pro Karte der Seltenheit [rarity] im Set, oder null ohne verlässliche Daten. */
    fun packsPerHit(setId: String, rarity: String): Double? {
        if (!supports(setId)) return null
        val id = setId.lowercase()
        return bySet[id]?.get(rarity) ?: base[rarity]
    }

    /** Pullrate einer Karte, wenn [sameRarity] Karten dieser Seltenheit im Set stecken. */
    fun estimate(setId: String, rarity: String, sameRarity: Int): PullRate? {
        if (sameRarity <= 0) return null
        val perHit = packsPerHit(setId, rarity) ?: return null
        val packs = (perHit * sameRarity).roundToLong().coerceAtLeast(1)
        return PullRate(packs, sameRarity)
    }
}
