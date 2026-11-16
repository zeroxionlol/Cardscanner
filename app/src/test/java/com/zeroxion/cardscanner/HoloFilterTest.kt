package com.zeroxion.cardscanner

import com.zeroxion.cardscanner.scan.HoloFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoloFilterTest {
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun gray(pixel: Int) = pixel and 0xFF

    /** Bunter Holo-Schimmer wird gleichmäßig hell, dunkler Text bleibt dunkel. */
    @Test
    fun rainbowBackgroundBecomesEvenAndTextStaysDark() {
        val rainbow = listOf(argb(230, 40, 40), argb(40, 230, 40), argb(40, 40, 230), argb(230, 230, 40), argb(230, 40, 230))
        val text = argb(35, 30, 40)
        val pixels = IntArray(1000) { i -> if (i % 5 == 0) text else rainbow[i % rainbow.size] }
        val background = pixels.indices.filter { it % 5 != 0 }
        HoloFilter.apply(pixels)

        val light = background.map { gray(pixels[it]) }
        assertEquals("Schimmer nicht gleichmäßig", light.min(), light.max())
        assertTrue(light.min() >= 250)
        assertTrue(gray(pixels[0]) <= 5)
        // Graubild: alle Kanäle gleich, deckend.
        assertEquals(argb(gray(pixels[0]), gray(pixels[0]), gray(pixels[0])), pixels[0])
    }

    /** Blasse Schrift auf dunklem Grund (Kartennummer auf Full-Art-Karten) bekommt vollen Kontrast. */
    @Test
    fun lowContrastIsStretched() {
        val pixels = IntArray(1000) { i -> if (i % 4 == 0) argb(95, 90, 110) else argb(50, 40, 60) }
        HoloFilter.apply(pixels)
        assertTrue(gray(pixels[0]) >= 250)
        assertTrue(gray(pixels[1]) <= 5)
    }

    /** Einzelne Glanzpunkte dürfen die Spreizung nicht aufbrauchen. */
    @Test
    fun sparklesDoNotEatTheContrast() {
        val pixels = IntArray(1000) { i ->
            when {
                i % 100 == 0 -> argb(255, 255, 255)
                i % 4 == 1 -> argb(95, 90, 110)
                else -> argb(50, 40, 60)
            }
        }
        HoloFilter.apply(pixels)
        assertTrue(gray(pixels[1]) >= 250)
        assertTrue(gray(pixels[2]) <= 5)
    }

    @Test
    fun flatAndEmptyImagesAreSafe() {
        HoloFilter.apply(IntArray(0))
        val flat = IntArray(50) { argb(120, 120, 120) }
        HoloFilter.apply(flat)
        assertEquals(argb(120, 120, 120), flat[0])
    }
}
