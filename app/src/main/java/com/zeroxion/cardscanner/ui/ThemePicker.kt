package com.zeroxion.cardscanner.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Auswahl der Farbwelt. Die App färbt sich sofort um, die Wahl bleibt gespeichert. */
@Composable
fun ThemePickerSheet(
    current: TypeTheme,
    onPick: (TypeTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Text("Farbwelt", style = MaterialTheme.typography.titleLarge)
            Text(
                "Wähle deinen Lieblingstyp – die ganze App färbt sich danach ein.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            val time = rememberArtTime()
            TypeTheme.entries.chunked(3).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    row.forEach { theme ->
                        ThemeTile(
                            theme = theme,
                            time = { time.value },
                            selected = theme == current,
                            onClick = { onPick(theme) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ThemeTile(theme: TypeTheme, time: () -> Float, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val ring by animateDpAsState(if (selected) 2.5.dp else 1.dp, label = "themeRing")
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(theme.backgroundTop, Night)),
                shape,
            )
            .border(ring, if (selected) theme.accent else Color.White.copy(alpha = 0.16f), shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Gemalte Motive der Farbwelt, verkleinert und animiert.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(theme.backgroundTop, Night))),
        ) {
            drawTypeArt(theme, time(), scale = 0.42f)
        }
        Spacer(Modifier.height(8.dp))
        // Mini-Vorschau: Wert-Kachel mit Akzent-Pille.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(theme.heroBrush),
            contentAlignment = Alignment.BottomStart,
        ) {
            Box(
                Modifier
                    .padding(8.dp)
                    .size(width = 34.dp, height = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(theme.accentBrush),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(theme.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Ausgewählt", tint = Ink, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            theme.glows.forEach { color ->
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            theme.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) theme.accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}
