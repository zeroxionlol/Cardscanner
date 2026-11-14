package com.zeroxion.cardscanner.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Die Sammlung liegt als JSON-Datei im privaten App-Speicher, die Fotos daneben im Ordner "photos". */
class CollectionStore(context: Context) {
    private val file = File(context.filesDir, "collection.json")
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<CollectionItem>>(emptyList())
    val items: StateFlow<List<CollectionItem>> = _items

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock { _items.value = read() }
    }

    suspend fun add(item: CollectionItem) = mutate { listOf(item) + it }

    suspend fun remove(uid: String) = mutate { list ->
        list.firstOrNull { it.uid == uid }?.photoPath?.let { runCatching { File(it).delete() } }
        list.filterNot { it.uid == uid }
    }

    suspend fun update(uid: String, change: (CollectionItem) -> CollectionItem) = mutate { list ->
        list.map { if (it.uid == uid) change(it) else it }
    }

    /** Frische Kartendaten in alle Einträge dieser Karte übernehmen. */
    suspend fun applyFresh(fresh: CardInfo, now: Long) = mutate { list ->
        list.map { item ->
            if (item.card.id != fresh.id) return@map item
            val keepOld = !(fresh.hasNormal || fresh.hasHolo)
            item.copy(
                card = item.card.copy(
                    normal = if (keepOld) item.card.normal else fresh.normal,
                    holo = if (keepOld) item.card.holo else fresh.holo,
                    priceUpdated = if (keepOld) item.card.priceUpdated else fresh.priceUpdated,
                    idProduct = fresh.idProduct ?: item.card.idProduct,
                    imageBase = item.card.imageBase ?: fresh.imageBase,
                ),
                refreshedAt = now,
            )
        }
    }

    private suspend fun mutate(block: (List<CollectionItem>) -> List<CollectionItem>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val next = block(_items.value)
                _items.value = next
                write(next)
            }
        }

    private fun read(): List<CollectionItem> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::decode) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(items: List<CollectionItem>) {
        val array = JSONArray()
        items.forEach { array.put(encode(it)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(array.toString())
            tmp.delete()
        }
    }

    private fun encodePrices(p: Prices?): JSONObject? {
        if (p == null) return null
        val o = JSONObject()
        p.trend?.let { o.put("trend", it) }
        p.avg?.let { o.put("avg", it) }
        p.low?.let { o.put("low", it) }
        p.avg1?.let { o.put("avg1", it) }
        p.avg7?.let { o.put("avg7", it) }
        p.avg30?.let { o.put("avg30", it) }
        return o
    }

    private fun JSONObject.dbl(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

    private fun decodePrices(o: JSONObject?): Prices? {
        if (o == null) return null
        return Prices(o.dbl("trend"), o.dbl("avg"), o.dbl("low"), o.dbl("avg1"), o.dbl("avg7"), o.dbl("avg30"))
    }

    private fun encode(item: CollectionItem): JSONObject {
        val c = item.card
        val o = JSONObject()
        o.put("uid", item.uid)
        o.put("variant", item.variant)
        o.put("quantity", item.quantity)
        o.put("addedAt", item.addedAt)
        o.put("refreshedAt", item.refreshedAt)
        item.photoPath?.let { o.put("photoPath", it) }
        o.put("cardId", c.id)
        o.put("lang", c.lang)
        o.put("name", c.name)
        o.put("localId", c.localId)
        o.put("setId", c.setId)
        o.put("setName", c.setName)
        c.officialCount?.let { o.put("officialCount", it) }
        c.rarity?.let { o.put("rarity", it) }
        c.imageBase?.let { o.put("imageBase", it) }
        encodePrices(c.normal)?.let { o.put("normal", it) }
        encodePrices(c.holo)?.let { o.put("holo", it) }
        c.priceUpdated?.let { o.put("priceUpdated", it) }
        c.idProduct?.let { o.put("idProduct", it) }
        return o
    }

    private fun decode(o: JSONObject): CollectionItem? {
        val uid = o.str("uid") ?: return null
        val cardId = o.str("cardId") ?: return null
        val card = CardInfo(
            id = cardId,
            lang = o.str("lang") ?: "de",
            name = o.str("name") ?: cardId,
            localId = o.str("localId") ?: "",
            setId = o.str("setId") ?: "",
            setName = o.str("setName") ?: "",
            officialCount = if (o.has("officialCount")) o.optInt("officialCount") else null,
            rarity = o.str("rarity"),
            imageBase = o.str("imageBase"),
            normal = decodePrices(o.optJSONObject("normal")),
            holo = decodePrices(o.optJSONObject("holo")),
            priceUpdated = o.str("priceUpdated"),
            idProduct = if (o.has("idProduct")) o.optLong("idProduct") else null,
        )
        return CollectionItem(
            uid = uid,
            card = card,
            photoPath = o.str("photoPath"),
            variant = o.str("variant") ?: VARIANT_NORMAL,
            quantity = o.optInt("quantity", 1).coerceAtLeast(1),
            addedAt = o.optLong("addedAt", 0L),
            refreshedAt = o.optLong("refreshedAt", 0L),
        )
    }
}
