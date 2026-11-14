package com.zeroxion.cardscanner.scan

/** Rechteck in Bildschirm-Pixeln (Kameravorschau). */
data class FrameBox(val left: Float, val top: Float, val width: Float, val height: Float)

/** Ausschnitt als Anteil des aufrechten Fotos (0..1). */
data class CropFractions(val left: Float, val top: Float, val width: Float, val height: Float) {
    companion object {
        val FULL = CropFractions(0f, 0f, 1f, 1f)
    }
}

/** Ausschnitt in Pixeln des ungedrehten Fotos. */
data class RawRect(val left: Int, val top: Int, val width: Int, val height: Int)

/** Der Rahmen samt Größe der Vorschau, in der er lag. */
data class FrameCrop(val viewWidth: Float, val viewHeight: Float, val box: FrameBox)

/**
 * Geometrie des gelben Scan-Rahmens: wo er in der Vorschau liegt und welchem
 * Ausschnitt des fertigen Fotos er entspricht. Reines Kotlin, damit es testbar ist.
 */
object ScanFrame {
    const val CARD_RATIO = 63f / 88f

    /** Rahmen im Kartenformat, mittig zwischen Kopfzeile und Bedienleiste. */
    fun frame(viewWidth: Float, viewHeight: Float, topReserved: Float, bottomReserved: Float): FrameBox {
        val available = (viewHeight - topReserved - bottomReserved).coerceAtLeast(viewHeight * 0.3f)
        var height = available * 0.97f
        var width = height * CARD_RATIO
        val maxWidth = viewWidth * 0.88f
        if (width > maxWidth) {
            width = maxWidth
            height = width / CARD_RATIO
        }
        val left = (viewWidth - width) / 2f
        val top = topReserved + (available - height) / 2f
        return FrameBox(left, top, width, height)
    }

    /**
     * Rechnet den Rahmen in einen Ausschnitt des Fotos um. Die Vorschau zeigt das Bild
     * mittig und formatfüllend; [margin] lässt rundherum etwas Luft, falls die Karte
     * nicht exakt im Rahmen liegt.
     */
    fun toImageFractions(
        box: FrameBox,
        viewWidth: Float,
        viewHeight: Float,
        imageWidth: Int,
        imageHeight: Int,
        margin: Float = 0.03f,
    ): CropFractions {
        if (viewWidth <= 0f || viewHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) return CropFractions.FULL
        val scale = maxOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val offsetX = (viewWidth - imageWidth * scale) / 2f
        val offsetY = (viewHeight - imageHeight * scale) / 2f
        val mx = box.width * margin
        val my = box.height * margin
        val left = ((box.left - mx - offsetX) / scale).coerceIn(0f, imageWidth.toFloat())
        val top = ((box.top - my - offsetY) / scale).coerceIn(0f, imageHeight.toFloat())
        val right = ((box.left + box.width + mx - offsetX) / scale).coerceIn(0f, imageWidth.toFloat())
        val bottom = ((box.top + box.height + my - offsetY) / scale).coerceIn(0f, imageHeight.toFloat())
        return CropFractions(
            left / imageWidth,
            top / imageHeight,
            (right - left) / imageWidth,
            (bottom - top) / imageHeight,
        )
    }

    /**
     * Überträgt einen Ausschnitt des aufrechten Fotos auf die ungedrehten Pixel.
     * [rotation] ist die Drehung im Uhrzeigersinn (0, 90, 180, 270), die das Foto aufrichtet.
     */
    fun toRawRect(f: CropFractions, rawWidth: Int, rawHeight: Int, rotation: Int): RawRect {
        val l: Float
        val t: Float
        val w: Float
        val h: Float
        when (((rotation % 360) + 360) % 360) {
            90 -> {
                l = f.top * rawWidth
                t = (1f - f.left - f.width) * rawHeight
                w = f.height * rawWidth
                h = f.width * rawHeight
            }
            180 -> {
                l = (1f - f.left - f.width) * rawWidth
                t = (1f - f.top - f.height) * rawHeight
                w = f.width * rawWidth
                h = f.height * rawHeight
            }
            270 -> {
                l = (1f - f.top - f.height) * rawWidth
                t = f.left * rawHeight
                w = f.height * rawWidth
                h = f.width * rawHeight
            }
            else -> {
                l = f.left * rawWidth
                t = f.top * rawHeight
                w = f.width * rawWidth
                h = f.height * rawHeight
            }
        }
        val left = Math.round(l).coerceIn(0, rawWidth - 1)
        val top = Math.round(t).coerceIn(0, rawHeight - 1)
        val width = Math.round(w).coerceIn(1, rawWidth - left)
        val height = Math.round(h).coerceIn(1, rawHeight - top)
        return RawRect(left, top, width, height)
    }
}
