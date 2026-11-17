package dev.valhalla.sinhalax.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.valhalla.sinhalax.SinhalaXApp

/** Words SinhalaX has learned from your typing. Stored only on this phone. */
@Composable
fun LearnedWordsScreen(app: SinhalaXApp, onBack: () -> Unit) {
    val words by app.prediction.learnedWords.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.prediction.ensureLoaded() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            ScreenTitle("Learned words", "${words.size} words · on this phone only", Modifier.weight(1f))
            if (words.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("Clear all", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (words.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No words yet.\nWords you type in Sinhala are counted here so suggestions get better for you.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(words, key = { it.word }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.word, fontSize = 20.sp, modifier = Modifier.weight(1f))
                        Text(
                            "×${entry.count}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        IconButton(onClick = { app.prediction.remove(entry.word) }) {
                            Icon(Icons.Outlined.Delete, "Delete ${entry.word}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all learned words?") },
            text = { Text("Suggestions will go back to the built-in dictionary only.") },
            confirmButton = {
                TextButton(onClick = {
                    app.prediction.clear()
                    confirmClear = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
