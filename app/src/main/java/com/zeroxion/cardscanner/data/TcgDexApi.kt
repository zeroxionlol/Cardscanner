package com.zeroxion.cardscanner.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Kleiner Client für die freie TCGdex-API (https://tcgdex.dev).
 * Sie liefert Kartendaten und die täglichen Cardmarket-Preise (Preisführer in Euro).
 *
 * Alle Funktionen blockieren und gehören auf Dispatchers.IO.
 */
object TcgDexApi {
    private const val BASE = "https://api.tcgdex.net/v2"

    class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

    @Volatile
    private var setCache: Map<String, List<SetBrief>> = emptyMap()

    /** Fehler, bei dem ein zweiter Versuch lohnt (Server kurz überlastet, Verbindung abgerissen). */
    private class Transient(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Pausen vor dem zweiten und dritten Versuch: der Server meldet zeitweise kurz "503". */
    private val retryPauses = longArrayOf(400, 1200)

    private fun httpGet(url: String): String? {
        var attempt = 0
        while (true) {
            try {
                return fetch(url)
            } catch (e: Transient) {
                if (attempt >= retryPauses.size) {
                    throw ApiException(e.message ?: "Keine Verbindung zur Kartendatenbank", e.cause)
                }
                Thread.sleep(retryPauses[attempt++])
            }
        }
    }

    private fun fetch(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Cardscanner-Android")
            val code = conn.responseCode
            if (code == 404) return null
            if (code >= 500) throw Transient("Server antwortet mit $code")
            if (code !in 200..299) throw ApiException("Server antwortet mit $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: ApiException) {
            throw e
        } catch (e: Transient) {
            throw e
        } catch (e: Exception) {
            throw Transient("Keine Verbindung zur Kartendatenbank", e)
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.dbl(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun parseBriefs(array: JSONArray?): List<CardBrief> {
        if (array == null) return emptyList()
        val out = ArrayList<CardBrief>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.str("id") ?: continue
            out += CardBrief(
                id = id,
                localId = o.str("localId") ?: "",
                name = o.str("name") ?: id,
                imageBase = o.str("image"),
            )
        }
        return out
    }

    /** Alle Sets mit ihrer Kartenanzahl (einmal geladen, dann im Speicher). */
    fun sets(lang: String = "en"): List<SetBrief> {
        setCache[lang]?.let { return it }
        val body = httpGet("$BASE/$lang/sets") ?: return emptyList()
        val array = JSONArray(body)
        val out = ArrayList<SetBrief>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.str("id") ?: continue
            val count = o.optJSONObject("cardCount")
            out += SetBrief(
                id = id,
                name = o.str("name") ?: id,
                official = count?.optInt("official", -1) ?: -1,
                total = count?.optInt("total", -1) ?: -1,
            )
        }
        setCache = setCache + (lang to out)
        return out
    }

    @Volatile
    private var digitalCache: Set<String>? = null

    /**
     * Sets der rein digitalen Pocket-App. Diese Karten gibt es nicht gedruckt,
     * sie sollen beim Scannen echter Karten nicht als Treffer auftauchen.
     */
    fun digitalSetIds(): Set<String> {
        digitalCache?.let { return it }
        val ids = try {
            val sets = httpGet("$BASE/en/series/tcgp")?.let { JSONObject(it).optJSONArray("sets") }
            val out = HashSet<String>()
            if (sets != null) {
                for (i in 0 until sets.length()) {
                    sets.optJSONObject(i)?.str("id")?.let { out += it.lowercase() }
                }
            }
            out
        } catch (e: Exception) {
            return emptySet()
        }
        digitalCache = ids
        return ids
    }

    private fun isPrinted(cardId: String): Boolean =
        cardId.substringBeforeLast('-').lowercase() !in digitalSetIds()

    private val setCardCache = java.util.concurrent.ConcurrentHashMap<String, List<CardBrief>>()

    /** Karten eines Sets (Kurzform). Leere Liste, wenn es das Set in der Sprache nicht gibt. */
    fun setCards(lang: String, setId: String): List<CardBrief> {
        val key = "$lang/$setId"
        setCardCache[key]?.let { return it }
        val body = httpGet("$BASE/$lang/sets/${enc(setId)}") ?: return emptyList()
        val cards = parseBriefs(JSONObject(body).optJSONArray("cards"))
        if (cards.isNotEmpty()) setCardCache[key] = cards
        return cards
    }

    private val setCodeCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    /**
     * Sets zu einem aufgedruckten Kürzel ("MEP" → mep, "SVI" → sv01). Die Kennungen gelten
     * für alle westlichen Sprachen; ein Kürzel kann zu mehreren Sets gehören.
     */
    fun setIdsByCode(code: String): List<String> {
        val key = code.trim().uppercase()
        if (key.isEmpty()) return emptyList()
        setCodeCache[key]?.let { return it }
        val body = httpGet("$BASE/en/sets?abbreviation.official=eq:${enc(key)}") ?: return emptyList()
        val array = JSONArray(body)
        val digital = digitalSetIds()
        val ids = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val id = array.optJSONObject(i)?.str("id") ?: continue
            if (id.lowercase() !in digital) ids += id
        }
        setCodeCache[key] = ids
        return ids
    }

    /**
     * Karten eines Pokémon (Pokédex-Nummer) mit der gesuchten Kartennummer. Die Pokédex-Nummer
     * ist in jeder Sprache gleich – so findet sich eine Karte auch ohne lesbaren Namen.
     * Der Server vergleicht nur grob; die Kartennummer wird deshalb vom Aufrufer nachgeprüft.
     */
    fun searchByDex(lang: String, dexId: Int, localId: String): List<CardBrief> {
        val number = localId.trimStart('0').ifEmpty { "0" }
        val body = httpGet("$BASE/$lang/cards?dexId=eq:$dexId&localId=${enc(number)}") ?: return emptyList()
        return parseBriefs(JSONArray(body)).filter { isPrinted(it.id) }
    }

    private val rarityCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Seltenheit einer Karte in der englischen Datenbank (einheitliche Bezeichnungen). */
    fun englishRarity(cardId: String): String? {
        val body = httpGet("$BASE/en/cards/${enc(cardId)}") ?: return null
        val o = JSONObject(body)
        if (!o.str("id").equals(cardId, ignoreCase = true)) return null
        return o.str("rarity")
    }

    /** Wie viele Karten eines Sets diese Seltenheit haben (englische Bezeichnung). */
    fun rarityCount(setId: String, rarity: String): Int {
        val key = "$setId|$rarity"
        rarityCounts[key]?.let { return it }
        val body = httpGet("$BASE/en/cards?set.id=eq:${enc(setId)}&rarity=eq:${enc(rarity)}") ?: return 0
        val count = JSONArray(body).length()
        rarityCounts[key] = count
        return count
    }

    /** Geschätzte Pullrate einer Karte, oder null, wenn es keine Daten dafür gibt. */
    fun pullRate(setId: String, cardId: String): PullRate? {
        if (!PullRates.supports(setId)) return null
        val rarity = englishRarity(cardId) ?: return null
        if (PullRates.packsPerHit(setId, rarity) == null) return null
        return PullRates.estimate(setId, rarity, rarityCount(setId, rarity))
    }

    private val printCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Merkmale des Kartendrucks, die ein Nachdruck vom Original übernimmt: Zeichner, KP, Attacken. */
    private fun printMarks(cardId: String): String? {
        printCache[cardId]?.let { return it }
        val body = httpGet("$BASE/en/cards/${enc(cardId)}") ?: return null
        val o = JSONObject(body)
        if (!o.str("id").equals(cardId, ignoreCase = true)) return null
        val illustrator = o.str("illustrator") ?: return null
        val attacks = o.optJSONArray("attacks")
        val names = ArrayList<String>()
        if (attacks != null) {
            for (i in 0 until attacks.length()) attacks.optJSONObject(i)?.str("name")?.let { names += it }
        }
        val marks = listOf(illustrator, o.str("hp") ?: "", names.joinToString("+")).joinToString("|").lowercase()
        printCache[cardId] = marks
        return marks
    }

    /** Ob zwei Einträge dieselbe Karte zeigen (etwa Original und Jubiläums-Nachdruck). */
    fun samePrint(cardId: String, otherId: String): Boolean {
        val marks = printMarks(cardId) ?: return false
        return marks == printMarks(otherId)
    }

    /** Namenssuche (enthält, Groß-/Kleinschreibung egal). */
    fun searchByName(lang: String, name: String): List<CardBrief> {
        val body = httpGet("$BASE/$lang/cards?name=${enc(name)}") ?: return emptyList()
        return parseBriefs(JSONArray(body)).filter { isPrinted(it.id) }
    }

    private fun parsePrices(cm: JSONObject, suffix: String): Prices? {
        val p = Prices(
            trend = cm.dbl("trend$suffix"),
            avg = cm.dbl("avg$suffix"),
            low = cm.dbl("low$suffix"),
            avg1 = cm.dbl("avg1$suffix"),
            avg7 = cm.dbl("avg7$suffix"),
            avg30 = cm.dbl("avg30$suffix"),
        )
        return p.takeIf { it.hasAny }
    }

    private fun fetchCard(lang: String, id: String): CardInfo? {
        val body = httpGet("$BASE/$lang/cards/${enc(id)}") ?: return null
        val o = JSONObject(body)
        val realId = o.str("id") ?: return null
        // Der Server sucht bei unbekannter ID ersatzweise nach dem Namen – das wollen wir nicht.
        if (!realId.equals(id, ignoreCase = true)) return null
        val set = o.optJSONObject("set")
        val cm = o.optJSONObject("pricing")?.optJSONObject("cardmarket")
        return CardInfo(
            id = realId,
            lang = lang,
            name = o.str("name") ?: realId,
            localId = o.str("localId") ?: "",
            setId = set?.str("id") ?: "",
            setName = set?.str("name") ?: "",
            officialCount = set?.optJSONObject("cardCount")?.optInt("official", -1)?.takeIf { it > 0 },
            rarity = o.str("rarity"),
            imageBase = o.str("image"),
            normal = cm?.let { parsePrices(it, "") },
            holo = cm?.let { parsePrices(it, "-holo") },
            priceUpdated = cm?.str("updated"),
            idProduct = cm?.let { if (it.has("idProduct") && !it.isNull("idProduct")) it.optLong("idProduct") else null },
        )
    }

    /**
     * Karte mit Preisen. Fehlt sie in der gewünschten Sprache, wird die englische
     * genommen; fehlen nur Preise oder Bild, werden sie aus der englischen ergänzt.
     */
    fun card(lang: String, id: String): CardInfo? {
        val local = fetchCard(lang, id)
        if (lang == "en") return local
        if (local != null && (local.hasNormal || local.hasHolo) && local.imageBase != null) return local
        val english = fetchCard("en", id) ?: return local
        if (local == null) return english
        val takePrices = !(local.hasNormal || local.hasHolo)
        return local.copy(
            imageBase = local.imageBase ?: english.imageBase,
            normal = if (takePrices) english.normal else local.normal,
            holo = if (takePrices) english.holo else local.holo,
            priceUpdated = if (takePrices) english.priceUpdated else local.priceUpdated,
            idProduct = local.idProduct ?: english.idProduct,
            officialCount = local.officialCount ?: english.officialCount,
        )
    }
}
