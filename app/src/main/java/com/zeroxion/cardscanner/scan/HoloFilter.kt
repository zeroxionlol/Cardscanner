package com.zeroxion.cardscanner.scan

/**
 * Macht Text auf Holo- und Glitzerkarten lesbarer.
 *
 * Holofolie spiegelt in kräftigen Regenbogenfarben, gedruckter Text ist dagegen in allen
 * Farbkanälen dunkel (oder hell). Nimmt man pro Bildpunkt den hellsten Kanal, wird der bunte
 * Schimmer zu gleichmäßigem Hell und der Text bleibt stehen. Danach wird der Kontrast gespreizt,
 * damit auch blasse Schrift auf dunklem Grund (Kartennummer auf Full-Art-Karten) deutlich wird.
 * Reines Kotlin auf ARGB-Bildpunkten, damit es testbar ist.
 */
object HoloFilter {
    /** Wandelt [pixels] (ARGB) an Ort und Stelle in ein kontrastreiches Graubild um. */
    fun apply(pixels: IntArray) {
        if (pixels.isEmpty()) return
        val histogram = IntArray(256)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val v = maxOf(r, g, b)
            pixels[i] = v
            histogram[v]++
        }

        // Die dunkelsten und hellsten 2 % (Glanzpunkte, Schatten) bestimmen nicht die Spreizung.
        val cut = pixels.size / 50
        var low = 0
        var seen = histogram[0]
        while (low < 255 && seen <= cut) seen += histogram[++low]
        var high = 255
        seen = histogram[255]
        while (high > 0 && seen <= cut) seen += histogram[--high]
        if (high <= low) {
            low = 0
            high = 255
        }

        val range = high - low
        val table = IntArray(256) { v ->
            val stretched = ((v - low) * 255 / range).coerceIn(0, 255)
            (0xFF shl 24) or (stretched shl 16) or (stretched shl 8) or stretched
        }
        for (i in pixels.indices) pixels[i] = table[pixels[i]]
    }
}
