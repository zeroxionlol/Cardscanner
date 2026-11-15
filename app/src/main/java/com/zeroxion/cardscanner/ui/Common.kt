package com.zeroxion.cardscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val euro: NumberFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
private val dayFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())

fun formatEuro(value: Double?): String =
    if (value == null || value <= 0.0) "–" else euro.format(value)

fun formatIsoDay(iso: String?): String? = try {
    if (iso == null) null else dayFormat.format(Instant.parse(iso))
} catch (e: Exception) {
    null
}

fun formatMillisDay(millis: Long): String? =
    if (millis <= 0L) null else dayFormat.format(Instant.ofEpochMilli(millis))

/** Kartenbild (Foto-Datei oder Web-Adresse) mit ruhigem Platzhalter. */
@Composable
fun CardImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier.background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Style,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.25f),
        )
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private val assetLanguage = Regex("""assets\.tcgdex\.net/[a-z-]+/""")

/** Mögliche Adressen für ein Kartenbild: erst die gewünschte Sprache, dann Englisch. */
fun artUrls(imageBase: String?, high: Boolean): List<String> {
    if (imageBase.isNullOrBlank()) return emptyList()
    val english = assetLanguage.replace(imageBase, "assets.tcgdex.net/en/")
    val sizes = if (high) listOf("high", "low") else listOf("low", "high")
    return listOf(imageBase, english).distinct().flatMap { base -> sizes.map { "$base/$it.webp" } }
}

/** Offizielles Kartenbild. Fehlt es in einer Sprache oder Größe, wird die nächste versucht. */
@Composable
fun CardArt(
    imageBase: String?,
    modifier: Modifier = Modifier,
    high: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val urls = remember(imageBase, high) { artUrls(imageBase, high) }
    var index by remember(imageBase, high) { mutableIntStateOf(0) }
    Box(
        modifier = modifier.background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Style,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.25f),
        )
        val url = urls.getOrNull(index)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
                onError = { if (index < urls.size) index += 1 },
            )
        }
    }
}

@Composable
fun PricePill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Ink,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFE27A), Gold)))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
