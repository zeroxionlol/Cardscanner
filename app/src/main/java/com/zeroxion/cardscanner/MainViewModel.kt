package com.zeroxion.cardscanner

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroxion.cardscanner.data.CardBrief
import com.zeroxion.cardscanner.data.CardInfo
import com.zeroxion.cardscanner.data.CollectionItem
import com.zeroxion.cardscanner.data.CollectionStore
import com.zeroxion.cardscanner.data.TcgDexApi
import com.zeroxion.cardscanner.scan.CardMatcher
import com.zeroxion.cardscanner.scan.CardTextParser
import com.zeroxion.cardscanner.scan.ImageUtils
import com.zeroxion.cardscanner.scan.ParsedCard
import com.zeroxion.cardscanner.scan.TextReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface ScanState {
    /** Kamera ist offen. */
    data object Camera : ScanState

    /** Foto wird ausgewertet. */
    data class Working(val photo: File?, val step: String) : ScanState

    /** Auswertung fertig: Treffer zur Auswahl. */
    data class Result(
        val photo: File,
        val parsed: ParsedCard,
        val candidates: List<CardInfo>,
        val selected: CardInfo?,
        val notice: String? = null,
        val searchResults: List<CardBrief> = emptyList(),
        val searching: Boolean = false,
    ) : ScanState
}

data class RefreshState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = CollectionStore(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val items: StateFlow<List<CollectionItem>> = store.items

    private val _scan = MutableStateFlow<ScanState>(ScanState.Camera)
    val scan: StateFlow<ScanState> = _scan

    private val _refresh = MutableStateFlow(RefreshState())
    val refresh: StateFlow<RefreshState> = _refresh

    private val _refreshingOne = MutableStateFlow<String?>(null)
    val refreshingOne: StateFlow<String?> = _refreshingOne

    private val _lang = MutableStateFlow(prefs.getString("lang", "de") ?: "de")
    /** Sprache der Karten, in der zuerst gesucht wird ("de" oder "en"). */
    val lang: StateFlow<String> = _lang

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    init {
        viewModelScope.launch { store.load() }
    }

    fun notify(text: String) {
        _messages.tryEmit(text)
    }

    fun toggleLang() {
        val next = if (_lang.value == "de") "en" else "de"
        _lang.value = next
        prefs.edit().putString("lang", next).apply()
    }

    // ---------- Scannen ----------

    /** Neues Foto (Kamera oder Galerie): speichern, Text lesen, Karte suchen. */
    fun onImage(source: Uri) {
        if (_scan.value is ScanState.Working) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            _scan.value = ScanState.Working(null, "Foto wird gespeichert …")
            val photo = try {
                withContext(Dispatchers.IO) { ImageUtils.importImage(app, source) }
            } catch (e: Exception) {
                _scan.value = ScanState.Camera
                val reason = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                notify("Das Foto konnte nicht verarbeitet werden ($reason).")
                return@launch
            }

            _scan.value = ScanState.Working(photo, "Text wird gelesen …")
            val lines = try {
                TextReader.read(app, photo)
            } catch (e: Exception) {
                emptyList()
            }
            val parsed = CardTextParser.parse(lines)

            _scan.value = ScanState.Working(photo, "Karte wird gesucht …")
            val lang = _lang.value
            val found = runCatching {
                if (parsed.isEmpty) emptyList()
                else withContext(Dispatchers.IO) { CardMatcher.find(parsed, lang) }
            }
            val candidates = found.getOrDefault(emptyList())
            val notice = when {
                found.isFailure -> "Keine Verbindung zur Kartendatenbank. Prüfe dein Internet und suche dann unten nach dem Namen."
                parsed.isEmpty -> "Auf dem Foto war kein Text lesbar. Suche unten nach dem Kartennamen."
                candidates.isEmpty() -> "Keine passende Karte gefunden. Suche unten nach dem Kartennamen."
                else -> null
            }
            _scan.value = ScanState.Result(
                photo = photo,
                parsed = parsed,
                candidates = candidates,
                selected = candidates.firstOrNull(),
                notice = notice,
            )
        }
    }

    fun selectCandidate(card: CardInfo) {
        val state = _scan.value as? ScanState.Result ?: return
        _scan.value = state.copy(selected = card)
    }

    /** Manuelle Suche nach dem Kartennamen. */
    fun search(query: String) {
        val state = _scan.value as? ScanState.Result ?: return
        val term = query.trim()
        if (term.length < 2) return
        val lang = _lang.value
        _scan.value = state.copy(searching = true)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val other = if (lang == "de") "en" else "de"
                    var hits = emptyList<CardBrief>()
                    for (candidate in CardTextParser.searchTerms(term).ifEmpty { listOf(term) }) {
                        hits = TcgDexApi.searchByName(lang, candidate)
                        if (hits.isEmpty()) hits = TcgDexApi.searchByName(other, candidate)
                        if (hits.isNotEmpty()) break
                    }
                    // Neueste zuerst, dann nach Ähnlichkeit zum Suchbegriff.
                    hits.distinctBy { it.id }
                        .asReversed()
                        .sortedByDescending { CardTextParser.similarity(it.name, term) }
                        .take(60)
                }
            }
            val current = _scan.value as? ScanState.Result ?: return@launch
            _scan.value = current.copy(
                searching = false,
                searchResults = result.getOrDefault(emptyList()),
            )
            if (result.isFailure) notify("Suche fehlgeschlagen – keine Verbindung.")
            else if (result.getOrDefault(emptyList()).isEmpty()) notify("Nichts gefunden für „$term“.")
        }
    }

    /** Treffer aus der manuellen Suche übernehmen (lädt Details und Preise nach). */
    fun pickSearchResult(brief: CardBrief) {
        val state = _scan.value as? ScanState.Result ?: return
        val lang = _lang.value
        _scan.value = state.copy(searching = true)
        viewModelScope.launch {
            val card = runCatching { withContext(Dispatchers.IO) { TcgDexApi.card(lang, brief.id) } }.getOrNull()
            val current = _scan.value as? ScanState.Result ?: return@launch
            if (card == null) {
                _scan.value = current.copy(searching = false)
                notify("Die Karte konnte nicht geladen werden.")
            } else {
                val merged = listOf(card) + current.candidates.filterNot { it.id == card.id }
                _scan.value = current.copy(searching = false, candidates = merged, selected = card)
            }
        }
    }

    fun addSelected(variant: String) {
        val state = _scan.value as? ScanState.Result ?: return
        val card = state.selected ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            store.add(
                CollectionItem(
                    uid = UUID.randomUUID().toString(),
                    card = card,
                    photoPath = state.photo.absolutePath,
                    variant = variant,
                    quantity = 1,
                    addedAt = now,
                    refreshedAt = now,
                ),
            )
            _scan.value = ScanState.Camera
            notify("${card.name} wurde zur Sammlung hinzugefügt.")
        }
    }

    fun discardScan() {
        val state = _scan.value
        if (state is ScanState.Result) runCatching { state.photo.delete() }
        if (state !is ScanState.Working) _scan.value = ScanState.Camera
    }

    // ---------- Sammlung ----------

    fun setQuantity(uid: String, quantity: Int) {
        viewModelScope.launch { store.update(uid) { it.copy(quantity = quantity.coerceIn(1, 999)) } }
    }

    fun setVariant(uid: String, variant: String) {
        viewModelScope.launch { store.update(uid) { it.copy(variant = variant) } }
    }

    fun delete(uid: String) {
        viewModelScope.launch { store.remove(uid) }
    }

    /** Preise aller Karten neu von Cardmarket (über TCGdex) holen. */
    fun refreshAll() {
        if (_refresh.value.running) return
        val keys = store.items.value.map { it.card.id to it.card.lang }.distinct()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            _refresh.value = RefreshState(running = true, done = 0, total = keys.size)
            var failed = 0
            var done = 0
            for (chunk in keys.chunked(4)) {
                val fresh = chunk.map { (id, lang) ->
                    async(Dispatchers.IO) { runCatching { TcgDexApi.card(lang, id) }.getOrNull() }
                }.awaitAll()
                val now = System.currentTimeMillis()
                for (card in fresh) {
                    if (card == null) failed++ else store.applyFresh(card, now)
                }
                done += chunk.size
                _refresh.value = RefreshState(running = true, done = done, total = keys.size)
            }
            _refresh.value = RefreshState(running = false, done = keys.size, total = keys.size)
            notify(
                when (failed) {
                    0 -> "Preise aktualisiert."
                    keys.size -> "Aktualisierung fehlgeschlagen – keine Verbindung?"
                    else -> "Preise aktualisiert, $failed Karte(n) nicht erreichbar."
                },
            )
        }
    }

    fun refreshOne(uid: String) {
        val item = store.items.value.firstOrNull { it.uid == uid } ?: return
        if (_refreshingOne.value != null) return
        _refreshingOne.value = uid
        viewModelScope.launch {
            val card = runCatching {
                withContext(Dispatchers.IO) { TcgDexApi.card(item.card.lang, item.card.id) }
            }.getOrNull()
            if (card == null) notify("Preis konnte nicht geladen werden.")
            else {
                store.applyFresh(card, System.currentTimeMillis())
                notify("Preis aktualisiert.")
            }
            _refreshingOne.value = null
        }
    }
}
