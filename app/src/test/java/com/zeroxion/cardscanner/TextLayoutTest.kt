package com.zeroxion.cardscanner

import com.zeroxion.cardscanner.scan.OcrLine
import com.zeroxion.cardscanner.scan.TextLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLayoutTest {

    /** Karte füllt den Scan-Rahmen: der Streifen liegt wie bisher am unteren Bildrand. */
    @Test
    fun cardFillingThePhoto() {
        val lines = listOf(
            OcrLine("BASIS Zapdos KP 120", 100, 170, left = 90, right = 1340),
            OcrLine("Donnernder Blitz 210", 1200, 1260, left = 110, right = 1330),
            OcrLine("©2026 Pokémon/Nintendo/Creatures/GAME FREAK", 1895, 1915, left = 520, right = 1050),
        )
        val band = TextLayout.bottomBand(lines, 1432, 2000)
        assertTrue("oben: ${band.top}", band.top in 1400..1700)
        assertEquals(2000, band.bottom)
    }

    /** Foto aus der Galerie: die Karte liegt klein in der Bildmitte, die Nummer weit über dem Bildrand. */
    @Test
    fun smallCardInTheMiddleOfThePhoto() {
        val lines = listOf(
            OcrLine("Basis-Energie", 310, 335, left = 255, right = 580),
            OcrLine("ENERGIE", 305, 345, left = 820, right = 1110),
            OcrLine("Basis- -Energie", 370, 420, left = 295, right = 720),
            OcrLine("©2026 Pokémon/Nintendo/Creatures/GAME FREAK", 1530, 1545, left = 530, right = 890),
        )
        val numberLine = 1505
        val band = TextLayout.bottomBand(lines, 1500, 2000)
        assertTrue("Streifen $band verfehlt die Nummernzeile", numberLine in (band.top + 40)..(band.bottom - 40))
        assertTrue("Streifen zu hoch: $band", band.height < 700)
        // Der alte feste Streifen (unterste 20 % des Fotos) hätte die Zeile verfehlt.
        assertTrue(numberLine < 2000 * 0.80)

        // Kleine Karte, kleine Schrift: stärker vergrößern als bei formatfüllender Karte.
        val zoom = TextLayout.zoom(lines, 1500, band.height)
        assertTrue("Vergrößerung $zoom", zoom > 2.5f)
        assertTrue(1500L * band.height * zoom * zoom <= 6_100_000L)
    }

    /** Unterer Kartentext im ersten Durchgang nicht lesbar: die Kartenhöhe wird aus der Textbreite geschätzt. */
    @Test
    fun bottomTextMissedInTheFirstPass() {
        val lines = listOf(
            OcrLine("Basis-Energie", 310, 335, left = 255, right = 580),
            OcrLine("ENERGIE", 305, 345, left = 820, right = 1110),
        )
        val band = TextLayout.bottomBand(lines, 1500, 2000)
        assertTrue("Streifen $band verfehlt die Nummernzeile", 1505 in band.top..(band.bottom - 40))
    }

    @Test
    fun noTextAndNoPositions() {
        val empty = TextLayout.bottomBand(emptyList(), 1500, 2000)
        assertEquals(2000, empty.bottom)
        assertTrue(empty.top in 1400..1700)

        // Zeilen ohne waagerechte Lage (ältere Aufrufer): nur die Höhen zählen.
        val plain = TextLayout.bottomBand(
            listOf(OcrLine("Pikachu", 100, 160), OcrLine("049/165", 1880, 1905)),
            1432,
            2000,
        )
        assertTrue(1890 in plain.top..plain.bottom)
        assertEquals(1f, TextLayout.zoom(emptyList(), 4000, 2000), 0.25f)
    }
}
