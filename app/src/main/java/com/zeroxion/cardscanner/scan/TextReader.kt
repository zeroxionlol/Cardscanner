package com.zeroxion.cardscanner.scan

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Texterkennung direkt auf dem Gerät (ML Kit, lateinische Schrift). */
object TextReader {
    suspend fun read(context: Context, photo: File): List<OcrLine> =
        suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromFilePath(context, Uri.fromFile(photo))
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val lines = ArrayList<OcrLine>()
                        for (block in result.textBlocks) {
                            for (line in block.lines) {
                                val box = line.boundingBox ?: continue
                                lines += OcrLine(line.text, box.top, box.bottom)
                            }
                        }
                        recognizer.close()
                        if (cont.isActive) cont.resume(lines)
                    }
                    .addOnFailureListener { error ->
                        recognizer.close()
                        if (cont.isActive) cont.resumeWithException(error)
                    }
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
}
