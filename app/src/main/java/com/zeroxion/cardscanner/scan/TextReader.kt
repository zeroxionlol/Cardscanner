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

/** Erkannter Text: das ganze Foto und zusätzlich die vergrößert gelesenen untersten Kartenzeilen. */
data class CardText(val lines: List<OcrLine>, val bottomLines: List<OcrLine>)

/** Texterkennung direkt auf dem Gerät (ML Kit). */
object TextReader {
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

            // Die Kartennummer ist sehr klein gedruckt. Die untersten Kartenzeilen werden deshalb
            // vergrößert ein zweites Mal gelesen – das trifft sie deutlich zuverlässiger.
            // Wo sie liegen, ergibt sich aus dem Text des ersten Durchgangs: die Karte füllt
            // das Foto nicht immer (Galerie, Toploader, größerer Abstand).
            val band = TextLayout.bottomBand(lines, bitmap.width, bitmap.height)
            val factor = TextLayout.zoom(lines, bitmap.width, band.height)
            var bottom = readBand(recognizer, bitmap, band, factor, holo = false)

            // Auf Holo- und Glitzerkarten verschwindet die Nummer oft im Schimmer. Fehlt sie noch,
            // wird derselbe Streifen ohne die Farbreflexe und mit gespreiztem Kontrast gelesen.
            if (!CardTextParser.parse(lines, bottom).hasSetNumber) {
                bottom = bottom + readBand(recognizer, bitmap, band, factor, holo = true)
            }
            return CardText(lines, bottom)
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    /** Liest einen Streifen des Fotos vergrößert; die Zeilen kommen in Koordinaten des ganzen Fotos zurück. */
    private suspend fun readBand(
        recognizer: TextRecognizer,
        bitmap: Bitmap,
        band: Band,
        factor: Float,
        holo: Boolean,
    ): List<OcrLine> {
        if (band.height < 24 || bitmap.width < 24) return emptyList()
        var strip: Bitmap? = null
        var enlarged: Bitmap? = null
        return try {
            strip = if (holo) {
                val pixels = IntArray(bitmap.width * band.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, band.top, bitmap.width, band.height)
                HoloFilter.apply(pixels)
                Bitmap.createBitmap(pixels, bitmap.width, band.height, Bitmap.Config.ARGB_8888)
            } else {
                Bitmap.createBitmap(bitmap, 0, band.top, bitmap.width, band.height)
            }
            enlarged = if (factor > 1.05f) {
                Bitmap.createScaledBitmap(
                    strip,
                    (strip.width * factor).toInt(),
                    (strip.height * factor).toInt(),
                    true,
                )
            } else strip
            recognize(recognizer, enlarged, band.top, enlarged.width.toFloat() / strip.width)
        } catch (e: Exception) {
            emptyList()
        } catch (e: OutOfMemoryError) {
            emptyList()
        } finally {
            if (enlarged != null && enlarged !== strip) enlarged.recycle()
            if (strip != null && strip !== bitmap) strip.recycle()
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
                                left = (box.left / factor).toInt(),
                                right = (box.right / factor).toInt(),
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
