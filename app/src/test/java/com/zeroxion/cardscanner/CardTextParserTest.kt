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
