package com.zeroxion.cardscanner

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroxion.cardscanner.data.Binder
import com.zeroxion.cardscanner.data.BinderLayout
import com.zeroxion.cardscanner.data.CardBrief
import com.zeroxion.cardscanner.data.CardGroup
import com.zeroxion.cardscanner.data.CardInfo
import com.zeroxion.cardscanner.data.CardmarketPriceGuide
import com.zeroxion.cardscanner.data.PullRate
import com.zeroxion.cardscanner.ui.TypeTheme
import com.zeroxion.cardscanner.data.CollectionItem
import com.zeroxion.cardscanner.data.CollectionStore
import com.zeroxion.cardscanner.data.PullRates
import com.zeroxion.cardscanner.data.ScanLangs
import com.zeroxion.cardscanner.data.TcgDexApi
import com.zeroxion.cardscanner.scan.CardMatcher
import com.zeroxion.cardscanner.scan.CardTextParser
import com.zeroxion.cardscanner.scan.FrameCrop
import com.zeroxion.cardscanner.scan.ImageUtils
import com.zeroxion.cardscanner.scan.ParsedCard
import com.zeroxion.cardscanner.scan.ScanFrame
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

sealed interface PullRateState {
    data object Loading : PullRateState
    data object None : PullRateState
    data class Ready(val rate: PullRate) : PullRateState
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

    private val _typeTheme = MutableStateFlow(TypeTheme.of(prefs.getString("theme", null)))
    /** Gewählte Farbwelt (Klassik, Feuer, Wasser, …). */
    val typeTheme: StateFlow<TypeTheme> = _typeTheme

    fun setTypeTheme(theme: TypeTheme) {
        _typeTheme.value = theme
        prefs.edit().putString("theme", theme.key).apply()
    }

    private val _lang = MutableStateFlow(prefs.getString("lang", "de") ?: "de")
    /** Sprache der Karten, in der zuerst gesucht wird ("de" oder "en"). */
    val lang: StateFlow<String> = _lang

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    /** Eigene Kategorien des Nutzers. */
    val groups: StateFlow<List<CardGroup>> = store.groups

    private val _scanGroupId = MutableStateFlow(prefs.getString("scanGroup", null))
    /** Kategorie, in die neu gescannte Karten gelegt werden (bleibt für die nächsten Scans gewählt). */
    val scanGroupId: StateFlow<String?> = _scanGroupId

    init {
        viewModelScope.launch { store.load() }
    }

    // ---------- Eigene Kategorien ----------

    fun setScanGroup(id: String?) {
        _scanGroupId.value = id
        prefs.edit().putString("scanGroup", id).apply()
    }

    fun createGroup(name: String, onCreated: (String) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val group = store.addGroup(name)
            onCreated(group.id)
        }
    }

    fun renameGroup(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { store.renameGroup(id, name) }
    }

    fun deleteGroup(id: String) {
        viewModelScope.launch {
            store.removeGroup(id)
            if (_scanGroupId.value == id) setScanGroup(null)
        }
    }

    /** Karte einer Kategorie zuordnen (null = keine). */
    fun setGroup(uid: String, groupId: String?) {
        viewModelScope.launch { store.update(uid) { it.copy(groupId = groupId) } }
    }

    // ---------- Pullrate ----------

    private val _pullRates = MutableStateFlow<Map<String, PullRateState>>(emptyMap())
    /** Pullraten je Karten-ID (werden beim Ansehen einer Karte geladen). */
    val pullRates: StateFlow<Map<String, PullRateState>> = _pullRates

    /** Pullrate für [card] laden, falls noch nicht geschehen. */
    fun ensurePullRate(card: CardInfo) {
        val key = card.id
        if (_pullRates.value.containsKey(key)) return
        if (ScanLangs.isAsian(card.lang) || !PullRates.supports(card.setId)) {
            _pullRates.value = _pullRates.value + (key to PullRateState.None)
            return
        }
        _pullRates.value = _pullRates.value + (key to PullRateState.Loading)
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { TcgDexApi.pullRate(card.setId, card.id) } }
            _pullRates.value = if (result.isFailure) {
                // Ohne Verbindung beim nächsten Öffnen erneut versuchen.
                _pullRates.value - key
            } else {
                val rate = result.getOrNull()
                _pullRates.value + (key to (if (rate != null) PullRateState.Ready(rate) else PullRateState.None))
            }
        }
    }

    // ---------- Binder ----------

    val binders: StateFlow<List<Binder>> = store.binders

    /** Coverfoto aus der Galerie in den App-Speicher kopieren (verkleinert). */
    private suspend fun importCover(uri: Uri): String? {
        val app = getApplication<Application>()
        return runCatching {
            withContext(Dispatchers.IO) {
                ImageUtils.importImage(File(app.filesDir, "covers"), maxDim = 1400) {
                    if (uri.scheme == "file") uri.path?.let { java.io.FileInputStream(it) }
                    else app.contentResolver.openInputStream(uri)
                }.absolutePath
            }
        }.getOrElse {
            notify("Das Coverfoto konnte nicht geladen werden.")
            null
        }
    }

    fun createBinder(name: String, layout: BinderLayout, cover: Uri?, onCreated: (String) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val coverPath = cover?.let { importCover(it) }
            val binder = store.addBinder(name, coverPath, layout)
            onCreated(binder.id)
        }
    }

    /** Name, Aufteilung und Cover ändern. [cover]: neues Foto; [removeCover]: Cover entfernen. */
    fun editBinder(id: String, name: String, layout: BinderLayout, cover: Uri?, removeCover: Boolean) {
        viewModelScope.launch {
            if (name.isNotBlank()) store.renameBinder(id, name)
            store.setBinderLayout(id, layout)
            when {
                cover != null -> importCover(cover)?.let { store.setBinderCover(id, it) }
                removeCover -> store.setBinderCover(id, null)
            }
        }
    }

    fun deleteBinder(id: String) {
        viewModelScope.launch { store.removeBinder(id) }
    }

    fun addBinderPage(id: String) {
        viewModelScope.launch { store.addBinderPage(id) }
    }

    fun removeBinderPage(id: String, page: Int) {
        viewModelScope.launch { store.removeBinderPage(id, page) }
    }

    fun setBinderSlot(id: String, index: Int, uid: String?) {
        viewModelScope.launch { store.setBinderSlot(id, index, uid) }
    }

    fun swapBinderSlots(id: String, from: Int, to: Int) {
        viewModelScope.launch { store.swapBinderSlots(id, from, to) }
    }

    fun notify(text: String) {
        _messages.tryEmit(text)
    }

    fun setLang(code: String) {
        val next = ScanLangs.byCode(code).code
        _lang.value = next
        prefs.edit().putString("lang", next).apply()
    }

    // ---------- Scannen ----------

    /**
     * Neues Foto (Kamera oder Galerie): speichern, Text lesen, Karte suchen.
     * Mit [frame] wird das Foto auf den gelben Scan-Rahmen zugeschnitten.
     */
    fun onImage(source: Uri, frame: FrameCrop? = null) {
        if (_scan.value is ScanState.Working) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            _scan.value = ScanState.Working(null, "Foto wird gespeichert …")
            val photo = try {
                withContext(Dispatchers.IO) {
                    if (frame == null) ImageUtils.importImage(app, source)
                    else ImageUtils.importImage(app, source) { width, height ->
                        ScanFrame.toImageFractions(frame.box, frame.viewWidth, frame.viewHeight, width, height)
                    }
                }
            } catch (e: Exception) {
                _scan.value = ScanState.Camera
                val reason = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                notify("Das Foto konnte nicht verarbeitet werden ($reason).")
                return@launch
            }

            _scan.value = ScanState.Working(photo, "Text wird gelesen …")
            val parsed = try {
                val text = TextReader.read(photo, ScanLangs.byCode(_lang.value).script)
                CardTextParser.parse(text.lines, text.bottomLines)
            } catch (e: Exception) {
                ParsedCard(emptyList(), null, null)
            }

            _scan.value = ScanState.Working(photo, "Karte wird gesucht …")
            val scanLang = ScanLangs.byCode(_lang.value)
            val found = runCatching {
                if (parsed.isEmpty) emptyList()
                else withContext(Dispatchers.IO) {
                    var hits = emptyList<CardInfo>()
                    for (apiLang in scanLang.apiLangs) {
                        hits = CardMatcher.find(parsed, apiLang)
                        if (hits.isNotEmpty()) break
                    }
                    hits
                }
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
            // Ohne lesbare Nummer gibt es oft viele Karten mit demselben Namen:
            // dann gleich alle zur Auswahl anbieten.
            val name = parsed.names.firstOrNull()
            if (found.isSuccess && parsed.localId == null && name != null) search(name, silent = true)
        }
    }

    fun selectCandidate(card: CardInfo) {
        val state = _scan.value as? ScanState.Result ?: return
        _scan.value = state.copy(selected = card)
    }

    /** Manuelle Suche nach dem Kartennamen. */
    fun search(query: String, silent: Boolean = false) {
        val state = _scan.value as? ScanState.Result ?: return
        val term = query.trim()
        if (term.length < 2) return
        val langs = ScanLangs.byCode(_lang.value).searchLangs
        _scan.value = state.copy(searching = true)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    var hits = emptyList<CardBrief>()
                    search@ for (candidate in CardTextParser.searchTerms(term).ifEmpty { listOf(term) }) {
                        for (apiLang in langs) {
                            hits = TcgDexApi.searchByName(apiLang, candidate)
                            if (hits.isNotEmpty()) break@search
                        }
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
            if (silent) return@launch
            if (result.isFailure) notify("Suche fehlgeschlagen – keine Verbindung.")
            else if (result.getOrDefault(emptyList()).isEmpty()) notify("Nichts gefunden für „$term“.")
        }
    }

    /** Treffer aus der manuellen Suche übernehmen (lädt Details und Preise nach). */
    fun pickSearchResult(brief: CardBrief) {
        val state = _scan.value as? ScanState.Result ?: return
        val langs = ScanLangs.byCode(_lang.value).searchLangs
        _scan.value = state.copy(searching = true)
        viewModelScope.launch {
            val card = runCatching {
                withContext(Dispatchers.IO) { langs.firstNotNullOfOrNull { TcgDexApi.card(it, brief.id) } }
            }.getOrNull()
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
        val group = store.groups.value.firstOrNull { it.id == _scanGroupId.value }
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
                    groupId = group?.id,
                ),
            )
            _scan.value = ScanState.Camera
            notify(
                if (group != null) "${card.name} wurde zu „${group.name}“ hinzugefügt."
                else "${card.name} wurde zur Sammlung hinzugefügt.",
            )
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

    private val priceGuideDir = File(app.cacheDir, "cardmarket")

    /** Ob die Verbindung nach Datenvolumen abgerechnet wird (Mobilfunk); im Zweifel: ja. */
    private fun meteredNetwork(): Boolean = try {
        val manager = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        manager == null || manager.isActiveNetworkMetered
    } catch (e: Exception) {
        true
    }

    /** Ob der Preisführer ohne Datenvolumen zu haben ist: heute schon geladen oder im WLAN. */
    private suspend fun priceGuideIsFree(): Boolean =
        withContext(Dispatchers.IO) { CardmarketPriceGuide.isFresh(priceGuideDir) || !meteredNetwork() }

    /**
     * Preise aus dem Cardmarket-Preisführer in die Sammlung übernehmen.
     * Liefert die Karten, für die es dort keinen Preis gab – oder alle, wenn die Liste nicht erreichbar war.
     */
    private suspend fun applyPriceGuide(cards: List<CardInfo>): List<CardInfo> {
        val wanted = cards.mapNotNull { it.idProduct?.takeIf { id -> id > 0 } }.toSet()
        if (wanted.isEmpty()) return cards
        val guide = runCatching {
            withContext(Dispatchers.IO) { CardmarketPriceGuide.load(priceGuideDir, wanted) }
        }.getOrNull() ?: return cards
        store.applyPrices(guide.entries, guide.updated, System.currentTimeMillis())
        return cards.filterNot { it.idProduct in guide.entries }
    }

    /**
     * Preise aller Karten neu holen. Im WLAN (oder wenn die Liste heute schon geladen wurde) kommen
     * sie in einem Zug aus dem Cardmarket-Preisführer; die Datei ist rund 16 MB groß, unterwegs fragt
     * die App deshalb weiter Karte für Karte bei TCGdex an. Was der eine Weg nicht liefert, übernimmt der andere.
     */
    fun refreshAll() {
        if (_refresh.value.running) return
        val cards = store.items.value.map { it.card }.distinctBy { it.id to it.lang }
        if (cards.isEmpty()) return
        viewModelScope.launch {
            val total = cards.size
            _refresh.value = RefreshState(running = true, done = 0, total = total)

            var pending = cards
            var guideTried = false
            if (priceGuideIsFree()) {
                guideTried = true
                pending = applyPriceGuide(pending)
                _refresh.value = RefreshState(running = true, done = total - pending.size, total = total)
            }

            // Karten ohne Produktnummer oder ohne Eintrag in der Liste: einzeln über TCGdex.
            val unreachable = ArrayList<CardInfo>()
            var done = total - pending.size
            for (chunk in pending.chunked(4)) {
                val fresh = chunk.map { card ->
                    async(Dispatchers.IO) { card to runCatching { TcgDexApi.card(card.lang, card.id) }.getOrNull() }
                }.awaitAll()
                val now = System.currentTimeMillis()
                for ((card, result) in fresh) {
                    if (result == null) unreachable += card else store.applyFresh(result, now)
                }
                done += chunk.size
                _refresh.value = RefreshState(running = true, done = done, total = total)
            }

            // TCGdex nicht erreichbar: dann die Preisliste auch unterwegs laden.
            var failed = unreachable.size
            if (failed > 0 && !guideTried) failed = applyPriceGuide(unreachable).size

            _refresh.value = RefreshState(running = false, done = total, total = total)
            notify(
                when (failed) {
                    0 -> "Preise aktualisiert."
                    total -> "Aktualisierung fehlgeschlagen – keine Verbindung?"
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
            val updated = when {
                card != null -> {
                    store.applyFresh(card, System.currentTimeMillis())
                    true
                }
                // TCGdex nicht erreichbar: der Preis steht auch im Cardmarket-Preisführer.
                // Für eine einzelne Karte wird er aber nicht eigens über Mobilfunk geladen.
                else -> priceGuideIsFree() && applyPriceGuide(listOf(item.card)).isEmpty()
            }
            notify(if (updated) "Preis aktualisiert." else "Preis konnte nicht geladen werden.")
            _refreshingOne.value = null
        }
    }
}
