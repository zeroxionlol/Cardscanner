package com.zeroxion.cardscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.zeroxion.cardscanner.scan.CropFractions
import com.zeroxion.cardscanner.scan.FrameBox
import com.zeroxion.cardscanner.scan.ImageUtils
import com.zeroxion.cardscanner.scan.ScanFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** Prüft den Foto-Import mit echten JPEG-Dateien (Android-Grafik läuft dafür im Test mit). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageUtilsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun jpeg(width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(200, 60, 40))
        val file = tmp.newFile("in_${width}x$height.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file
    }

    private fun sizeOf(file: File): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return bounds.outWidth to bounds.outHeight
    }

    @Test
    fun largePhotoIsImportedAndScaledDown() {
        val source = jpeg(3000, 4000)
        val out = ImageUtils.importImage(tmp.newFolder("photos"), 2000) { FileInputStream(source) }
        assertTrue(out.exists() && out.length() > 0)
        val (w, h) = sizeOf(out)
        println("IMPORT large -> ${w}x$h")
        assertEquals(2000, maxOf(w, h))
        assertEquals(1500, minOf(w, h))
    }

    @Test
    fun smallPhotoKeepsItsSize() {
        val source = jpeg(600, 840)
        val out = ImageUtils.importImage(tmp.newFolder("photos"), 2000) { FileInputStream(source) }
        val (w, h) = sizeOf(out)
        println("IMPORT small -> ${w}x$h")
        assertEquals(600, w)
        assertEquals(840, h)
    }

    @Test
    fun cameraFileUriIsImportedIntoAppStorage() {
        val context = RuntimeEnvironment.getApplication()
        val source = jpeg(1200, 1600)
        val out = ImageUtils.importImage(context, Uri.fromFile(source))
        println("IMPORT uri -> ${out.path} ${sizeOf(out)}")
        assertTrue(out.exists() && out.length() > 0)
        assertEquals(File(context.filesDir, "photos").path, out.parentFile?.path)
        assertEquals(1200 to 1600, sizeOf(out))
    }

    @Test
    fun cropKeepsOnlyTheFramedPart() {
        // Linke Hälfte rot, rechte Hälfte blau; ausgeschnitten wird die rechte Hälfte.
        val bitmap = Bitmap.createBitmap(1000, 800, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(1000 * 800) { i -> if (i % 1000 < 500) Color.RED else Color.BLUE }
        bitmap.setPixels(pixels, 0, 1000, 0, 0, 1000, 800)
        val source = tmp.newFile("halb.jpg")
        FileOutputStream(source).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }

        val out = ImageUtils.importImage(
            tmp.newFolder("photos"),
            2000,
            crop = { width, height ->
                assertEquals(1000, width)
                assertEquals(800, height)
                CropFractions(0.5f, 0f, 0.5f, 1f)
            },
        ) { FileInputStream(source) }

        val result = BitmapFactory.decodeFile(out.path)
        println("IMPORT crop -> ${result.width}x${result.height}")
        assertEquals(500, result.width)
        assertEquals(800, result.height)
        for (x in listOf(40, 250, 460)) {
            val c = result.getPixel(x, 400)
            assertTrue("Pixel $x sollte blau sein", Color.blue(c) > 200 && Color.red(c) < 60)
        }
    }

    @Test
    fun framedCameraPhotoIsCutToTheFrame() {
        // Vorschau 1000 × 2000 mit Rahmen in der Mitte, Foto im selben Format.
        val source = jpeg(1500, 3000)
        val box = FrameBox(250f, 500f, 500f, 700f)
        val out = ImageUtils.importImage(tmp.newFolder("photos"), 2000, crop = { w, h ->
            ScanFrame.toImageFractions(box, 1000f, 2000f, w, h, margin = 0f)
        }) { FileInputStream(source) }
        val (w, h) = sizeOf(out)
        println("IMPORT framed -> ${w}x$h")
        assertEquals(750, w)
        assertEquals(1050, h)
    }

    @Test(expected = IOException::class)
    fun brokenFileIsRejected() {
        val notAnImage = tmp.newFile("kaputt.jpg").apply { writeText("das ist kein Bild") }
        ImageUtils.importImage(tmp.newFolder("photos"), 2000) { FileInputStream(notAnImage) }
    }
}
