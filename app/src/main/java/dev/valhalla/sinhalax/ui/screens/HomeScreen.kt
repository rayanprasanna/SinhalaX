package dev.valhalla.sinhalax.ui.screens

import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import dev.valhalla.sinhalax.CrashLog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.valhalla.sinhalax.R
import dev.valhalla.sinhalax.ui.ImeStatus
import dev.valhalla.sinhalax.ui.Tab
import dev.valhalla.sinhalax.ui.theme.Success

@Composable
fun HomeScreen(status: ImeStatus, onOpenTab: (Tab) -> Unit, onOpenPrivacy: () -> Unit) {
    val context = LocalContext.current
    var tryText by rememberSaveable { mutableStateOf("") }
    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Image(
                    painterResource(R.drawable.sinhalax_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("SinhalaX", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "අපේ අකුරු, අපේ අනන්‍යතාව",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (status.ready) Success else MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (status.ready) "SinhalaX is active" else "Setup not finished",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            when {
                                status.ready -> "You're typing with SinhalaX."
                                !status.enabled -> "Enable SinhalaX in keyboard settings."
                                else -> "Select SinhalaX as your keyboard."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!status.ready) {
                        FilledTonalButton(onClick = {
                            if (!status.enabled) ImeStatus.openEnableSettings(context) else ImeStatus.showPicker(context)
                        }) { Text(if (!status.enabled) "Enable" else "Select") }
                    }
                }
            }
        }

        crash?.let { report ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "SinhalaX stopped unexpectedly",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            "That's why your phone may have switched back to another keyboard. " +
                                "Copy the details and send them to the developer. Nothing was sent automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            report.lineSequence().take(6).joinToString("\n"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copy details") }
                            TextButton(onClick = {
                                CrashLog.clear(context)
                                crash = null
                            }) { Text("Dismiss") }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = tryText,
                onValueChange = { tryText = it },
                placeholder = { Text("Try it: type \"kohomadha\" and press space") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(Icons.Outlined.Palette, "Themes", "Colours & RGB", Modifier.weight(1f)) { onOpenTab(Tab.THEMES) }
                Tile(Icons.Outlined.Tune, "Settings", "Size, sound, hints", Modifier.weight(1f)) { onOpenTab(Tab.SETTINGS) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(Icons.AutoMirrored.Outlined.MenuBook, "How to type", "Singlish guide", Modifier.weight(1f)) { onOpenTab(Tab.GUIDE) }
                Tile(Icons.Outlined.Lock, "Privacy", "100% on-device", Modifier.weight(1f), onOpenPrivacy)
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Coming next", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "GIFs • English word suggestions • Wijesekara layout • Downloadable fonts • " +
                                "Sticker maker • Tamil layout",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
