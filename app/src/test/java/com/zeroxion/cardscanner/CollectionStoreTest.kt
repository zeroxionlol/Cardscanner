package com.zeroxion.cardscanner

import com.zeroxion.cardscanner.data.CardInfo
import com.zeroxion.cardscanner.data.CollectionItem
import com.zeroxion.cardscanner.data.CollectionStore
import com.zeroxion.cardscanner.data.Prices
import com.zeroxion.cardscanner.data.VARIANT_NORMAL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Eigene Sets: anlegen, Karten zuordnen, umbenennen, löschen – und alles übersteht einen Neustart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollectionStoreTest {

    private fun item(uid: String, groupId: String? = null) = CollectionItem(
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
        groupId = groupId,
    )

    @Test
    fun setsSurviveRestartAndDeletingASetKeepsTheCards() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = CollectionStore(context)
        store.load()

        val jubilee = store.addGroup("30 Jähriges Jubiläum")
        val lena = store.addGroup("Lenas Karten")
        assertEquals(jubilee.id, store.addGroup("30 jähriges jubiläum").id) // kein Doppel
        assertEquals(2, store.groups.value.size)

        store.add(item("a"))
        store.add(item("b"))
        store.update("a") { it.copy(groupId = jubilee.id) }
        store.update("b") { it.copy(groupId = lena.id) }
        store.renameGroup(lena.id, "Lenas Sammlung")

        // Neustart der App: alles neu von der Platte lesen.
        val reopened = CollectionStore(context)
        reopened.load()
        assertEquals(listOf("30 Jähriges Jubiläum", "Lenas Sammlung"), reopened.groups.value.map { it.name })
        assertEquals(jubilee.id, reopened.items.value.first { it.uid == "a" }.groupId)
        assertEquals(lena.id, reopened.items.value.first { it.uid == "b" }.groupId)

        reopened.removeGroup(jubilee.id)
        assertEquals(1, reopened.groups.value.size)
        assertEquals(2, reopened.items.value.size)
        assertNull(reopened.items.value.first { it.uid == "a" }.groupId)

        val again = CollectionStore(context)
        again.load()
        assertNull(again.items.value.first { it.uid == "a" }.groupId)
        assertEquals(lena.id, again.items.value.first { it.uid == "b" }.groupId)
    }
}
