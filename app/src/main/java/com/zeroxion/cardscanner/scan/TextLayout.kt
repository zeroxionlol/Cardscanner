package com.zeroxion.cardscanner.scan

/** Waagerechter Streifen eines Fotos (Pixelzeilen, [bottom] exklusiv). */
data class Band(val top: Int, val bottom: Int) {
    val height: Int get() = bottom - top
}

/**
 * Schätzt aus den Textzeilen des ersten Lesedurchgangs, wo die Karte im Foto liegt.
 * Die Karte füllt das Bild nicht immer: Fotos aus der Galerie, Karten im Toploader oder
 * aus größerem Abstand. Ein fester "unterer Bildrand" verfehlt die Kartennummer dann.
 * Reines Kotlin, damit es testbar ist.
 */
object TextLayout {
    /** Höhe einer Karte im Verhältnis zur Breite ihres Textes (der Text lässt links und rechts etwas Rand). */
    private const val HEIGHT_PER_TEXT_WIDTH = (88f / 63f) / 0.88f

    /**
     * Streifen mit den untersten Kartenzeilen (Illustrator, Kartennummer, Copyright).
     *
     * Zwei Anhaltspunkte für den unteren Kartenrand: die unterste gelesene Zeile und – aus der
     * Textbreite über das Kartenformat hochgerechnet – die erwartete Kartenhöhe. Der Streifen
     * deckt beide ab; war die unterste Zeile im ersten Durchgang nicht lesbar, wird er dadurch
     * höher statt falsch.
     */
    fun bottomBand(lines: List<OcrLine>, imageWidth: Int, imageHeight: Int): Band {
        val usable = lines.filter { it.text.isNotBlank() && it.bottom > it.top }
        if (usable.isEmpty() || imageHeight <= 0) return Band((imageHeight * 0.78f).toInt(), imageHeight)

        val textTop = usable.minOf { it.top }
        val textBottom = usable.maxOf { it.bottom }
        val span = (textBottom - textTop).toFloat()

        val located = usable.filter { it.right > it.left }
        val textWidth = if (located.isEmpty()) 0 else located.maxOf { it.right } - located.minOf { it.left }
        // Nur wenn der Text einen ordentlichen Teil der Bildbreite einnimmt, taugt er als Maß.
        val fromWidth = if (textWidth >= imageWidth * 0.3f) textWidth * HEIGHT_PER_TEXT_WIDTH else 0f

        val cardHeight = maxOf(span, fromWidth, imageHeight * 0.2f)
        val expectedBottom = if (fromWidth > 0f) textTop + fromWidth * 0.96f else textBottom.toFloat()
        val upper = minOf(textBottom.toFloat(), expectedBottom)
        val lower = maxOf(textBottom.toFloat(), expectedBottom)

        val top = (upper - cardHeight * 0.20f).toInt().coerceIn(0, imageHeight - 1)
        val bottom = (lower + cardHeight * 0.12f).toInt().coerceIn(top + 1, imageHeight)
        return Band(top, bottom)
    }

    /**
     * Vergrößerung für einen Streifen: so, dass die kleine Kartennummer groß genug zum Lesen wird,
     * ohne dass das vergrößerte Bild mehr als [maxPixels] Bildpunkte bekommt.
     */
    fun zoom(lines: List<OcrLine>, imageWidth: Int, bandHeight: Int, maxPixels: Long = 6_000_000L): Float {
        val located = lines.filter { it.right > it.left }
        val textWidth = if (located.isEmpty()) 0 else located.maxOf { it.right } - located.minOf { it.left }
        val reference = if (textWidth >= imageWidth * 0.3f) textWidth else imageWidth
        var factor = (2900f / reference.coerceAtLeast(1)).coerceIn(1f, 3.5f)
        val pixels = imageWidth.toLong() * bandHeight.coerceAtLeast(1)
        val limit = Math.sqrt(maxPixels.toDouble() / pixels.coerceAtLeast(1L)).toFloat()
        if (factor > limit) factor = limit.coerceAtLeast(1f)
        return factor
    }
}
