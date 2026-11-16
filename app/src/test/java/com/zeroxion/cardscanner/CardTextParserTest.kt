package com.zeroxion.cardscanner

import com.zeroxion.cardscanner.scan.CardTextParser
import com.zeroxion.cardscanner.scan.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTextParserTest {

    @Test
    fun germanCardWithNumber() {
        val lines = listOf(
            OcrLine("PHASE 2", 120, 150),
            OcrLine("Glurak ex", 110, 190),
            OcrLine("KP 330", 120, 180),
            OcrLine("Entwickelt sich aus Glutexo", 200, 225),
            OcrLine("Brennende Finsternis 180+", 900, 950),
            OcrLine("Schwäche x2 Resistenz Rückzug", 1500, 1530),
            OcrLine("Illus. PLANETA Mochizuki", 1640, 1660),
            OcrLine("G OBF DE 125/197", 1650, 1680),
        )
        val parsed = CardTextParser.parse(lines)
        assertEquals("Glurak ex", parsed.names.first())
        assertEquals("125", parsed.localId)
        assertEquals(197, parsed.setTotal)
    }

    @Test
    fun hpOnSameLineAsName() {
        val lines = listOf(
            OcrLine("BASIS Pikachu KP 60", 50, 110),
            OcrLine("Donnerschock 20", 600, 640),
            OcrLine("049/165", 1400, 1425),
        )
        val parsed = CardTextParser.parse(lines)
        assertEquals("Pikachu", parsed.names.first())
        assertEquals("049", parsed.localId)
        assertEquals(165, parsed.setTotal)
    }

    @Test
    fun galleryNumberAndPromo() {
        val gallery = CardTextParser.parse(
            listOf(OcrLine("Mew V", 40, 100), OcrLine("TG12/TG30", 1400, 1420)),
        )
        assertEquals("TG12", gallery.localId)
        assertEquals(30, gallery.setTotal)

        val promo = CardTextParser.parse(
            listOf(OcrLine("Evoli", 40, 100), OcrLine("Angriff 30", 700, 730), OcrLine("SWSH 123", 1400, 1420)),
        )
        assertEquals("SWSH123", promo.localId)
        assertNull(promo.setTotal)
    }

    @Test
    fun enlargedBottomStripWinsForTheNumber() {
        // Im ganzen Foto wurde die Nummer falsch gelesen, im vergrößerten Rand richtig.
        val lines = listOf(
            OcrLine("BASIS Evoli KP 70", 40, 110),
            OcrLine("Ruckzuckhieb 20+", 900, 950),
            OcrLine("111/123", 1700, 1720),
        )
        val bottom = listOf(
            OcrLine("Illus. En Morikura", 1660, 1680),
            OcrLine("J SCR DE 117/128", 1700, 1722),
        )
        val parsed = CardTextParser.parse(lines, bottom)
        assertEquals("Evoli", parsed.names.first())
        assertEquals("117", parsed.localId)
        assertEquals(128, parsed.setTotal)
    }

    @Test
    fun commonMisreadsInTheNumberAreRepaired() {
        val letterForOne = CardTextParser.parse(listOf(OcrLine("Evoli", 40, 100), OcrLine("I17/128", 1400, 1420)))
        assertEquals("117", letterForOne.localId)
        assertEquals(128, letterForOne.setTotal)

        val mixed = CardTextParser.parse(listOf(OcrLine("Evoli", 40, 100), OcrLine("1l7/l28", 1400, 1420)))
        assertEquals("117", mixed.localId)
        assertEquals(128, mixed.setTotal)

        val zero = CardTextParser.parse(listOf(OcrLine("Evoli", 40, 100), OcrLine("O49/165", 1400, 1420)))
        assertEquals("049", zero.localId)
        assertEquals(165, zero.setTotal)
    }

    @Test
    fun japaneseAndFrenchCards() {
        val japanese = CardTextParser.parse(
            listOf(
                OcrLine("たね イーブイ HP 70", 40, 110),
                OcrLine("でんこうせっか 20+", 900, 950),
                OcrLine("SV8a 126/187", 1700, 1722),
            ),
        )
        assertEquals("イーブイ", japanese.names.first())
        assertEquals("126", japanese.localId)
        assertEquals(187, japanese.setTotal)

        val twoChars = CardTextParser.parse(listOf(OcrLine("基礎 伊布 HP 70", 40, 110), OcrLine("126/187", 1700, 1722)))
        assertEquals("伊布", twoChars.names.first())

        val french = CardTextParser.parse(
            listOf(OcrLine("DE BASE Évoli PV 70", 40, 110), OcrLine("133/165", 1700, 1722)),
        )
        assertEquals("Évoli", french.names.first())
        assertEquals("133", french.localId)
    }

    @Test
    fun setCodeNextToTheNumber() {
        val parsed = CardTextParser.parse(
            listOf(OcrLine("Glurak ex", 110, 190), OcrLine("G OBF DE 125/197", 1650, 1680)),
        )
        assertEquals("125", parsed.localId)
        assertEquals(197, parsed.setTotal)
        assertEquals("OBF", parsed.setCode)
        assertTrue(parsed.hasSetNumber)
    }

    /** Promo ohne Setgröße: "MEP DE 094" (Alola-Kokowei, Jubiläums-Promo). */
    @Test
    fun promoWithSetCodeInsteadOfSetSize() {
        val lines = listOf(
            OcrLine("PHASE 1 Alola-Kokowei KP 150", 300, 370),
            OcrLine("Entwickelt sich aus Owei", 385, 410),
            OcrLine("Nr. 0103 Palmfrucht-Pokémon Größe: 10,9 m Gewicht: 415,6 kg", 885, 905),
            OcrLine("Fähigkeit Hoch hinaus", 965, 1010),
            OcrLine("Wenn an dieses Pokémon 6 oder mehr -Energien angelegt", 1020, 1050),
            OcrLine("sind, erhält es +250 KP.", 1065, 1095),
            OcrLine("Megasauger 150", 1160, 1210),
            OcrLine("Heile 50 Schadenspunkte bei diesem Pokémon.", 1220, 1250),
            OcrLine("Schwäche x2 Resistenz Rückzug", 1395, 1425),
            OcrLine("Illustr. yuu", 1470, 1490),
            OcrLine("©2026 Pokémon/Nintendo/Creatures/GAME FREAK", 1540, 1555),
        )
        val bottom = listOf(
            OcrLine("Illustr. yuu", 1470, 1490),
            OcrLine("J MEP DE 094", 1500, 1525),
            OcrLine("©2026 Pokémon/Nintendo/Creatures/GAME FREAK", 1540, 1555),
        )
        val parsed = CardTextParser.parse(lines, bottom)
        assertEquals("Alola-Kokowei", parsed.names.first())
        assertEquals("094", parsed.localId)
        assertNull(parsed.setTotal)
        assertEquals("MEP", parsed.setCode)
        assertEquals(103, parsed.dexId)
        assertTrue(parsed.hasSetNumber)
    }

    /** Energie: "MEE DE 016 Illustr. YOSHIROTTEN" – Nummer und Zeichner in einer Zeile. */
    @Test
    fun energyCardWithSetCode() {
        val lines = listOf(
            OcrLine("Basis-Energie", 310, 335),
            OcrLine("ENERGIE", 305, 345),
            OcrLine("Basis- -Energie", 370, 420),
            OcrLine("MEE DE 016 Illustr. YOSHIROTTEN", 1495, 1520),
            OcrLine("©2026 Pokémon/Nintendo/Creatures/GAME FREAK", 1530, 1545),
        )
        val parsed = CardTextParser.parse(lines)
        assertEquals("016", parsed.localId)
        assertEquals("MEE", parsed.setCode)
        assertNull(parsed.setTotal)
        assertNull(parsed.dexId)
    }

    @Test
    fun setCodeSurvivesTypicalMisreads() {
        fun bottomLine(text: String) = CardTextParser.parse(
            listOf(OcrLine("BASIS Zapdos KP 120", 300, 360), OcrLine("Donnernder Blitz 210", 1200, 1250), OcrLine(text, 1500, 1525)),
        )

        // Sprachkürzel klebt am Set-Kürzel.
        val glued = bottomLine("MEPDE 098")
        assertEquals("MEP", glued.setCode)
        assertEquals("098", glued.localId)

        // Regelzeichen klebt davor: beide Schreibweisen werden in der Datenbank probiert.
        val mark = bottomLine("JMEP DE 098")
        assertEquals("098", mark.localId)
        assertEquals(listOf("JMEP", "MEP"), CardTextParser.setCodeCandidates(mark.setCode!!))
        assertEquals(listOf("MEP"), CardTextParser.setCodeCandidates("mep"))

        // Sprachkürzel verlesen oder gar nicht erkannt.
        val noLang = bottomLine("J MEP 098")
        assertEquals("MEP", noLang.setCode)
        assertEquals("098", noLang.localId)
        val wrongLang = bottomLine("MEP oE 098")
        assertEquals("MEP", wrongLang.setCode)
        assertEquals("098", wrongLang.localId)

        // Ziffer als Buchstabe gelesen.
        val letter = bottomLine("MEE DE O16 Illustr. YOSHIROTTEN")
        assertEquals("MEE", letter.setCode)
        assertEquals("016", letter.localId)

        // Nur die Nummer: ohne Kürzel, aber als Kartennummer brauchbar.
        val bare = bottomLine("098")
        assertEquals("098", bare.localId)
        assertNull(bare.setCode)
        assertFalse(bare.hasSetNumber)
    }

    @Test
    fun numbersInTheCardTextAreNotCardNumbers() {
        // Schaden, KP und Jahreszahl sind keine Kartennummer.
        val parsed = CardTextParser.parse(
            listOf(
                OcrLine("VMAX Mew VMAX KP 310", 300, 370),
                OcrLine("Dyna-Wunder 130", 1250, 1300),
                OcrLine("Schwäche x2 Resistenz -30 Rückzug", 1380, 1410),
                OcrLine("Illustr. 5ban Graphics", 1440, 1460),
                OcrLine("©2021 Pokémon/Nintendo/Creatures/GAME FREAK", 1500, 1515),
            ),
        )
        assertNull(parsed.localId)
        assertNull(parsed.setCode)
        assertEquals("VMAX Mew VMAX", parsed.names.first())
        assertEquals(listOf("Mew", "VMAX Mew VMAX"), CardTextParser.searchTerms(parsed.names.first()))
    }

    @Test
    fun oldPromoKeepsItsPrefix() {
        // "SWSH" ist hier Teil der Nummer und kein Set-Kürzel.
        val parsed = CardTextParser.parse(
            listOf(OcrLine("Evoli", 40, 100), OcrLine("Angriff 30", 700, 730), OcrLine("SWSH 123", 1400, 1420)),
        )
        assertEquals("SWSH123", parsed.localId)
        assertNull(parsed.setCode)
    }

    @Test
    fun dexNumberInOtherLanguages() {
        fun dex(text: String) = CardTextParser.parse(
            listOf(OcrLine("Zapdos", 40, 100), OcrLine(text, 600, 620), OcrLine("098", 1400, 1420)),
        ).dexId
        assertEquals(145, dex("Nr. 0145 Elektro-Pokémon Größe: 1,6 m Gewicht: 52,6 kg"))
        assertEquals(145, dex("NO. 0145 Electric Pokémon HT: 5'3\" WT: 116 lbs."))
        assertEquals(145, dex("N° 0145 Pokémon Électrique"))
        assertEquals(6, dex("NR. 006 Flammen-Pokémon GR.: 1,7 m"))
        assertNull(dex("Elektro-Pokémon Größe: 1,6 m"))
    }

    @Test
    fun nothingReadable() {
        val parsed = CardTextParser.parse(emptyList())
        assertTrue(parsed.isEmpty)
    }

    @Test
    fun localIdComparison() {
        assertTrue(CardTextParser.sameLocalId("004", "4"))
        assertTrue(CardTextParser.sameLocalId("tg12", "TG12"))
        assertTrue(CardTextParser.sameLocalId("SWSH001", "SWSH1"))
        assertFalse(CardTextParser.sameLocalId("12", "13"))
    }

    @Test
    fun searchTermsGetCoarser() {
        assertEquals(listOf("Glurak", "Glurak ex"), CardTextParser.searchTerms("Glurak ex"))
        assertEquals(listOf("Glurak", "Glurak-ex"), CardTextParser.searchTerms("Glurak-ex"))
        assertEquals(listOf("Pikachu"), CardTextParser.searchTerms("Pikachu"))
        assertEquals(listOf("Professors Forschung", "Professors"), CardTextParser.searchTerms("Professors Forschung"))
    }

    @Test
    fun similarityRanksCloserNamesHigher() {
        assertEquals(1.0, CardTextParser.similarity("Glurak", "glurak"), 0.0001)
        assertEquals(1.0, CardTextParser.similarity("Glurak-ex", "Glurak ex"), 0.0001)
        assertTrue(CardTextParser.similarity("Glurak", "Glurax") > CardTextParser.similarity("Glurak", "Pikachu"))
    }
}
