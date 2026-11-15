package com.zeroxion.cardscanner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

data class NavEntry(val label: String, val icon: ImageVector)

/** Schwebende Navigationsleiste aus Milchglas; die gewählte Seite leuchtet Elektro-gelb. */
@Composable
fun GlassNavBar(
    entries: List<NavEntry>,
    selected: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 10.dp, top = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(shape)
                .hazeEffect(state = hazeState, style = GlassHaze)
                .border(1.dp, GlassBorder, shape)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                NavPill(
                    entry = entry,
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavPill(entry: NavEntry, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val content by animateColorAsState(
        if (selected) Ink else Color.White.copy(alpha = 0.72f),
        label = "navContent",
    )
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(Brush.verticalGradient(listOf(Color(0xFFFFE27A), Electric)), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(entry.icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Text(
            entry.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
        )
    }
}
