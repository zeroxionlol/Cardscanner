package com.zeroxion.cardscanner.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object ImageUtils {
    /**
     * Kopiert ein Foto (Kamera oder Galerie) in den App-Speicher: aufrecht gedreht
     * und auf eine handliche Größe verkleinert, damit Sammlung und Texterkennung schnell bleiben.
     */
    fun importImage(context: Context, source: Uri, maxDim: Int = 2000): File {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Bild konnte nicht geöffnet werden")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Kein gültiges Bild")

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Bild konnte nicht gelesen werden")

        val rotation = try {
            resolver.openInputStream(source)?.use { stream ->
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
        val upright = if (matrix.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { upright.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (upright !== decoded) decoded.recycle()
        upright.recycle()
        return file
    }
}
