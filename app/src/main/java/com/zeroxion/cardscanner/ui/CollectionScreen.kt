package com.zeroxion.cardscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroxion.cardscanner.MainViewModel
import com.zeroxion.cardscanner.RefreshState
import com.zeroxion.cardscanner.data.CollectionItem
import java.io.File

private const val SORT_NEW = 0
private const val SORT_VALUE = 1
private const val SORT_NAME = 2

@Composable
fun CollectionScreen(
    vm: MainViewModel,
    padding: PaddingValues,
    onOpen: (String) -> Unit,
    onScan: () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(SORT_NEW) }

    val shown = remember(items, query, sort) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) items else items.filter {
            it.card.name.lowercase().contains(q) ||
                it.card.setName.lowercase().contains(q) ||
                it.card.localId.lowercase().contains(q)
        }
        when (sort) {
            SORT_VALUE -> filtered.sortedByDescending { it.totalPrice }
            SORT_NAME -> filtered.sortedBy { it.card.name.lowercase() }
            else -> filtered.sortedByDescending { it.addedAt }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text(
                    "CARDSCANNER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("Meine Sammlung", style = MaterialTheme.typography.headlineLarge)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            ValueHero(items = items, refresh = refresh, onRefresh = vm::refreshAll)
        }

        if (items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(onScan)
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        placeholder = { Text("Sammlung durchsuchen") },
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortChip("Neueste", sort == SORT_NEW) { sort = SORT_NEW }
                        SortChip("Wert", sort == SORT_VALUE) { sort = SORT_VALUE }
                        SortChip("Name", sort == SORT_NAME) { sort = SORT_NAME }
                    }
                }
            }

            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Keine Karte passt zu „${query.trim()}“.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            items(shown, key = { it.uid }) { item ->
                CardTile(item = item, onClick = { onOpen(item.uid) })
            }
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
private fun ValueHero(items: List<CollectionItem>, refresh: RefreshState, onRefresh: () -> Unit) {
    val total = items.sumOf { it.totalPrice }
    val count = items.sumOf { it.quantity }
    val lastRefresh = items.maxOfOrNull { it.refreshedAt } ?: 0L
    val priced = items.count { it.unitPrice != null }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(HeroBrush)
            .padding(22.dp),
    ) {
        Text(
            "GESAMTWERT · CARDMARKET",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (items.isEmpty()) "0,00 €" else formatEuro(total).let { if (it == "–") "0,00 €" else it },
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
        Spacer(Modifier.height(2.dp))
        val stand = formatMillisDay(lastRefresh)
        Text(
            text = buildString {
                append(if (count == 1) "1 Karte" else "$count Karten")
                if (items.isNotEmpty() && priced < items.size) append(" · ${items.size - priced} ohne Preis")
                if (stand != null) append(" · Stand $stand")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(16.dp))
        if (refresh.running) {
            Text(
                "Aktualisiere Preise … ${refresh.done}/${refresh.total}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (refresh.total == 0) 0f else refresh.done.toFloat() / refresh.total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        } else {
            Button(
                onClick = onRefresh,
                enabled = items.isNotEmpty(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1B1F3B),
                    disabledContainerColor = Color.White.copy(alpha = 0.25f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Preise aktualisieren")
            }
        }
    }
}

@Composable
private fun EmptyState(onScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Noch keine Karten", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Fotografiere eine Karte – die App erkennt sie und zeigt dir den aktuellen Cardmarket-Preis.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onScan, shape = RoundedCornerShape(50)) {
            Text("Erste Karte scannen")
        }
    }
}

@Composable
private fun CardTile(item: CollectionItem, onClick: () -> Unit) {
    val photo = item.photoPath?.let { File(it) }?.takeIf { it.exists() }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box {
            CardImage(
                model = photo ?: item.card.imageUrl(),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(63f / 88f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            PricePill(
                text = formatEuro(item.unitPrice),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
            if (item.quantity > 1) {
                Text(
                    "×${item.quantity}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
            Text(
                item.card.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(item.card.setName, item.card.numberLabel).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
