package com.zeroxion.cardscanner

import com.zeroxion.cardscanner.data.CardmarketPriceGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.StringReader

class CardmarketPriceGuideTest {
    // Aufbau wie die echte Datei von Cardmarket (gekürzt).
    private val sample = """
        {"version":1,"createdAt":"2026-11-16T02:48:57+0100","priceGuides":[
        {"idProduct":907966,"idCategory":51,"avg":22.35,"low":18.49,"trend":23.89,"avg1":23.47,"avg7":23.51,"avg30":23.28,"avg-holo":null,"low-holo":null,"trend-holo":0,"avg1-holo":null,"avg7-holo":null,"avg30-holo":null},
        {"idProduct":895609,"idCategory":51,"avg":0.36,"low":0.02,"trend":1.11,"avg1":0.78,"avg7":1.01,"avg30":0.66,"avg-holo":1.5,"low-holo":0.9,"trend-holo":2.25,"avg1-holo":null,"avg7-holo":2.1,"avg30-holo":2.0},
        {"idProduct":9079,"idCategory":51,"avg":5,"low":4,"trend":6,"avg1":null,"avg7":null,"avg30":null,"avg-holo":null,"low-holo":null,"trend-holo":null,"avg1-holo":null,"avg7-holo":null,"avg30-holo":null},
        {"idProduct":111,"idCategory":51,"avg":null,"low":null,"trend":0,"avg1":null,"avg7":null,"avg30":null,"avg-holo":null,"low-holo":null,"trend-holo":0,"avg1-holo":null,"avg7-holo":null,"avg30-holo":null}
        ]}
    """.trimIndent()

    @Test
    fun readsOnlyTheWantedProducts() {
        val guide = CardmarketPriceGuide.parse(StringReader(sample), setOf(907966L, 895609L, 12345L))
        assertEquals(setOf(907966L, 895609L), guide.entries.keys)

        val normalOnly = guide.entries.getValue(907966L)
        assertEquals(23.89, normalOnly.normal!!.trend!!, 0.0001)
        assertEquals(18.49, normalOnly.normal!!.low!!, 0.0001)
        assertEquals(23.28, normalOnly.normal!!.avg30!!, 0.0001)
        // "trend-holo": 0 ohne weitere Werte ist kein Holo-Preis.
        assertNull(normalOnly.holo)

        val both = guide.entries.getValue(895609L)
        assertEquals(1.11, both.normal!!.trend!!, 0.0001)
        assertEquals(2.25, both.holo!!.trend!!, 0.0001)
        assertEquals(2.1, both.holo!!.avg7!!, 0.0001)
        assertNull(both.holo!!.avg1)
    }

    @Test
    fun updatedIsAnIsoInstantLikeTcgDex() {
        val guide = CardmarketPriceGuide.parse(StringReader(sample), setOf(907966L))
        assertEquals("2026-11-16T01:48:57Z", guide.updated)
        assertEquals(java.time.Instant.parse("2026-11-16T01:48:57Z"), java.time.Instant.parse(guide.updated))
    }

    /** Ähnliche Nummern ("9079" in "907966") und Produkte ganz ohne Preise werden nicht verwechselt. */
    @Test
    fun similarNumbersAndEmptyEntries() {
        val guide = CardmarketPriceGuide.parse(StringReader(sample), setOf(9079L, 111L))
        assertEquals(setOf(9079L), guide.entries.keys)
        assertEquals(6.0, guide.entries.getValue(9079L).normal!!.trend!!, 0.0001)
    }

    /** Die Datei wird in Stücken gelesen; Einträge über eine Stückgrenze hinweg bleiben ganz. */
    @Test
    fun entriesAcrossBufferBoundaries() {
        val many = buildString {
            append("""{"version":1,"createdAt":"2026-11-16T02:48:57+0100","priceGuides":[""")
            for (id in 1..3000) {
                if (id > 1) append(',')
                append("""{"idProduct":$id,"idCategory":51,"avg":$id.5,"low":1,"trend":$id.25,"avg1":null,"avg7":null,"avg30":null,"avg-holo":null,"low-holo":null,"trend-holo":null,"avg1-holo":null,"avg7-holo":null,"avg30-holo":null}""")
            }
            append("]}")
        }
        assertTrue(many.length > 3 * 64 * 1024)
        val wanted = (1L..3000L).toSet()
        val guide = CardmarketPriceGuide.parse(StringReader(many), wanted)
        assertEquals(wanted, guide.entries.keys)
        assertEquals(1234.25, guide.entries.getValue(1234L).normal!!.trend!!, 0.0001)
    }

    @Test
    fun brokenFileIsReported() {
        try {
            CardmarketPriceGuide.parse(StringReader("<html>Fehler</html>"), setOf(1L))
            fail("Ungültige Datei wurde angenommen")
        } catch (expected: java.io.IOException) {
        }
        // Abgebrochener Download: die vollständigen Einträge davor zählen trotzdem.
        val cut = sample.substring(0, sample.indexOf("""{"idProduct":895609""") + 40)
        assertEquals(setOf(907966L), CardmarketPriceGuide.parse(StringReader(cut), setOf(907966L, 895609L)).entries.keys)
    }

    @Test
    fun freshnessOfTheStoredFile() {
        val dir = File(System.getProperty("java.io.tmpdir"), "cardscanner-guide-test-${System.nanoTime()}")
        try {
            assertFalse(CardmarketPriceGuide.isFresh(dir))
            dir.mkdirs()
            val file = File(dir, "price_guide_6.json")
            file.writeText(sample)
            val now = 1_800_000_000_000L
            file.setLastModified(now - 60 * 60 * 1000L)
            assertTrue(CardmarketPriceGuide.isFresh(dir, now))
            // Liegt eine aktuelle Datei bereit, wird sie ohne Netz gelesen.
            assertEquals(setOf(907966L), CardmarketPriceGuide.load(dir, setOf(907966L), now).entries.keys)

            file.setLastModified(now - 7 * 60 * 60 * 1000L)
            assertFalse(CardmarketPriceGuide.isFresh(dir, now))
            // Ohne gewünschte Produkte gibt es nichts zu laden.
            assertTrue(CardmarketPriceGuide.load(dir, emptySet(), now).entries.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
