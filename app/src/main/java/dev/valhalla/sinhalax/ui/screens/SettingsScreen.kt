package dev.valhalla.sinhalax.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.valhalla.sinhalax.SinhalaXApp
import dev.valhalla.sinhalax.data.KeyboardSettings
import dev.valhalla.sinhalax.keyboard.KeyboardPreview
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: SinhalaXApp,
    settings: KeyboardSettings,
    onOpenLearnedWords: () -> Unit,
    onOpenShortcuts: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    fun update(transform: (KeyboardSettings) -> KeyboardSettings) {
        scope.launch { app.settings.update(transform) }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            item { ScreenTitle("Settings", "Changes show in the keyboard below.") }

            item { SectionLabel("Typing") }
            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Start typing in", style = MaterialTheme.typography.bodyLarge)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            SegmentedButton(
                                selected = settings.startInSinhala,
                                onClick = { update { it.copy(startInSinhala = true) } },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) { Text("සිංහල") }
                            SegmentedButton(
                                selected = !settings.startInSinhala,
                                onClick = { update { it.copy(startInSinhala = false) } },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text("English") }
                        }
                    }
                    SwitchRow(
                        "Live Sinhala preview",
                        "Show the Sinhala word and alternatives (න/ණ, ල/ළ) while you type",
                        settings.livePreview,
                        Icons.Outlined.Translate,
                    ) { v -> update { it.copy(livePreview = v) } }
                    SwitchRow(
                        "Auto-capitalise (English)",
                        "Capital letter at the start of each sentence",
                        settings.autoCapitalize,
                        Icons.Outlined.TextFields,
                    ) { v -> update { it.copy(autoCapitalize = v) } }
                    SwitchRow(
                        "Sinhala letter hints",
                        "Small Sinhala letter on each key",
                        settings.showHints,
                        Icons.Outlined.Lightbulb,
                    ) { v -> update { it.copy(showHints = v) } }
                    SwitchRow(
                        "Number row",
                        "Always show 1–0 above the letters",
                        settings.numberRow,
                        Icons.Outlined.Dialpad,
                    ) { v -> update { it.copy(numberRow = v) } }
                }
            }

            item { SectionLabel("Smart suggestions") }
            item {
                SettingsCard {
                    SwitchRow(
                        "Dictionary suggestions",
                        "Suggest and complete Sinhala words from a 100,000-word dictionary",
                        settings.wordSuggestions,
                        Icons.AutoMirrored.Outlined.MenuBook,
                    ) { v -> update { it.copy(wordSuggestions = v) } }
                    SwitchRow(
                        "Auto-correct",
                        "Space fixes common Singlish slips (kohomada → කොහොමද). Backspace undoes it.",
                        settings.autoCorrect,
                        Icons.Outlined.Spellcheck,
                    ) { v -> update { it.copy(autoCorrect = v) } }
                    SwitchRow(
                        "Learn my words",
                        "Remember names and words you use, on this phone only",
                        settings.learnWords,
                        Icons.Outlined.School,
                    ) { v -> update { it.copy(learnWords = v) } }
                    TextButton(
                        onClick = onOpenLearnedWords,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("View learned words") }
                }
            }

            item { SectionLabel("Tools") }
            item {
                SettingsCard {
                    SwitchRow(
                        "Shortcuts",
                        "Type a short code like gm, then space, to put in your saved text",
                        settings.shortcutsEnabled,
                        Icons.Outlined.Bolt,
                    ) { v -> update { it.copy(shortcutsEnabled = v) } }
                    TextButton(
                        onClick = onOpenShortcuts,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("Manage shortcuts") }
                    SwitchRow(
                        "Slide gestures",
                        "Slide on space to move the cursor; slide left on ⌫ to select words, let go to delete them",
                        settings.slideGestures,
                        Icons.Outlined.SwipeLeft,
                    ) { v -> update { it.copy(slideGestures = v) } }
                    SwitchRow(
                        "Incognito",
                        "Don't learn words or keep clipboard, emoji and GIF history (🕶 shows on the toolbar)",
                        settings.incognito,
                        Icons.Outlined.VisibilityOff,
                    ) { v -> update { it.copy(incognito = v) } }
                    Text(
                        "More tools (text edit, one-hand, number pad, calculator, repeat text) are in the keyboard: " +
                            "tap ⚙ (or SinhalaX) on the toolbar. Long-press any tool there to pin it to the toolbar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            item { SectionLabel("Size") }
            item {
                SettingsCard {
                    SliderRow(
                        "Keyboard height", settings.heightScale, 0.8f..1.3f,
                        format = { "${(it * 100).roundToInt()}%" },
                    ) { v -> update { it.copy(heightScale = v) } }
                    SliderRow(
                        "Key text size", settings.keyFontScale, 0.8f..1.3f,
                        format = { "${(it * 100).roundToInt()}%" },
                    ) { v -> update { it.copy(keyFontScale = v) } }
                    SliderRow(
                        "Bottom padding", settings.bottomPaddingDp.toFloat(), 0f..48f, steps = 11,
                        format = { "${it.roundToInt()} dp" },
                    ) { v -> update { it.copy(bottomPaddingDp = v.roundToInt()) } }
                }
            }

            item { SectionLabel("Feedback") }
            item {
                SettingsCard {
                    SwitchRow("Vibrate on keypress", null, settings.vibrate, Icons.Outlined.Vibration) { v ->
                        update { it.copy(vibrate = v) }
                    }
                    SwitchRow("Sound on keypress", null, settings.sound, Icons.AutoMirrored.Outlined.VolumeUp) { v ->
                        update { it.copy(sound = v) }
                    }
                }
            }

            item { SectionLabel("Clipboard") }
            item {
                SettingsCard {
                    SwitchRow(
                        "Clipboard history",
                        "Keeps your last ${dev.valhalla.sinhalax.data.ClipboardRepository.MAX_ITEMS} copies on this phone. Passwords are never saved.",
                        settings.clipboardHistory,
                        Icons.Outlined.ContentPaste,
                    ) { v -> update { it.copy(clipboardHistory = v) } }
                    TextButton(
                        onClick = { scope.launch { app.clipboard.clear() } },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("Clear clipboard history") }
                }
            }
            item {
                SectionLabel("Privacy")
            }
            item {
                InfoCard(
                    Icons.Outlined.VisibilityOff,
                    "Nothing leaves your phone",
                    "Typing works offline and never sends what you type. Only the Themes store, GIFs, stickers and memes, Translate (the translate box text), voice typing and emoji style images go online, while you use them.",
                )
            }
            item {
                Text(
                    "Sinhala dictionary based on the Word Frequency List for Sinhala, " +
                        "A. Fernando & G. Dias, University of Moratuwa (ICON 2021).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
        KeyboardPreview(settings)
    }
}
