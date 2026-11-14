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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Style,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
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

@Composable
fun PricePill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color(0xFF231A00),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Gold)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
