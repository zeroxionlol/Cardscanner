package com.zeroxion.cardscanner

import com.zeroxion.cardscanner.data.PullRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRatesTest {
    @Test
    fun prismaticEvolutionsSpecialIllustrationRare() {
        // 32 SIR, eine SIR in etwa jedem 45. Booster → eine bestimmte etwa in jedem 1.440.
        assertEquals(1440L, PullRates.estimate("sv08.5", "Special illustration rare", 32)?.packs)
    }

    @Test
    fun earlyScarletVioletIsEasier() {
        assertEquals(320L, PullRates.estimate("sv01", "Special illustration rare", 10)?.packs)
        assertEquals(860L, PullRates.estimate("sv05", "Special illustration rare", 10)?.packs)
    }

    @Test
    fun onlyCurrentBoosterSets() {
        assertTrue(PullRates.supports("me01"))
        assertTrue(PullRates.supports("sv03.5"))
        assertFalse(PullRates.supports("svp"))
        assertFalse(PullRates.supports("mep"))
        assertFalse(PullRates.supports("swsh7"))
        assertNull(PullRates.estimate("swsh7", "Secret Rare", 20))
        assertNull(PullRates.estimate("sv04.5", "Shiny rare", 100))
    }
}
