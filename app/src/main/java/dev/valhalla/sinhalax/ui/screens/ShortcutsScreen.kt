package dev.valhalla.sinhalax.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.valhalla.sinhalax.SinhalaXApp
import dev.valhalla.sinhalax.data.KeyboardSettings
import dev.valhalla.sinhalax.data.Shortcut
import dev.valhalla.sinhalax.data.ShortcutRepository
import kotlinx.coroutines.launch

/**
 * Shortcuts (text expansion): type a short code, then space, and the keyboard puts in your
 * saved text. Backspace straight after puts the code back. Stored only on this phone.
 */
@Composable
fun ShortcutsScreen(app: SinhalaXApp, settings: KeyboardSettings, onBack: () -> Unit) {
    val items by app.shortcuts.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    // null = closed; Shortcut("", "") = new; otherwise editing that one.
    var editing by remember { mutableStateOf<Shortcut?>(null) }
    var deleting by remember { mutableStateOf<Shortcut?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            ScreenTitle("Shortcuts", "${items.size} shortcuts · on this phone only", Modifier.weight(1f))
            Switch(
                checked = settings.shortcutsEnabled,
                onCheckedChange = { v -> scope.launch { app.settings.update { it.copy(shortcutsEnabled = v) } } },
            )
        }

        Text(
            "Type the code, then space. Example: gm → සුභ උදෑසනක්! Works in Sinhala and English mode. " +
                "Backspace right after puts the code back.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.key }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { editing = item }
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.key,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .widthIn(min = 28.dp),
                    )
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    IconButton(onClick = { deleting = item }) {
                        Icon(Icons.Outlined.Delete, "Delete ${item.key}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            ExtendedFloatingActionButton(
                onClick = { editing = Shortcut("", "") },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Add shortcut") },
            )
        }
    }

    editing?.let { current ->
        ShortcutDialog(
            initial = current,
            existingKeys = items.map { it.key }.toSet() - current.key,
            onDismiss = { editing = null },
            onSave = { shortcut ->
                scope.launch { app.shortcuts.put(shortcut, replacingKey = current.key.ifEmpty { null }) }
                editing = null
            },
        )
    }
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${target.key}”?") },
            text = { Text(target.text) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.shortcuts.remove(target.key) }
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ShortcutDialog(
    initial: Shortcut,
    existingKeys: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Shortcut) -> Unit,
) {
    var key by remember { mutableStateOf(initial.key) }
    var text by remember { mutableStateOf(initial.text) }
    val cleanKey = ShortcutRepository.normalizeKey(key)
    val keyError = when {
        key.isNotEmpty() && cleanKey != key.trim().lowercase() -> "Use English letters and numbers only"
        cleanKey in existingKeys -> "You already have “$cleanKey”"
        else -> null
    }
    val canSave = cleanKey.isNotEmpty() && text.isNotBlank() && keyError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.key.isEmpty()) "New shortcut" else "Edit shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.take(ShortcutRepository.MAX_KEY) },
                    label = { Text("Code (e.g. gm)") },
                    singleLine = true,
                    isError = keyError != null,
                    supportingText = { Text(keyError ?: "Type this, then space") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(2000) },
                    label = { Text("Text it becomes") },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = { onSave(Shortcut(cleanKey, text.trim())) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
