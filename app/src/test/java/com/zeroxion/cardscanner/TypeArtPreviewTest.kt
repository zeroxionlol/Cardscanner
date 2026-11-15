package com.zeroxion.cardscanner

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.zeroxion.cardscanner.ui.Night
import com.zeroxion.cardscanner.ui.TypeTheme
import com.zeroxion.cardscanner.ui.drawTypeArt
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rendert die Typ-Motive als PNG (im Build-Workflow als Artefakt "vorschau"). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TypeArtPreviewTest {
    @Test
    fun render() {
        assumeTrue(System.getenv("PREVIEW") == "1")
        val out = File("build/previews").apply { mkdirs() }
        val w = 720
        val h = 1460
        TypeTheme.entries.forEach { theme ->
            listOf(0.1f, 0.37f).forEachIndexed { i, t ->
                val image = ImageBitmap(w, h)
                CanvasDrawScope().draw(Density(1.85f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
                    drawRect(Brush.verticalGradient(listOf(theme.backgroundTop, Night)))
                    theme.glows.forEachIndexed { g, c ->
                        val center = listOf(Offset(0f, h * 0.04f), Offset(w * 1f, h * 0.3f), Offset(0f, h * 0.72f), Offset(w * 1f, h * 1f))[g]
                        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.3f), c.copy(alpha = 0f)), center, h * 0.55f), h * 0.55f, center)
                    }
                    drawTypeArt(theme, t)
                }
                File(out, "${theme.key}-$i.png").outputStream().use {
                    image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}
