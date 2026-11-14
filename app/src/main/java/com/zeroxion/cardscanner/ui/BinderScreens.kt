package com.zeroxion.cardscanner.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroxion.cardscanner.MainViewModel
import com.zeroxion.cardscanner.data.Binder
import com.zeroxion.cardscanner.data.BinderLayout
import com.zeroxion.cardscanner.data.CardGroup
import com.zeroxion.cardscanner.data.CollectionItem
import kotlinx.coroutines.launch
import java.io.File

private const val CARD_RATIO = 63f / 88f

/** Wert aller Karten, die im Binder stecken (jede Tasche zählt einmal). */
private fun binderValue(binder: Binder, byUid: Map<String, CollectionItem>): Double =
    binder.slots.values.sumOf { byUid[it]?.unitPrice ?: 0.0 }

private fun cardCount(n: Int): String = if (n == 1) "1 Karte" else "$n Karten"

// ================================================================ Übersicht

@Composable
fun BinderListScreen(
    vm: MainViewModel,
    padding: PaddingValues,
    onOpen: (String) -> Unit,
) {
    val binders by vm.binders.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val byUid = remember(items) { items.associateBy { it.uid } }
    var creating by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Binder", style = MaterialTheme.typography.headlineMedium)
                    val total = binders.sumOf { binderValue(it, byUid) }
                    Text(
                        if (binders.isEmpty()) "Gib deiner Sammlung ihren eigenen Platz."
                        else "${binders.size} Binder · ${formatEuro(total.takeIf { it > 0 })}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { creating = true },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Neuer Binder")
                }
            }
        }

        if (binders.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyBinders(onCreate = { creating = true })
            }
        }

        items(binders, key = { it.id }) { binder ->
            BinderTile(binder = binder, byUid = byUid, onClick = { onOpen(binder.id) })
        }
    }

    if (creating) {
        BinderEditorDialog(
            initial = null,
            onSave = { name, layout, cover, _ ->
                creating = false
                vm.createBinder(name, layout, cover) { id -> onOpen(id) }
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun EmptyBinders(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp))
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(typeBrushFor("water-binder")),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AutoStories,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Noch keine Binder", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Lege einen Binder mit eigenem Coverfoto an und stecke Karten aus deiner Sammlung in die Taschen.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreate, shape = RoundedCornerShape(50)) {
            Text("Ersten Binder anlegen")
        }
    }
}

/** Coverbild eines Binders: eigenes Foto, sonst die wertvollste Karte auf einem Farbverlauf. */
@Composable
private fun BinderCover(
    binder: Binder,
    byUid: Map<String, CollectionItem>,
    modifier: Modifier = Modifier,
    override: Any? = null,
) {
    val coverFile = remember(binder.coverPath) {
        binder.coverPath?.let { File(it) }?.takeIf { it.exists() }
    }
    val model = override ?: coverFile
    Box(modifier.background(typeBrushFor(binder.id)), contentAlignment = Alignment.Center) {
        if (model != null) {
            CardImage(model = model, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            val top = binder.slots.values.mapNotNull { byUid[it] }.maxByOrNull { it.unitPrice ?: 0.0 }
            if (top != null) {
                CardArt(
                    imageBase = top.card.imageBase,
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .aspectRatio(CARD_RATIO)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Icon(
                    Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        // Buchrücken mit Ringen – damit es nach Binder aussieht.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(14.dp)
                .background(Color.Black.copy(alpha = 0.35f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.Center),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun BinderTile(binder: Binder, byUid: Map<String, CollectionItem>, onClick: () -> Unit) {
    val value = binderValue(binder, byUid)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        BinderCover(binder = binder, byUid = byUid, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.85f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
        ) {
            Text(
                binder.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${binder.filled}/${binder.capacity} · ${binder.layout.label}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
            if (value > 0) {
                Spacer(Modifier.height(6.dp))
                PricePill(formatEuro(value))
            }
        }
    }
}

// ================================================================ Anlegen / Bearbeiten

/**
 * Name, Seitenaufteilung und Coverfoto eines Binders.
 * [onSave] bekommt Name, Aufteilung, ein neu gewähltes Foto (oder null) und ob das Cover entfernt wurde.
 */
@Composable
fun BinderEditorDialog(
    initial: Binder?,
    onSave: (String, BinderLayout, Uri?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var layout by remember { mutableStateOf(initial?.layout ?: BinderLayout.default) }
    var newCover by remember { mutableStateOf<Uri?>(null) }
    var removeCover by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            newCover = uri
            removeCover = false
        }
    }
    val existing = remember(initial?.coverPath) {
        initial?.coverPath?.let { File(it) }?.takeIf { it.exists() }
    }
    val preview: Any? = newCover ?: existing.takeIf { !removeCover }
    val valid = name.trim().isNotEmpty()
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neuer Binder" else "Binder bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(HeroBrush)
                        .clickable { pick() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (preview != null) {
                        CardImage(
                            model = preview,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(if (preview != null) Alignment.BottomCenter else Alignment.Center)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (preview != null) "Cover ändern" else "Coverfoto wählen",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (preview != null) {
                        IconButton(
                            onClick = {
                                newCover = null
                                removeCover = true
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Cover entfernen",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("z. B. 151 – Kanto Starters") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Taschen pro Seite",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BinderLayout.all.forEach { option ->
                            SetChip(
                                label = "${option.label}",
                                selected = option == layout,
                                onClick = { layout = option },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(name.trim(), layout, newCover, removeCover) }) {
                Text(if (initial == null) "Anlegen" else "Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

// ================================================================ Ein Binder

@Composable
fun BinderScreen(
    vm: MainViewModel,
    binder: Binder,
    padding: PaddingValues,
    onBack: () -> Unit,
    /** Karte öffnen; dazu alle Karten des Binders in ihrer Reihenfolge (zum Wischen). */
    onOpenCard: (String, List<String>) -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val byUid = remember(items) { items.associateBy { it.uid } }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { binder.pageCount }

    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRemovePage by remember { mutableStateOf<Int?>(null) }
    var scrollToLast by remember { mutableStateOf(false) }
    /** Tasche, für die gerade eine Karte gewählt wird. */
    var pickingFor by rememberSaveable(binder.id) { mutableStateOf<Int?>(null) }
    /** Belegte Tasche, deren Optionen offen sind. */
    var slotMenu by rememberSaveable(binder.id) { mutableStateOf<Int?>(null) }
    /** Tasche, deren Karte gerade verschoben wird. */
    var moving by rememberSaveable(binder.id) { mutableStateOf<Int?>(null) }

    BackHandler(enabled = moving != null) { moving = null }

    LaunchedEffect(binder.pageCount) {
        if (scrollToLast) {
            pager.animateScrollToPage(binder.pageCount - 1)
            scrollToLast = false
        }
    }

    val value = binderValue(binder, byUid)

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
    ) {
        // Kopf
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
            }
            BinderCover(
                binder = binder,
                byUid = byUid,
                modifier = Modifier
                    .size(width = 40.dp, height = 52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { editing = true },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    binder.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append("${binder.filled}/${binder.capacity} Taschen")
                        if (value > 0) append(" · ${formatEuro(value)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Mehr")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Bearbeiten & Cover") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            editing = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Seite hinzufügen") },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            scrollToLast = true
                            vm.addBinderPage(binder.id)
                        },
                    )
                    if (binder.pageCount > 1) {
                        DropdownMenuItem(
                            text = { Text("Diese Seite entfernen") },
                            leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                val page = pager.currentPage
                                val size = binder.layout.pageSize
                                val used = binder.slots.keys.any { it / size == page }
                                if (used) confirmRemovePage = page else vm.removeBinderPage(binder.id, page)
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Binder löschen", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuOpen = false
                            confirmDelete = true
                        },
                    )
                }
            }
        }

        if (moving != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .glass(RoundedCornerShape(16.dp), tint = Electric, strength = 1.8f)
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.OpenWith, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Tippe auf den Zielplatz – auch auf anderen Seiten.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { moving = null }) { Text("Abbrechen") }
            }
        }

        // Seiten
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
        ) { page ->
            BinderPage(
                binder = binder,
                page = page,
                byUid = byUid,
                moving = moving,
                onSlotClick = { index ->
                    val from = moving
                    when {
                        from != null -> {
                            if (from != index) vm.swapBinderSlots(binder.id, from, index)
                            moving = null
                        }
                        binder.slots[index]?.let { byUid[it] } != null -> slotMenu = index
                        else -> pickingFor = index
                    }
                },
                onSlotLongClick = { index ->
                    if (binder.slots[index]?.let { byUid[it] } != null) moving = index
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
            )
        }

        // Seitenleiste
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val current = pager.currentPage
            val last = binder.pageCount - 1
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(0) } }, enabled = current > 0) {
                Icon(Icons.Rounded.FirstPage, contentDescription = "Erste Seite")
            }
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(current - 1) } }, enabled = current > 0) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Vorige Seite")
            }
            Text(
                "Seite ${current + 1} / ${binder.pageCount}",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(current + 1) } }, enabled = current < last) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Nächste Seite")
            }
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(last) } }, enabled = current < last) {
                Icon(Icons.AutoMirrored.Rounded.LastPage, contentDescription = "Letzte Seite")
            }
            IconButton(onClick = {
                scrollToLast = true
                vm.addBinderPage(binder.id)
            }) {
                Icon(Icons.Rounded.Add, contentDescription = "Seite hinzufügen", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }

    // ---------- Dialoge und Auswahl

    pickingFor?.let { index ->
        CardPickerSheet(
            title = "Karte für Tasche ${index % binder.layout.pageSize + 1} · Seite ${index / binder.layout.pageSize + 1}",
            items = items,
            groups = groups,
            inBinder = remember(binder.slots) { binder.slots.values.toSet() },
            onPick = { uid ->
                pickingFor = null
                vm.setBinderSlot(binder.id, index, uid)
            },
            onDismiss = { pickingFor = null },
        )
    }

    slotMenu?.let { index ->
        val item = binder.slots[index]?.let { byUid[it] }
        if (item == null) {
            slotMenu = null
        } else {
            SlotSheet(
                item = item,
                onOpen = {
                    slotMenu = null
                    onOpenCard(item.uid, binder.orderedUids.distinct())
                },
                onMove = {
                    slotMenu = null
                    moving = index
                },
                onReplace = {
                    slotMenu = null
                    pickingFor = index
                },
                onRemove = {
                    slotMenu = null
                    vm.setBinderSlot(binder.id, index, null)
                },
                onDismiss = { slotMenu = null },
            )
        }
    }

    if (editing) {
        BinderEditorDialog(
            initial = binder,
            onSave = { name, layout, cover, removeCover ->
                editing = false
                vm.editBinder(binder.id, name, layout, cover, removeCover)
            },
            onDismiss = { editing = false },
        )
    }

    confirmRemovePage?.let { page ->
        AlertDialog(
            onDismissRequest = { confirmRemovePage = null },
            title = { Text("Seite ${page + 1} entfernen?") },
            text = { Text("Die Karten auf dieser Seite kommen aus dem Binder, bleiben aber in deiner Sammlung.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemovePage = null
                    vm.removeBinderPage(binder.id, page)
                }) { Text("Entfernen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemovePage = null }) { Text("Abbrechen") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Binder „${binder.name}“ löschen?") },
            text = { Text("Die Karten bleiben in deiner Sammlung, nur der Binder und sein Cover werden gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onBack()
                    vm.deleteBinder(binder.id)
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

/** Eine Binderseite: Taschen im Kartenformat, so groß wie auf den Bildschirm passt. */
@Composable
private fun BinderPage(
    binder: Binder,
    page: Int,
    byUid: Map<String, CollectionItem>,
    moving: Int?,
    onSlotClick: (Int) -> Unit,
    onSlotLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = binder.layout.columns
    val rows = binder.layout.rows
    BoxWithConstraints(
        modifier = modifier
            .glass(RoundedCornerShape(26.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val spacing = 10.dp
        val label = 20.dp
        val byWidth = (maxWidth - spacing * (columns - 1)) / columns
        val byHeight = ((maxHeight - spacing * (rows - 1)) / rows - label) * CARD_RATIO
        val cell = minOf(byWidth, byHeight).coerceAtLeast(28.dp)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    for (c in 0 until columns) {
                        val index = page * binder.layout.pageSize + r * columns + c
                        val item = binder.slots[index]?.let { byUid[it] }
                        BinderSlot(
                            item = item,
                            selected = moving == index,
                            target = moving != null && moving != index,
                            onClick = { onSlotClick(index) },
                            onLongClick = { onSlotLongClick(index) },
                            modifier = Modifier.width(cell),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BinderSlot(
    item: CollectionItem?,
    selected: Boolean,
    target: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(9.dp)
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_RATIO)
                .clip(shape)
                .then(if (selected) Modifier.border(3.dp, Gold, shape) else Modifier)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            if (item == null) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.05f))
                        .drawBehind {
                            val stroke = 1.5.dp.toPx()
                            drawRoundRect(
                                color = if (target) primary else outline,
                                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                                cornerRadius = CornerRadius(9.dp.toPx()),
                                style = Stroke(
                                    width = stroke,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                                ),
                            )
                        },
                )
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Karte einstecken",
                    tint = if (target) primary else outline,
                )
            } else {
                val photo = remember(item.photoPath) { item.photoPath?.let { File(it) }?.takeIf { it.exists() } }
                if (item.card.imageBase != null) {
                    CardArt(imageBase = item.card.imageBase, modifier = Modifier.matchParentSize())
                } else {
                    CardImage(model = photo, modifier = Modifier.matchParentSize())
                }
                if (target) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .border(2.dp, primary.copy(alpha = 0.6f), shape),
                    )
                }
            }
        }
        Text(
            if (item != null) formatEuro(item.unitPrice) else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .height(20.dp)
                .padding(top = 4.dp),
        )
    }
}

/** Optionen für eine belegte Tasche. */
@Composable
private fun SlotSheet(
    item: CollectionItem,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardArt(
                    imageBase = item.card.imageBase,
                    modifier = Modifier
                        .width(72.dp)
                        .aspectRatio(CARD_RATIO)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.card.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOf(item.card.setName, item.card.numberLabel).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    PricePill(formatEuro(item.unitPrice))
                }
            }
            Spacer(Modifier.height(16.dp))
            SheetAction(Icons.AutoMirrored.Rounded.OpenInNew, "Karte ansehen", onClick = onOpen)
            SheetAction(Icons.Rounded.OpenWith, "Verschieben", onClick = onMove)
            SheetAction(Icons.Rounded.SwapHoriz, "Andere Karte einstecken", onClick = onReplace)
            SheetAction(
                Icons.Rounded.RemoveCircleOutline,
                "Aus dem Binder nehmen",
                color = MaterialTheme.colorScheme.error,
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/** Karte aus der Sammlung wählen, die in eine Tasche kommt. */
@Composable
private fun CardPickerSheet(
    title: String,
    items: List<CollectionItem>,
    groups: List<CardGroup>,
    inBinder: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var groupFilter by remember { mutableStateOf<String?>(null) }
    val shown = remember(items, query, groupFilter) {
        val q = query.trim().lowercase()
        items
            .filter { groupFilter == null || it.groupId == groupFilter }
            .filter {
                q.isEmpty() ||
                    it.card.name.lowercase().contains(q) ||
                    it.card.setName.lowercase().contains(q) ||
                    it.card.localId.lowercase().contains(q)
            }
            .sortedByDescending { it.addedAt }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.55f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text("Sammlung durchsuchen") },
                colors = glassFieldColors(),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Suche leeren")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            if (groups.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "all") {
                        SetChip("Alle", selected = groupFilter == null, onClick = { groupFilter = null })
                    }
                    rowItems(groups, key = { it.id }) { group ->
                        SetChip(group.name, selected = groupFilter == group.id, onClick = { groupFilter = group.id })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            if (shown.isEmpty()) {
                Text(
                    if (items.isEmpty()) "Deine Sammlung ist noch leer. Scanne zuerst ein paar Karten."
                    else "Keine Karte passt.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.uid }) { item ->
                        PickerTile(item = item, inBinder = item.uid in inBinder, onClick = { onPick(item.uid) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerTile(item: CollectionItem, inBinder: Boolean, onClick: () -> Unit) {
    val photo = remember(item.photoPath) { item.photoPath?.let { File(it) }?.takeIf { it.exists() } }
    Column(
        modifier = Modifier
            .glass(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box {
            val imageModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_RATIO)
                .clip(RoundedCornerShape(9.dp))
            if (item.card.imageBase != null) CardArt(imageBase = item.card.imageBase, modifier = imageModifier)
            else CardImage(model = photo, modifier = imageModifier)
            if (inBinder) {
                Text(
                    "im Binder",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            item.card.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
        Text(
            formatEuro(item.unitPrice),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
