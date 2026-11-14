package com.zeroxion.cardscanner.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.zeroxion.cardscanner.data.Script
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Erkannter Text: das ganze Foto und zusätzlich der vergrößerte untere Rand. */
data class CardText(val lines: List<OcrLine>, val bottomLines: List<OcrLine>)

/** Texterkennung direkt auf dem Gerät (ML Kit, lateinische Schrift). */
object TextReader {
    /** Anteil der Bildhöhe, ab dem der untere Rand (Kartennummer) extra gelesen wird. */
    private const val STRIP_START = 0.80f

    suspend fun read(photo: File, script: Script = Script.LATIN): CardText {
        val bitmap = BitmapFactory.decodeFile(photo.path) ?: throw IOException("Foto nicht lesbar")
        // Die Modelle für Japanisch, Koreanisch und Chinesisch lesen lateinische Zeichen und Ziffern mit.
        val recognizer = when (script) {
            Script.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            Script.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            Script.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            Script.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        try {
            val lines = recognize(recognizer, bitmap, 0, 1f)

            // Die Kartennummer ist sehr klein gedruckt. Der untere Rand wird deshalb
            // vergrößert ein zweites Mal gelesen – das trifft sie deutlich zuverlässiger.
            var bottom = emptyList<OcrLine>()
            val stripTop = (bitmap.height * STRIP_START).toInt()
            val stripHeight = bitmap.height - stripTop
            if (stripHeight >= 24 && bitmap.width >= 24) {
                val factor = (3200f / bitmap.width).coerceIn(1f, 3f)
                val strip = Bitmap.createBitmap(bitmap, 0, stripTop, bitmap.width, stripHeight)
                val enlarged = if (factor > 1.05f) {
                    Bitmap.createScaledBitmap(
                        strip,
                        (strip.width * factor).toInt(),
                        (strip.height * factor).toInt(),
                        true,
                    )
                } else strip
                val usedFactor = enlarged.width.toFloat() / strip.width
                bottom = try {
                    recognize(recognizer, enlarged, stripTop, usedFactor)
                } catch (e: Exception) {
                    emptyList()
                }
                if (enlarged !== strip) enlarged.recycle()
                if (strip !== bitmap) strip.recycle()
            }
            return CardText(lines, bottom)
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    private suspend fun recognize(
        recognizer: TextRecognizer,
        bitmap: Bitmap,
        offsetY: Int,
        factor: Float,
    ): List<OcrLine> = suspendCancellableCoroutine { cont ->
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    val lines = ArrayList<OcrLine>()
                    for (block in result.textBlocks) {
                        for (line in block.lines) {
                            val box = line.boundingBox ?: continue
                            lines += OcrLine(
                                text = line.text,
                                top = offsetY + (box.top / factor).toInt(),
                                bottom = offsetY + (box.bottom / factor).toInt(),
                            )
                        }
                    }
                    if (cont.isActive) cont.resume(lines)
                }
                .addOnFailureListener { error ->
                    if (cont.isActive) cont.resumeWithException(error)
                }
        } catch (e: Exception) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }
}
