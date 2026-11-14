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
     * Kopiert ein Foto (Kamera oder Galerie) in den App-Speicher: aufrecht gedreht
     * und auf eine handliche Größe verkleinert, damit Sammlung und Texterkennung schnell bleiben.
     */
    fun importImage(context: Context, source: Uri, maxDim: Int = 2000): File {
        val dir = File(context.filesDir, "photos")
        val path = source.path
        return if (source.scheme == "file" && path != null) {
            importImage(dir, maxDim) { FileInputStream(path) }
        } else {
            importImage(dir, maxDim) { context.contentResolver.openInputStream(source) }
        }
    }

    /** Kern der Verarbeitung; [open] liefert bei jedem Aufruf einen frischen Stream auf das Bild. */
    fun importImage(targetDir: File, maxDim: Int = 2000, open: () -> InputStream?): File {
        // 1) Nur die Bildgröße lesen. decodeStream liefert dabei absichtlich kein Bild zurück.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = open() ?: throw IOException("Bild konnte nicht geöffnet werden")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Kein gültiges Bild")

        // 2) Verkleinert laden.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decodeStream = open() ?: throw IOException("Bild konnte nicht geöffnet werden")
        val decoded = decodeStream.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Bild konnte nicht gelesen werden")

        // 3) Drehung aus den Fotodaten übernehmen (fehlt sie, bleibt das Bild wie es ist).
        val rotation = try {
            open()?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }

        val longest = maxOf(decoded.width, decoded.height)
        val scale = if (longest > maxDim) maxDim.toFloat() / longest else 1f
        val matrix = Matrix()
        if (scale < 1f) matrix.postScale(scale, scale)
        if (rotation != 0f) matrix.postRotate(rotation)
        val upright = if (scale >= 1f && rotation == 0f) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        // 4) Als JPEG in den App-Speicher schreiben.
        if (!targetDir.exists() && !targetDir.mkdirs()) throw IOException("Speicherordner fehlt")
        val file = File(targetDir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            if (!upright.compress(Bitmap.CompressFormat.JPEG, 88, out)) {
                throw IOException("Bild konnte nicht gespeichert werden")
            }
        }
        if (upright !== decoded) decoded.recycle()
        upright.recycle()
        return file
    }
}
