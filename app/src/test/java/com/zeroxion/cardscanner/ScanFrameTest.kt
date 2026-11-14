package com.zeroxion.cardscanner

import com.zeroxion.cardscanner.scan.CropFractions
import com.zeroxion.cardscanner.scan.FrameBox
import com.zeroxion.cardscanner.scan.RawRect
import com.zeroxion.cardscanner.scan.ScanFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFrameTest {
    private val eps = 0.0005f

    @Test
    fun frameHasCardShapeAndStaysBetweenHeaderAndControls() {
        // Etwa der Außenbildschirm eines Fold: 1248 × 1972 Pixel.
        val box = ScanFrame.frame(1248f, 1972f, topReserved = 300f, bottomReserved = 640f)
        assertEquals(ScanFrame.CARD_RATIO, box.width / box.height, 0.001f)
        assertTrue(box.top >= 300f)
        assertTrue(box.top + box.height <= 1972f - 640f)
        assertEquals((1248f - box.width) / 2f, box.left, 0.01f)
    }

    @Test
    fun frameIsLimitedByWidthOnTallScreens() {
        val box = ScanFrame.frame(1000f, 4000f, topReserved = 100f, bottomReserved = 100f)
        assertEquals(880f, box.width, 0.01f)
        assertEquals(ScanFrame.CARD_RATIO, box.width / box.height, 0.001f)
    }

    @Test
    fun sameShapePhotoMapsDirectly() {
        val f = ScanFrame.toImageFractions(FrameBox(250f, 500f, 500f, 700f), 1000f, 2000f, 1500, 3000, margin = 0f)
        assertEquals(0.25f, f.left, eps)
        assertEquals(0.25f, f.top, eps)
        assertEquals(0.5f, f.width, eps)
        assertEquals(0.35f, f.height, eps)
    }

    @Test
    fun widerPhotoIsCenteredLikeThePreview() {
        // Vorschau 1000 × 2000, Foto 3000 × 4000: links und rechts ragt das Foto über die Vorschau hinaus.
        val f = ScanFrame.toImageFractions(FrameBox(250f, 500f, 500f, 700f), 1000f, 2000f, 3000, 4000, margin = 0f)
        assertEquals(1000f / 3000f, f.left, eps)
        assertEquals(0.25f, f.top, eps)
        assertEquals(1000f / 3000f, f.width, eps)
        assertEquals(0.35f, f.height, eps)
    }

    @Test
    fun marginGrowsTheCropButNeverLeavesThePhoto() {
        val tight = ScanFrame.toImageFractions(FrameBox(250f, 500f, 500f, 700f), 1000f, 2000f, 1500, 3000, margin = 0f)
        val loose = ScanFrame.toImageFractions(FrameBox(250f, 500f, 500f, 700f), 1000f, 2000f, 1500, 3000, margin = 0.03f)
        assertTrue(loose.width > tight.width && loose.height > tight.height)
        val edge = ScanFrame.toImageFractions(FrameBox(0f, 0f, 1000f, 2000f), 1000f, 2000f, 1500, 3000, margin = 0.1f)
        assertEquals(0f, edge.left, eps)
        assertEquals(1f, edge.width, eps)
        assertEquals(1f, edge.height, eps)
    }

    @Test
    fun rawRectFollowsPhotoRotation() {
        val f = CropFractions(0.1f, 0.2f, 0.5f, 0.6f)
        assertEquals(RawRect(300, 800, 1500, 2400), ScanFrame.toRawRect(f, 3000, 4000, 0))
        // Querformat-Sensor, Foto muss um 90° gedreht werden, um aufrecht zu stehen.
        assertEquals(RawRect(800, 1200, 2400, 1500), ScanFrame.toRawRect(f, 4000, 3000, 90))
        assertEquals(RawRect(1200, 800, 1500, 2400), ScanFrame.toRawRect(f, 3000, 4000, 180))
        assertEquals(RawRect(800, 300, 2400, 1500), ScanFrame.toRawRect(f, 4000, 3000, 270))
    }
}
