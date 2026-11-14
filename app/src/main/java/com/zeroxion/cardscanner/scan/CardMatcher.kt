package com.zeroxion.cardscanner.scan

import com.zeroxion.cardscanner.data.CardBrief
import com.zeroxion.cardscanner.data.CardInfo
import com.zeroxion.cardscanner.data.TcgDexApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Sucht zu einem gelesenen Kartentext die passenden Karten in der Datenbank. */
object CardMatcher {
    private class Scored(val brief: CardBrief, val score: Int)

    private fun nameBonus(candidate: String, names: List<String>): Int =
        names.maxOfOrNull { (CardTextParser.similarity(candidate, it) * 20).toInt() } ?: 0

    private fun searchBoth(lang: String, term: String): List<CardBrief> {
        val local = TcgDexApi.searchByName(lang, term)
        if (local.isNotEmpty() || lang == "en") return local
        return TcgDexApi.searchByName("en", term)
    }

    /** Beste Treffer zuerst, höchstens sechs. */
    suspend fun find(parsed: ParsedCard, lang: String): List<CardInfo> = coroutineScope {
        val scored = LinkedHashMap<String, Scored>()
        fun offer(brief: CardBrief, score: Int) {
            val old = scored[brief.id]
            if (old == null || old.score < score) scored[brief.id] = Scored(brief, score)
        }

        val localId = parsed.localId
        val setTotal = parsed.setTotal

        // 1) Nummer + Setgröße ("136/189") grenzen das Set meist eindeutig ein.
        if (localId != null && setTotal != null) {
            val sets = TcgDexApi.sets()
                .filter { it.official == setTotal || it.total == setTotal }
                .take(8)
            sets.map { set ->
                async(Dispatchers.IO) {
                    val cards = TcgDexApi.setCards(lang, set.id)
                    if (cards.isEmpty() && lang != "en") TcgDexApi.setCards("en", set.id) else cards
                }
            }.awaitAll().flatten()
                .filter { CardTextParser.sameLocalId(it.localId, localId) }
                .forEach { offer(it, 80 + nameBonus(it.name, parsed.names)) }
        }

        // 2) Namenssuche, mit der Nummer gegengeprüft.
        for (name in parsed.names.take(2)) {
            if (scored.values.any { it.score >= 95 }) break
            for (term in CardTextParser.searchTerms(name)) {
                val hits = searchBoth(lang, term)
                if (hits.isEmpty()) continue
                for (hit in hits) {
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

        val top = scored.values.sortedByDescending { it.score }.take(6)
        top.map { s ->
            async(Dispatchers.IO) {
                val card = runCatching { TcgDexApi.card(lang, s.brief.id) }.getOrNull()
                card?.let { it to s.score }
            }
        }.awaitAll()
            .filterNotNull()
            .sortedByDescending { (card, score) ->
                score + if (setTotal != null && card.officialCount == setTotal) 30 else 0
            }
            .map { it.first }
    }
}
