package com.zeroxion.cardscanner

import com.zeroxion.cardscanner.data.TcgDexApi
import com.zeroxion.cardscanner.scan.CardMatcher
import com.zeroxion.cardscanner.scan.ParsedCard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Prüft die App-Logik gegen die echte TCGdex-API (Sets, Suche, Preise, Kartenzuordnung).
 * Läuft nur mit der Umgebungsvariable LIVE_API=1 – siehe Workflow "API-Check".
 */
class LiveApiTest {

    @Before
    fun onlyWhenRequested() {
        assumeTrue(System.getenv("LIVE_API") == "1")
    }

    @Test
    fun setsAndSetCards() {
        val sets = TcgDexApi.sets()
        println("LIVE sets: ${sets.size}")
        assertTrue(sets.size > 100)
        val swsh3 = sets.firstOrNull { it.id == "swsh3" }
        println("LIVE swsh3: $swsh3")
        assertNotNull(swsh3)
        assertTrue(swsh3!!.official > 0)

        val de = TcgDexApi.setCards("de", "swsh3")
        val en = TcgDexApi.setCards("en", "swsh3")
        println("LIVE swsh3 cards de=${de.size} en=${en.size} first=${(de + en).firstOrNull()}")
        assertTrue(en.isNotEmpty())
    }

    @Test
    fun cardWithCardmarketPrices() {
        val en = TcgDexApi.card("en", "swsh3-136")
        println("LIVE card en: $en")
        assertNotNull(en)
        assertEquals("swsh3", en!!.setId)
        assertTrue("Cardmarket-Preise fehlen", en.hasNormal || en.hasHolo)
        assertNotNull(en.imageUrl())

        val de = TcgDexApi.card("de", "swsh3-136")
        println("LIVE card de: $de")
        assertNotNull(de)
        assertTrue("Preise in der deutschen Karte fehlen", de!!.hasNormal || de.hasHolo)

        val newer = TcgDexApi.card("de", "sv03-125")
        println("LIVE card sv03-125 de: $newer")
    }

    /** Zeigt, was die Datenbank für die übrigen Scan-Sprachen liefert (Sets, Karten, Preise). */
    @Test
    fun otherScanLanguages() = runBlocking {
        for (lang in listOf("fr", "ja", "ko", "zh-tw", "zh-cn")) {
            val asian = com.zeroxion.cardscanner.data.ScanLangs.isAsian(lang)
            val sets = runCatching { TcgDexApi.sets(lang) }.getOrDefault(emptyList())
            val probe = if (asian) sets.filter { it.official > 20 }.takeLast(3) else sets.filter { it.id == "sv03" }
            var withCards = 0
            var withPrice = 0
            var sample = ""
            var matched = ""
            for (set in probe) {
                val cards = runCatching { TcgDexApi.setCards(lang, set.id) }.getOrDefault(emptyList())
                val brief = cards.getOrNull(cards.size / 2) ?: continue
                withCards++
                val card = runCatching { TcgDexApi.card(lang, brief.id) }.getOrNull() ?: continue
                if (card.hasNormal || card.hasHolo) withPrice++
                sample = "${card.id} ${card.name} ${card.numberLabel} trend=${card.prices(card.defaultVariant)?.headline} image=${card.imageBase != null}"
                val total = card.officialCount ?: continue
                val found = CardMatcher.find(ParsedCard(listOf(card.name), card.localId, total), lang)
                matched = "${found.firstOrNull()?.id} (erwartet ${card.id})"
            }
            println("LIVE lang=$lang sets=${sets.size} probe=${probe.map { it.id }} mitKarten=$withCards mitPreis=$withPrice sample=[$sample] matcher=[$matched]")
        }
        assertTrue(TcgDexApi.sets("ja").isNotEmpty())
    }

    @Test
    fun unknownCardIsNull() {
        assertEquals(null, TcgDexApi.card("en", "gibtesnicht-999"))
    }

    @Test
    fun nameSearch() {
        val de = TcgDexApi.searchByName("de", "Glurak")
        val en = TcgDexApi.searchByName("en", "Charizard")
        val withSpace = TcgDexApi.searchByName("de", "Glurak ex")
        println("LIVE search Glurak=${de.size} Charizard=${en.size} 'Glurak ex'=${withSpace.size} sample=${withSpace.take(3)}")
        assertTrue(de.isNotEmpty())
        assertTrue(en.isNotEmpty())
        assertTrue(withSpace.isNotEmpty())
    }

    @Test
    fun matcherFindsCardFromScannedText() = runBlocking {
        val target = TcgDexApi.card("de", "swsh3-136")!!
        val total = target.officialCount!!
        println("LIVE matcher target: ${target.name} ${target.localId}/$total")

        // Wie nach einem sauberen Scan: Name + Nummer + Setgröße.
        val full = CardMatcher.find(ParsedCard(listOf(target.name), target.localId, total), "de")
        println("LIVE matcher full: ${full.map { it.id }}")
        assertEquals("swsh3-136", full.first().id)

        // Name falsch gelesen, Nummer stimmt.
        val numberOnly = CardMatcher.find(ParsedCard(listOf("Xyzqwv"), target.localId, total), "de")
        println("LIVE matcher numberOnly: ${numberOnly.map { it.id }}")
        assertTrue(numberOnly.any { it.id == "swsh3-136" })

        // Nummer nicht lesbar, nur der Name.
        val nameOnly = CardMatcher.find(ParsedCard(listOf(target.name), null, null), "de")
        println("LIVE matcher nameOnly: ${nameOnly.map { it.id + " " + it.name }}")
        assertTrue(nameOnly.isNotEmpty())

        // Aufdruck "Glurak ex", in der Datenbank "Glurak-ex" (Obsidian Flammen 125/197).
        val hyphen = CardMatcher.find(ParsedCard(listOf("Glurak ex"), "125", 197), "de")
        println("LIVE matcher hyphen: ${hyphen.map { it.id + " " + it.name }}")
        assertEquals("sv03-125", hyphen.first().id)

        val hyphenNoTotal = CardMatcher.find(ParsedCard(listOf("Glurak ex"), "125", null), "de")
        println("LIVE matcher hyphenNoTotal: ${hyphenNoTotal.map { it.id + " " + it.name }}")
        assertTrue(hyphenNoTotal.any { it.id == "sv03-125" })

        // Karten der digitalen Pocket-App dürfen nicht als Treffer erscheinen.
        val digital = TcgDexApi.digitalSetIds()
        println("LIVE digital sets: $digital")
        assertTrue(digital.contains("a1"))
        assertTrue(hyphenNoTotal.none { it.setId.lowercase() in digital })
    }

    /** Promos und Energien ohne Setgröße: Set-Kürzel + Nummer, notfalls aus der englischen Datenbank. */
    @Test
    fun matcherFindsCardsBySetCode() = runBlocking {
        assertEquals(listOf("mep"), TcgDexApi.setIdsByCode("MEP"))
        assertEquals(listOf("sv01"), TcgDexApi.setIdsByCode("svi"))
        assertTrue(TcgDexApi.setIdsByCode("QQQ").isEmpty())

        // Basis-Metall-Energie "MEE DE 016": der Name allein ("Basis--Energie") sagt nichts über den Typ.
        val energy = CardMatcher.find(ParsedCard(listOf("Basis- -Energie"), "016", null, "MEE"), "de")
        println("LIVE setCode energy: ${energy.map { it.id + " " + it.name }}")
        assertEquals("mee-016", energy.first().id)
        assertEquals("mee-013", CardMatcher.find(ParsedCard(listOf("Basis- -Energie"), "013", null, "MEE"), "de").first().id)

        // Alola-Kokowei "MEP DE 094": fehlt in der deutschen Datenbank, die englische kennt sie.
        val promo = CardMatcher.find(ParsedCard(listOf("Alola-Kokowei"), "094", null, "MEP", 103), "de")
        println("LIVE setCode promo: ${promo.map { it.id + " " + it.name + " " + it.lang }}")
        assertEquals("mep-094", promo.first().id)
        assertTrue("Preis fehlt", promo.first().hasNormal || promo.first().hasHolo)

        // Regelzeichen am Kürzel gelesen ("JMEP").
        val glued = CardMatcher.find(ParsedCard(listOf("Zapdos"), "098", null, "JMEP", 145), "de")
        println("LIVE setCode glued: ${glued.map { it.id + " " + it.name }}")
        assertEquals("mep-098", glued.first().id)

        // Kürzel und Setgröße zusammen ("OBF DE 125/197").
        val both = CardMatcher.find(ParsedCard(listOf("Glurak ex"), "125", 197, "OBF"), "de")
        assertEquals("sv03-125", both.first().id)
    }

    /** Kürzel nicht lesbar: Pokédex-Nummer oder Name führen mit der Kartennummer trotzdem zur Karte. */
    @Test
    fun matcherFindsPromoWithoutSetCode() = runBlocking {
        val byDex = CardMatcher.find(ParsedCard(listOf("Alola-Kokowei"), "094", null, null, 103), "de")
        println("LIVE dex: ${byDex.map { it.id + " " + it.name }}")
        assertEquals("mep-094", byDex.first().id)

        val unreadableName = CardMatcher.find(ParsedCard(emptyList(), "098", null, null, 145), "de")
        println("LIVE dex ohne Namen: ${unreadableName.map { it.id + " " + it.name }}")
        assertEquals("mep-098", unreadableName.first().id)

        // Ohne Pokédex-Nummer: deutscher Name passt, die Nummer gibt es nur englisch.
        val byName = CardMatcher.find(ParsedCard(listOf("Zapdos"), "098", null), "de")
        println("LIVE name+nummer: ${byName.map { it.id + " " + it.name }}")
        assertEquals("mep-098", byName.first().id)
    }

    /** Jubiläums-Nachdruck mit der Nummer des Originals: beide Karten stehen zur Wahl. */
    @Test
    fun matcherOffersStampedReprint() = runBlocking {
        val mew = CardMatcher.find(ParsedCard(listOf("VMAX Mew VMAX"), "114", 264), "de")
        println("LIVE reprint: ${mew.map { it.id + " " + it.name + " " + it.setName }}")
        assertEquals("swsh8-114", mew[0].id)
        assertEquals("30th-c-026", mew[1].id)

        // Gleicher Name, andere Karte: kein Nachdruck als Vorschlag (die Sammlung enthält ein altes Pikachu).
        val pikachu = CardMatcher.find(ParsedCard(listOf("Pikachu"), "025", 165), "de")
        println("LIVE kein reprint: ${pikachu.map { it.id + " " + it.name }}")
        assertEquals("sv03.5-025", pikachu.first().id)
        assertTrue(pikachu.none { it.setId == "30th-c" })
    }

    @Test
    fun pullRateOfPrismaticSir() {
        val rate = TcgDexApi.pullRate("sv08.5", "sv08.5-161")
        println("LIVE pull rate sv08.5-161: $rate")
        assertNotNull(rate)
        assertEquals(32, rate!!.sameRarity)
        assertEquals(1440L, rate.packs)
    }
}
