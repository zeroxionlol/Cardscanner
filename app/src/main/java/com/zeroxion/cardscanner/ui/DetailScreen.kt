package com.zeroxion.cardscanner.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroxion.cardscanner.MainViewModel
import com.zeroxion.cardscanner.data.CardInfo
import com.zeroxion.cardscanner.data.CollectionItem
import com.zeroxion.cardscanner.data.Prices
import com.zeroxion.cardscanner.data.VARIANT_HOLO
import com.zeroxion.cardscanner.data.VARIANT_NORMAL
import java.io.File
import java.net.URLEncoder

fun cardmarketUrl(card: CardInfo): String =
    if (card.idProduct != null && card.idProduct > 0) {
        "https://www.cardmarket.com/de/Pokemon/Products?idProduct=${card.idProduct}"
    } else {
        "https://www.cardmarket.com/de/Pokemon/Products/Search?searchString=" +
            URLEncoder.encode(card.name, "UTF-8")
    }

@Composable
fun DetailScreen(
    vm: MainViewModel,
    item: CollectionItem,
    padding: PaddingValues,
    onBack: () -> Unit,
    /** z. B. "3 / 12", wenn zwischen mehreren Karten gewischt werden kann. */
    position: String? = null,
) {
    val context = LocalContext.current
    val refreshingUid by vm.refreshingOne.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val photo = remember(item.photoPath) { item.photoPath?.let { File(it) }?.takeIf { it.exists() } }
    var showPhoto by remember(item.uid) { mutableStateOf(photo != null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val card = item.card
    val prices = card.prices(item.variant)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
            .verticalScroll(rememberScrollState())
            .padding(bottom = padding.calculateBottomPadding() + 32.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (position != null) {
                    Text(
                        position,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Karte löschen",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val imageModifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(0.72f)
                .aspectRatio(63f / 88f)
                .clip(RoundedCornerShape(22.dp))
            if (showPhoto && photo != null) {
                CardImage(model = photo, contentScale = ContentScale.Crop, modifier = imageModifier)
            } else {
                CardArt(imageBase = card.imageBase, high = true, modifier = imageModifier)
            }
            if (photo != null && card.imageBase != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = showPhoto,
                        onClick = { showPhoto = true },
                        label = { Text("Mein Foto") },
                        shape = RoundedCornerShape(50),
                    )
                    FilterChip(
                        selected = !showPhoto,
                        onClick = { showPhoto = false },
                        label = { Text("Kartenbild") },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(card.name, style = MaterialTheme.typography.headlineMedium)
            Text(
                listOfNotNull(
                    card.setName.takeIf { it.isNotBlank() },
                    card.numberLabel.takeIf { it.isNotBlank() },
                    card.rarity,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            PricePanel(prices = prices, updated = formatIsoDay(card.priceUpdated))

            if (card.hasNormal && card.hasHolo) {
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Variante",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilterChip(
                        selected = item.variant == VARIANT_NORMAL,
                        onClick = { vm.setVariant(item.uid, VARIANT_NORMAL) },
                        label = { Text("Normal") },
                        shape = RoundedCornerShape(50),
                    )
                    FilterChip(
                        selected = item.variant == VARIANT_HOLO,
                        onClick = { vm.setVariant(item.uid, VARIANT_HOLO) },
                        label = { Text("Holo / Reverse") },
                        shape = RoundedCornerShape(50),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Kategorie",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GroupPicker(
                groups = groups,
                selectedId = item.groupId,
                onSelect = { vm.setGroup(item.uid, it) },
                onCreate = { name -> vm.createGroup(name) { id -> vm.setGroup(item.uid, id) } },
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Anzahl", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Zusammen ${formatEuro(item.totalPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(
                    onClick = { vm.setQuantity(item.uid, item.quantity - 1) },
                    enabled = item.quantity > 1,
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Weniger")
                }
                Text(
                    item.quantity.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                FilledTonalIconButton(onClick = { vm.setQuantity(item.uid, item.quantity + 1) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Mehr")
                }
            }

            Spacer(Modifier.height(18.dp))
            val refreshing = refreshingUid == item.uid
            Button(
                onClick = { vm.refreshOne(item.uid) },
                enabled = !refreshing,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Preis aktualisieren")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cardmarketUrl(card))))
                    }.onFailure { vm.notify("Kein Browser gefunden.") }
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Auf Cardmarket ansehen")
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Karte löschen?") },
            text = { Text("${card.name} und das gespeicherte Foto werden aus der Sammlung entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onBack()
                    vm.delete(item.uid)
                }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
fun PricePanel(prices: Prices?, updated: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (prices != null) Mint else MaterialTheme.colorScheme.outline),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "CARDMARKET-PREISTREND",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        if (prices == null) {
            Text("Kein Preis verfügbar", style = MaterialTheme.typography.titleLarge)
            Text(
                "Für diese Karte liefert Cardmarket aktuell keinen Preis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                formatEuro(prices.headline),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PriceStat("Ab", prices.low, Modifier.weight(1f))
                PriceStat("Ø 1 Tag", prices.avg1, Modifier.weight(1f))
                PriceStat("Ø 7 Tage", prices.avg7, Modifier.weight(1f))
                PriceStat("Ø 30 Tage", prices.avg30, Modifier.weight(1f))
            }
            if (updated != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Preisstand von Cardmarket: $updated",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PriceStat(label: String, value: Double?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            formatEuro(value),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
