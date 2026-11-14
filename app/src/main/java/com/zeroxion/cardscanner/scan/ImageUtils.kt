package com.zeroxion.cardscanner.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

object ImageUtils {
    /**
     * Kopiert ein Foto (Kamera oder Galerie) in den App-Speicher: aufrecht gedreht,
     * optional auf den Scan-Rahmen zugeschnitten und auf eine handliche Größe verkleinert.
     *
     * [crop] bekommt Breite und Höhe des aufrechten Fotos und liefert den gewünschten Ausschnitt.
     */
    fun importImage(
        context: Context,
        source: Uri,
        maxDim: Int = 2000,
        crop: ((Int, Int) -> CropFractions)? = null,
    ): File {
        val dir = File(context.filesDir, "photos")
        val path = source.path
        return if (source.scheme == "file" && path != null) {
            importImage(dir, maxDim, crop) { FileInputStream(path) }
        } else {
            importImage(dir, maxDim, crop) { context.contentResolver.openInputStream(source) }
        }
    }

    /** Kern der Verarbeitung; [open] liefert bei jedem Aufruf einen frischen Stream auf das Bild. */
    fun importImage(
        targetDir: File,
        maxDim: Int = 2000,
        crop: ((Int, Int) -> CropFractions)? = null,
        open: () -> InputStream?,
    ): File {
        // 1) Nur die Bildgröße lesen. decodeStream liefert dabei absichtlich kein Bild zurück.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = open() ?: throw IOException("Bild konnte nicht geöffnet werden")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val rawWidth = bounds.outWidth
        val rawHeight = bounds.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) throw IOException("Kein gültiges Bild")

        // 2) Drehung aus den Fotodaten und gewünschter Ausschnitt.
        val rotation = readRotation(open)
        val sideways = rotation == 90 || rotation == 270
        val uprightWidth = if (sideways) rawHeight else rawWidth
        val uprightHeight = if (sideways) rawWidth else rawHeight
        val wanted = crop?.invoke(uprightWidth, uprightHeight)
        val fractions = if (wanted != null && wanted.width >= 0.05f && wanted.height >= 0.05f) wanted
        else CropFractions.FULL
        val raw = ScanFrame.toRawRect(fractions, rawWidth, rawHeight, rotation)

        // 3) So laden, dass der Ausschnitt noch genug Auflösung für die Texterkennung hat.
        var sample = 1
        while (maxOf(raw.width, raw.height) / (sample * 2) >= maxDim) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decodeStream = open() ?: throw IOException("Bild konnte nicht geöffnet werden")
        val decoded = decodeStream.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Bild konnte nicht gelesen werden")

        val sx = decoded.width.toFloat() / rawWidth
        val sy = decoded.height.toFloat() / rawHeight
        val x = (raw.left * sx).toInt().coerceIn(0, decoded.width - 1)
        val y = (raw.top * sy).toInt().coerceIn(0, decoded.height - 1)
        val w = Math.round(raw.width * sx).coerceIn(1, decoded.width - x)
        val h = Math.round(raw.height * sy).coerceIn(1, decoded.height - y)

        // 4) Zuschneiden, verkleinern und aufrichten in einem Schritt.
        val longest = maxOf(w, h)
        val scale = if (longest > maxDim) maxDim.toFloat() / longest else 1f
        val matrix = Matrix()
        if (scale < 1f) matrix.postScale(scale, scale)
        if (rotation != 0) matrix.postRotate(rotation.toFloat())
        val whole = x == 0 && y == 0 && w == decoded.width && h == decoded.height
        val result = if (whole && scale >= 1f && rotation == 0) decoded
        else Bitmap.createBitmap(decoded, x, y, w, h, matrix, true)

        // 5) Als JPEG in den App-Speicher schreiben.
        if (!targetDir.exists() && !targetDir.mkdirs()) throw IOException("Speicherordner fehlt")
        val file = File(targetDir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            if (!result.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
                throw IOException("Bild konnte nicht gespeichert werden")
            }
        }
        if (result !== decoded) decoded.recycle()
        result.recycle()
        return file
    }

    /** Drehung im Uhrzeigersinn, die das Foto aufrichtet (fehlt die Angabe: 0). */
    private fun readRotation(open: () -> InputStream?): Int = try {
        open()?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }
}
