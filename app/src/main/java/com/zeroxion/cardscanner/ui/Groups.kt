package com.zeroxion.cardscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.zeroxion.cardscanner.data.CardGroup

/** Dialog zum Benennen einer Kategorie oder eines Binders. */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = "z. B. Lenas Karten",
) {
    var text by remember { mutableStateOf(initial) }
    val valid = text.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 40) text = it },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text(placeholder) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(text.trim()) }),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(text.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
fun SetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
fun NewSetChip(onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text("Neue Kategorie") },
        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = RoundedCornerShape(50),
    )
}

/**
 * Auswahl, in welcher eigenen Kategorie eine Karte liegt: "Keine Kategorie", die vorhandenen
 * und "Neue Kategorie" (legt eine an und wählt sie gleich aus).
 */
@Composable
fun GroupPicker(
    groups: List<CardGroup>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by remember { mutableStateOf(false) }
    val active = groups.firstOrNull { it.id == selectedId }

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "none") {
            SetChip("Keine Kategorie", selected = active == null, onClick = { onSelect(null) })
        }
        items(groups, key = { it.id }) { group ->
            SetChip(group.name, selected = active?.id == group.id, onClick = { onSelect(group.id) })
        }
        item(key = "new") {
            NewSetChip(onClick = { creating = true })
        }
    }

    if (creating) {
        NameDialog(
            title = "Neue Kategorie",
            confirmLabel = "Anlegen",
            onConfirm = {
                creating = false
                onCreate(it)
            },
            onDismiss = { creating = false },
        )
    }
}
