package com.zeroxion.cardscanner.data

/** Cardmarket-Preise einer Variante (alle Werte in Euro). */
data class Prices(
    val trend: Double? = null,
    val avg: Double? = null,
    val low: Double? = null,
    val avg1: Double? = null,
    val avg7: Double? = null,
    val avg30: Double? = null,
) {
    /** Der Wert, den die App als "den Preis" zeigt: Preistrend, sonst der beste verfügbare Durchschnitt. */
    val headline: Double?
        get() = listOf(trend, avg7, avg, avg30, avg1, low).firstOrNull { it != null && it > 0.0 }

    val hasAny: Boolean get() = headline != null
}

const val VARIANT_NORMAL = "normal"
const val VARIANT_HOLO = "holo"

data class CardInfo(
    val id: String,
    val lang: String,
    val name: String,
    val localId: String,
    val setId: String,
    val setName: String,
    val officialCount: Int?,
    val rarity: String?,
    val imageBase: String?,
    val normal: Prices?,
    val holo: Prices?,
    val priceUpdated: String?,
    val idProduct: Long?,
) {
    fun imageUrl(high: Boolean = false): String? =
        imageBase?.let { "$it/${if (high) "high" else "low"}.webp" }

    val numberLabel: String
        get() = if (officialCount != null && officialCount > 0) "$localId/$officialCount" else localId

    val hasNormal: Boolean get() = normal?.hasAny == true
    val hasHolo: Boolean get() = holo?.hasAny == true

    /** Preise der gewünschten Variante; fehlt sie, die jeweils andere. */
    fun prices(variant: String): Prices? {
        val wanted = if (variant == VARIANT_HOLO) holo else normal
        val other = if (variant == VARIANT_HOLO) normal else holo
        return wanted?.takeIf { it.hasAny } ?: other?.takeIf { it.hasAny }
    }

    val defaultVariant: String get() = if (!hasNormal && hasHolo) VARIANT_HOLO else VARIANT_NORMAL
}

data class CardBrief(
    val id: String,
    val localId: String,
    val name: String,
    val imageBase: String?,
) {
    val imageUrl: String? get() = imageBase?.let { "$it/low.webp" }
}

data class SetBrief(
    val id: String,
    val name: String,
    val official: Int,
    val total: Int,
)

/** Ein eigenes Set des Nutzers, z. B. "Lenas Karten". */
data class CardGroup(
    val id: String,
    val name: String,
)

data class CollectionItem(
    val uid: String,
    val card: CardInfo,
    val photoPath: String?,
    val variant: String,
    val quantity: Int,
    val addedAt: Long,
    val refreshedAt: Long,
    /** Eigenes Set, dem die Karte zugeordnet ist (oder keines). */
    val groupId: String? = null,
) {
    val unitPrice: Double? get() = card.prices(variant)?.headline
    val totalPrice: Double get() = (unitPrice ?: 0.0) * quantity
}

/** Seitenaufteilung eines Binders, z. B. 3 × 3 Taschen pro Seite. */
data class BinderLayout(val columns: Int, val rows: Int) {
    val pageSize: Int get() = columns * rows
    val label: String get() = "$columns × $rows"

    companion object {
        val all = listOf(BinderLayout(2, 2), BinderLayout(3, 3), BinderLayout(4, 3))
        val default = BinderLayout(3, 3)

        fun of(columns: Int, rows: Int): BinderLayout =
            all.firstOrNull { it.columns == columns && it.rows == rows } ?: default
    }
}

/**
 * Ein Binder (Sammelalbum) mit Seiten voller Taschen.
 *
 * @param slots Platz-Nummer (über alle Seiten fortlaufend, ab 0) → uid der Karte aus der Sammlung
 * @param coverPath eigenes Coverfoto im App-Speicher (oder keines)
 */
data class Binder(
    val id: String,
    val name: String,
    val coverPath: String?,
    val layout: BinderLayout,
    val pageCount: Int,
    val slots: Map<Int, String>,
    val createdAt: Long,
) {
    val capacity: Int get() = layout.pageSize * pageCount
    val filled: Int get() = slots.size

    /** Karten in der Reihenfolge, in der sie im Binder stecken. */
    val orderedUids: List<String> get() = slots.toSortedMap().values.toList()

    fun page(index: Int): Int = index / layout.pageSize
}
