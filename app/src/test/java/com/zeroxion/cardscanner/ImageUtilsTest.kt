package com.zeroxion.cardscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.zeroxion.cardscanner.scan.ImageUtils
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

    @Test(expected = IOException::class)
    fun brokenFileIsRejected() {
        val notAnImage = tmp.newFile("kaputt.jpg").apply { writeText("das ist kein Bild") }
        ImageUtils.importImage(tmp.newFolder("photos"), 2000) { FileInputStream(notAnImage) }
    }
}
