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
import java.util.UUID

/** Die Sammlung liegt als JSON-Datei im privaten App-Speicher, die Fotos daneben im Ordner "photos". */
class CollectionStore(context: Context) {
    private val file = File(context.filesDir, "collection.json")
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<CollectionItem>>(emptyList())
    val items: StateFlow<List<CollectionItem>> = _items

    /** Eigene Sets des Nutzers, in der Reihenfolge, in der sie angelegt wurden. */
    private val groupFile = File(context.filesDir, "sets.json")
    private val _groups = MutableStateFlow<List<CardGroup>>(emptyList())
    val groups: StateFlow<List<CardGroup>> = _groups

    /** Binder (Sammelalben) mit Coverfoto und belegten Taschen. */
    private val binderFile = File(context.filesDir, "binders.json")
    private val _binders = MutableStateFlow<List<Binder>>(emptyList())
    val binders: StateFlow<List<Binder>> = _binders

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _groups.value = readGroups()
            val items = read()
            _items.value = items
            // Taschen, deren Karte es nicht mehr gibt, werden wieder frei.
            val known = items.mapTo(HashSet()) { it.uid }
            _binders.value = readBinders().map { b -> b.copy(slots = b.slots.filterValues { it in known }) }
        }
    }

    // ---------- Binder ----------

    suspend fun addBinder(
        name: String,
        coverPath: String?,
        layout: BinderLayout,
        now: Long = System.currentTimeMillis(),
    ): Binder = withContext(Dispatchers.IO) {
        mutex.withLock {
            val binder = Binder(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                coverPath = coverPath,
                layout = layout,
                pageCount = 1,
                slots = emptyMap(),
                createdAt = now,
            )
            val next = _binders.value + binder
            _binders.value = next
            writeBinders(next)
            binder
        }
    }

    suspend fun renameBinder(id: String, name: String) = mutateBinder(id) { it.copy(name = name.trim()) }

    /** Neues Coverfoto (null = keines). Das alte Foto wird gelöscht. */
    suspend fun setBinderCover(id: String, coverPath: String?) = mutateBinder(id) { b ->
        if (b.coverPath != null && b.coverPath != coverPath) runCatching { File(b.coverPath).delete() }
        b.copy(coverPath = coverPath)
    }

    /**
     * Andere Seitenaufteilung. Die Karten behalten ihre Reihenfolge; es gibt so viele Seiten,
     * dass alle hineinpassen.
     */
    suspend fun setBinderLayout(id: String, layout: BinderLayout) = mutateBinder(id) { b ->
        val last = b.slots.keys.maxOrNull() ?: -1
        val needed = (last + layout.pageSize) / layout.pageSize
        b.copy(layout = layout, pageCount = maxOf(1, b.pageCount, needed))
    }

    suspend fun addBinderPage(id: String) = mutateBinder(id) { it.copy(pageCount = it.pageCount + 1) }

    /** Entfernt eine Seite samt ihrer Taschen; die folgenden Seiten rücken nach. */
    suspend fun removeBinderPage(id: String, page: Int) = mutateBinder(id) { b ->
        if (b.pageCount <= 1 || page !in 0 until b.pageCount) return@mutateBinder b
        val size = b.layout.pageSize
        val first = page * size
        val slots = b.slots.mapNotNull { (index, uid) ->
            when {
                index < first -> index to uid
                index < first + size -> null
                else -> (index - size) to uid
            }
        }.toMap()
        b.copy(pageCount = b.pageCount - 1, slots = slots)
    }

    /** Karte in eine Tasche stecken (uid = null leert die Tasche). */
    suspend fun setBinderSlot(id: String, index: Int, uid: String?) = mutateBinder(id) { b ->
        if (index < 0) return@mutateBinder b
        val slots = if (uid == null) b.slots - index else b.slots + (index to uid)
        val needed = index / b.layout.pageSize + 1
        b.copy(slots = slots, pageCount = maxOf(b.pageCount, needed))
    }

    /** Zwei Taschen tauschen (auch mit einer leeren). */
    suspend fun swapBinderSlots(id: String, from: Int, to: Int) = mutateBinder(id) { b ->
        if (from == to || from < 0 || to < 0) return@mutateBinder b
        val slots = b.slots.toMutableMap()
        val a = slots.remove(from)
        val c = slots.remove(to)
        if (a != null) slots[to] = a
        if (c != null) slots[from] = c
        b.copy(slots = slots)
    }

    /** Löscht einen Binder samt Coverfoto. Die Karten bleiben in der Sammlung. */
    suspend fun removeBinder(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _binders.value.firstOrNull { it.id == id }?.coverPath?.let { runCatching { File(it).delete() } }
            val next = _binders.value.filterNot { it.id == id }
            _binders.value = next
            writeBinders(next)
        }
    }

    private suspend fun mutateBinder(id: String, change: (Binder) -> Binder) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = _binders.value.map { if (it.id == id) change(it) else it }
            _binders.value = next
            writeBinders(next)
        }
    }

    private fun readBinders(): List<Binder> {
        if (!binderFile.exists()) return emptyList()
        return try {
            val array = JSONArray(binderFile.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.str("id") ?: return@mapNotNull null
                val slotsJson = o.optJSONObject("slots")
                val slots = HashMap<Int, String>()
                slotsJson?.keys()?.forEach { key ->
                    val index = key.toIntOrNull() ?: return@forEach
                    val uid = slotsJson.optString(key).takeIf { it.isNotEmpty() } ?: return@forEach
                    if (index >= 0) slots[index] = uid
                }
                val layout = BinderLayout.of(o.optInt("columns", 3), o.optInt("rows", 3))
                val last = slots.keys.maxOrNull() ?: -1
                Binder(
                    id = id,
                    name = o.str("name") ?: "Binder",
                    coverPath = o.str("coverPath"),
                    layout = layout,
                    pageCount = maxOf(1, o.optInt("pages", 1), (last + layout.pageSize) / layout.pageSize),
                    slots = slots,
                    createdAt = o.optLong("createdAt", 0L),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeBinders(binders: List<Binder>) {
        val array = JSONArray()
        binders.forEach { b ->
            val slots = JSONObject()
            b.slots.forEach { (index, uid) -> slots.put(index.toString(), uid) }
            val o = JSONObject()
                .put("id", b.id)
                .put("name", b.name)
                .put("columns", b.layout.columns)
                .put("rows", b.layout.rows)
                .put("pages", b.pageCount)
                .put("createdAt", b.createdAt)
                .put("slots", slots)
            b.coverPath?.let { o.put("coverPath", it) }
            array.put(o)
        }
        val tmp = File(binderFile.parentFile, binderFile.name + ".tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(binderFile)) {
            binderFile.writeText(array.toString())
            tmp.delete()
        }
    }

    /** Legt ein Set an; gibt es den Namen schon, wird das vorhandene zurückgegeben. */
    suspend fun addGroup(name: String): CardGroup = withContext(Dispatchers.IO) {
        mutex.withLock {
            val clean = name.trim()
            val existing = _groups.value.firstOrNull { it.name.equals(clean, ignoreCase = true) }
            if (existing != null) existing
            else {
                val group = CardGroup(UUID.randomUUID().toString(), clean)
                val next = _groups.value + group
                _groups.value = next
                writeGroups(next)
                group
            }
        }
    }

    suspend fun renameGroup(id: String, name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = _groups.value.map { if (it.id == id) it.copy(name = name.trim()) else it }
            _groups.value = next
            writeGroups(next)
        }
    }

    /** Löscht ein Set. Die Karten bleiben in der Sammlung, nur ohne Set. */
    suspend fun removeGroup(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val nextGroups = _groups.value.filterNot { it.id == id }
            val nextItems = _items.value.map { if (it.groupId == id) it.copy(groupId = null) else it }
            _groups.value = nextGroups
            _items.value = nextItems
            writeGroups(nextGroups)
            write(nextItems)
        }
    }

    private fun readGroups(): List<CardGroup> {
        if (!groupFile.exists()) return emptyList()
        return try {
            val array = JSONArray(groupFile.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.str("id") ?: return@mapNotNull null
                val name = o.str("name") ?: return@mapNotNull null
                CardGroup(id, name)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeGroups(groups: List<CardGroup>) {
        val array = JSONArray()
        groups.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)) }
        groupFile.writeText(array.toString())
    }

    suspend fun add(item: CollectionItem) = mutate { listOf(item) + it }

    suspend fun remove(uid: String) {
        mutate { list ->
            list.firstOrNull { it.uid == uid }?.photoPath?.let { runCatching { File(it).delete() } }
            list.filterNot { it.uid == uid }
        }
        // Die Tasche im Binder wird wieder frei.
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (_binders.value.none { b -> uid in b.slots.values }) return@withLock
                val next = _binders.value.map { b -> b.copy(slots = b.slots.filterValues { it != uid }) }
                _binders.value = next
                writeBinders(next)
            }
        }
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

    /**
     * Preise aus dem Cardmarket-Preisführer übernehmen, zugeordnet über die Produktnummer.
     * [updated] ist der Stand der Preisliste.
     */
    suspend fun applyPrices(prices: Map<Long, CardmarketPriceGuide.Entry>, updated: String?, now: Long) =
        mutate { list ->
            list.map { item ->
                val entry = item.card.idProduct?.let { prices[it] }?.takeIf { it.hasAny } ?: return@map item
                item.copy(
                    card = item.card.copy(
                        normal = entry.normal,
                        holo = entry.holo,
                        priceUpdated = updated ?: item.card.priceUpdated,
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
        item.groupId?.let { o.put("groupId", it) }
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
            groupId = o.str("groupId"),
        )
    }
}
