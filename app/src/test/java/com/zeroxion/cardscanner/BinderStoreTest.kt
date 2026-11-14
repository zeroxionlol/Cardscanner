package com.zeroxion.cardscanner

import com.zeroxion.cardscanner.data.BinderLayout
import com.zeroxion.cardscanner.data.CardInfo
import com.zeroxion.cardscanner.data.CollectionItem
import com.zeroxion.cardscanner.data.CollectionStore
import com.zeroxion.cardscanner.data.Prices
import com.zeroxion.cardscanner.data.VARIANT_NORMAL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Binder: anlegen, Taschen füllen, Seiten, Cover – und alles übersteht einen Neustart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BinderStoreTest {

    private fun item(uid: String) = CollectionItem(
        uid = uid,
        card = CardInfo(
            id = "sv03-125", lang = "de", name = "Glurak-ex", localId = "125", setId = "sv03",
            setName = "Obsidian Flammen", officialCount = 197, rarity = null, imageBase = null,
            normal = Prices(trend = 4.02), holo = null, priceUpdated = null, idProduct = null,
        ),
        photoPath = null,
        variant = VARIANT_NORMAL,
        quantity = 1,
        addedAt = 1L,
        refreshedAt = 1L,
    )

    @Test
    fun slotsAndCoverSurviveRestart() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val cover = File(context.filesDir, "cover-test.jpg").apply { writeText("x") }
        val store = CollectionStore(context)
        store.load()
        store.add(item("a"))
        store.add(item("b"))

        val binder = store.addBinder("151 Kanto Starters", cover.absolutePath, BinderLayout(3, 3))
        store.setBinderSlot(binder.id, 0, "a")
        store.setBinderSlot(binder.id, 4, "b")
        // Eine Tasche auf Seite 2 legt die Seite automatisch an.
        store.setBinderSlot(binder.id, 10, "a")
        assertEquals(2, store.binders.value.single().pageCount)

        val reopened = CollectionStore(context)
        reopened.load()
        val b = reopened.binders.value.single()
        assertEquals("151 Kanto Starters", b.name)
        assertEquals(cover.absolutePath, b.coverPath)
        assertEquals(BinderLayout(3, 3), b.layout)
        assertEquals(mapOf(0 to "a", 4 to "b", 10 to "a"), b.slots)
        assertEquals(listOf("a", "b", "a"), b.orderedUids)
        assertEquals(18, b.capacity)

        // Cover ersetzen löscht das alte Foto.
        reopened.setBinderCover(b.id, null)
        assertFalse(cover.exists())
        assertNull(reopened.binders.value.single().coverPath)
    }

    @Test
    fun deletingACardFreesItsSlots() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = CollectionStore(context)
        store.load()
        store.add(item("a"))
        store.add(item("b"))
        val binder = store.addBinder("Test", null, BinderLayout(2, 2))
        store.setBinderSlot(binder.id, 0, "a")
        store.setBinderSlot(binder.id, 1, "b")
        store.setBinderSlot(binder.id, 2, "a")

        store.remove("a")
        assertEquals(mapOf(1 to "b"), store.binders.value.single().slots)

        val reopened = CollectionStore(context)
        reopened.load()
        assertEquals(mapOf(1 to "b"), reopened.binders.value.single().slots)
    }

    @Test
    fun pagesLayoutAndSwapping() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = CollectionStore(context)
        store.load()
        val binder = store.addBinder("Seiten", null, BinderLayout(2, 2))
        store.addBinderPage(binder.id)
        store.addBinderPage(binder.id)
        store.setBinderSlot(binder.id, 1, "p1")
        store.setBinderSlot(binder.id, 5, "p2")
        store.setBinderSlot(binder.id, 9, "p3")
        assertEquals(3, store.binders.value.single().pageCount)

        // Seite 2 (Index 1) entfernen: ihre Karte fällt raus, Seite 3 rückt nach.
        store.removeBinderPage(binder.id, 1)
        var b = store.binders.value.single()
        assertEquals(2, b.pageCount)
        assertEquals(mapOf(1 to "p1", 5 to "p3"), b.slots)

        store.swapBinderSlots(binder.id, 1, 2)
        b = store.binders.value.single()
        assertEquals(mapOf(2 to "p1", 5 to "p3"), b.slots)

        // Größere Seiten: Reihenfolge bleibt, Seitenzahl reicht für alle Karten.
        store.setBinderLayout(binder.id, BinderLayout(3, 3))
        b = store.binders.value.single()
        assertEquals(BinderLayout(3, 3), b.layout)
        assertTrue(b.capacity > 5)

        store.removeBinder(binder.id)
        assertTrue(store.binders.value.isEmpty())
    }
}
