package com.zeroxion.cardscanner.scan

import com.zeroxion.cardscanner.data.CardBrief
import com.zeroxion.cardscanner.data.CardInfo
import com.zeroxion.cardscanner.data.ScanLangs
import com.zeroxion.cardscanner.data.TcgDexApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Sucht zu einem gelesenen Kartentext die passenden Karten in der Datenbank. */
object CardMatcher {
    private class Scored(val brief: CardBrief, val score: Int)

    /** Ab dieser Wertung gilt eine Karte als sicher erkannt; die Namenssuche entfällt dann. */
    private const val CERTAIN = 95

    /**
     * Sammlungen mit Nachdrucken alter Karten samt Jubiläums-Stempel. Auf ihnen steht die Nummer
     * des Originals ("114/264"), in der Datenbank sind sie aber eigene Karten mit eigenem Preis.
     */
    private val stampedReprintSets = listOf("30th-c")

    private fun nameBonus(candidate: String, names: List<String>): Int =
        names.maxOfOrNull { (CardTextParser.similarity(candidate, it) * 20).toInt() } ?: 0

    private fun setIdOf(cardId: String): String = cardId.substringBeforeLast('-')

    /** Beste Treffer zuerst, höchstens acht (plus gestempelte Nachdrucke direkt hinter ihrem Original). */
    suspend fun find(parsed: ParsedCard, lang: String): List<CardInfo> = coroutineScope {
        val scored = LinkedHashMap<String, Scored>()
        fun offer(brief: CardBrief, score: Int) {
            val old = scored[brief.id]
            if (old == null || old.score < score) scored[brief.id] = Scored(brief, score)
        }

        val localId = parsed.localId
        val asian = ScanLangs.isAsian(lang)
        val setTotal = parsed.setTotal
        // Westliche Sprachen teilen sich die Set-Kennungen; die englische Datenbank ist am vollständigsten.
        val english = lang != "en" && !asian

        /**
         * Karten eines Sets mit der gelesenen Nummer. Neue Promos fehlen in der deutschen oder
         * französischen Datenbank oft noch – dann zählt der englische Eintrag.
         */
        fun numbered(setId: String, id: String): List<CardBrief> {
            val local = TcgDexApi.setCards(lang, setId).filter { CardTextParser.sameLocalId(it.localId, id) }
            if (local.isNotEmpty() || !english) return local
            return TcgDexApi.setCards("en", setId).filter { CardTextParser.sameLocalId(it.localId, id) }
        }

        // 1) Set-Kürzel + Nummer ("MEP DE 094"): der einzige Weg zu Promos und Energien ohne Setgröße.
        val setCode = parsed.setCode
        if (localId != null && setCode != null && !asian) {
            val setIds = withContext(Dispatchers.IO) {
                CardTextParser.setCodeCandidates(setCode)
                    .firstNotNullOfOrNull { code -> TcgDexApi.setIdsByCode(code).takeIf { it.isNotEmpty() } }
                    .orEmpty()
            }
            setIds.take(4)
                .map { setId -> async(Dispatchers.IO) { numbered(setId, localId) } }
                .awaitAll().flatten()
                .forEach { offer(it, CERTAIN + nameBonus(it.name, parsed.names)) }
        }

        // 2) Nummer + Setgröße ("136/189") grenzen das Set meist eindeutig ein.
        if (localId != null && setTotal != null) {
            val sets = withContext(Dispatchers.IO) {
                val digital = TcgDexApi.digitalSetIds()
                // Asiatische Karten haben eigene Sets; westliche teilen sich die englische Set-Liste.
                TcgDexApi.sets(if (asian) lang else "en")
                    .filter { it.official == setTotal || it.total == setTotal }
                    .filterNot { it.id.lowercase() in digital }
                    .take(8)
            }
            sets.map { set -> async(Dispatchers.IO) { numbered(set.id, localId) } }
                .awaitAll().flatten()
                .forEach { offer(it, 80 + nameBonus(it.name, parsed.names)) }
        }

        // 3) Pokédex-Nummer + Kartennummer: unabhängig von Sprache und Schrift des Namens.
        val dexId = parsed.dexId
        if (localId != null && dexId != null && !asian && scored.values.none { it.score >= 80 }) {
            withContext(Dispatchers.IO) { runCatching { TcgDexApi.searchByDex("en", dexId, localId) }.getOrDefault(emptyList()) }
                .filter { CardTextParser.sameLocalId(it.localId, localId) }
                .asReversed()
                .forEach { offer(it, 70 + nameBonus(it.name, parsed.names)) }
        }

        // 4) Namenssuche, mit der Nummer gegengeprüft.
        for (name in parsed.names.take(2)) {
            if (scored.values.any { it.score >= CERTAIN }) break
            for (term in CardTextParser.searchTerms(name)) {
                val local = withContext(Dispatchers.IO) { TcgDexApi.searchByName(lang, term) }
                val numberFound = localId != null && local.any { CardTextParser.sameLocalId(it.localId, localId) }
                // Englisch dazu, wenn die Sprache nichts kennt – oder zwar den Namen, aber nicht diese Nummer.
                val foreign = when {
                    !english || numberFound -> emptyList()
                    local.isEmpty() -> withContext(Dispatchers.IO) { TcgDexApi.searchByName("en", term) }
                    localId != null -> withContext(Dispatchers.IO) { TcgDexApi.searchByName("en", term) }
                        .filter { CardTextParser.sameLocalId(it.localId, localId) }
                    else -> emptyList()
                }
                val hits = local + foreign
                if (hits.isEmpty()) continue
                // Die API liefert alt → neu; bei gleicher Wertung sollen neuere Karten vorn stehen.
                for (hit in hits.asReversed()) {
                    val idMatch = localId != null && CardTextParser.sameLocalId(hit.localId, localId)
                    val base = when {
                        idMatch -> 60
                        localId == null -> 30
                        else -> 5
                    }
                    offer(hit, base + nameBonus(hit.name, listOf(name)))
                }
                break
            }
        }

        val ranked = scored.values.sortedByDescending { it.score }.take(8)

        // 5) Gestempelte Nachdrucke gleichnamiger Karten als Alternative anbieten.
        val reprintOf = LinkedHashMap<String, String>()
        val reprints = ArrayList<CardBrief>()
        val originals = ranked.take(3).filter { it.score >= 60 && setIdOf(it.brief.id) !in stampedReprintSets }
        if (originals.isNotEmpty() && !asian) {
            val pool = withContext(Dispatchers.IO) {
                runCatching {
                    stampedReprintSets.flatMap { setId ->
                        TcgDexApi.setCards(lang, setId) + if (english) TcgDexApi.setCards("en", setId) else emptyList()
                    }
                }.getOrDefault(emptyList())
            }
            for (original in originals) {
                // Gleicher Name genügt nicht ("Pikachu" gibt es hundertfach): es muss dieselbe Karte sein.
                val twin = pool.filter { CardTextParser.similarity(it.name, original.brief.name) >= 0.99 }
                    .distinctBy { it.id }
                    .firstOrNull { candidate ->
                        withContext(Dispatchers.IO) {
                            runCatching { TcgDexApi.samePrint(candidate.id, original.brief.id) }.getOrDefault(false)
                        }
                    } ?: continue
                if (reprintOf.putIfAbsent(twin.id, original.brief.id) == null) reprints += twin
            }
        }

        val wanted = ranked.filterNot { it.brief.id in reprintOf }.map { it.brief.id to it.score } +
            reprints.map { it.id to 0 }
        val loaded = wanted.map { (id, score) ->
            async(Dispatchers.IO) {
                runCatching { TcgDexApi.card(lang, id) }.getOrNull()?.let { it to score }
            }
        }.awaitAll().filterNotNull()

        val main = loaded.filterNot { it.first.id in reprintOf }
            .sortedByDescending { (card, score) ->
                score + if (setTotal != null && card.officialCount == setTotal) 30 else 0
            }
            .map { it.first }
        val twins = loaded.filter { it.first.id in reprintOf }.map { it.first }
        main.flatMap { card -> listOf(card) + twins.filter { reprintOf[it.id] == card.id } }
    }
}
