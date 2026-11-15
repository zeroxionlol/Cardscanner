package com.zeroxion.cardscanner

import com.zeroxion.cardscanner.ui.TypeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Startet die App wie auf dem Handy – fängt Abstürze beim Öffnen ab. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppStartTest {

    @Test
    fun typeThemesLoad() {
        assertEquals(TypeTheme.FIRE, TypeTheme.of("fire"))
        assertEquals(TypeTheme.CLASSIC, TypeTheme.of(null))
        TypeTheme.entries.forEach { it.heroBrush; it.accentBrush }
    }

    @Test
    fun mainActivityStarts() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertFalse("App hat sich sofort wieder geschlossen", controller.get().isFinishing)
        controller.pause().stop().destroy()
    }
}
