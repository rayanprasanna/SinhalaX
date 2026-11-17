package dev.valhalla.sinhalax.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valhalla.sinhalax.engine.SinglishScheme
import dev.valhalla.sinhalax.engine.SinglishTransliterator

private data class GuideRow(val letter: String, val keys: String, val example: String? = null)

/** The guide is generated from the engine's own table, so it can never go out of date. */
@Composable
fun GuideScreen() {
    val engine = remember { SinglishTransliterator() }
    var input by rememberSaveable { mutableStateOf("") }

    val vowels = remember {
        SinglishScheme.independentVowels.entries
            .groupBy({ it.value }, { it.key })
            .map { (letter, keys) ->
                GuideRow(letter, keys.joinToString(" / "), "k${keys.first()} → ${engine.transliterate("k" + keys.first())}")
            }
    }
    val consonants = remember {
        SinglishScheme.consonants.entries
            .groupBy({ it.value }, { it.key })
            .map { (letter, keys) -> GuideRow(letter, keys.joinToString(" / ")) }
    }
    val signs = remember {
        SinglishScheme.specials.map { (key, letter) ->
            GuideRow(letter, key, "si${key}hala → ${engine.transliterate("si${key}hala")}".takeIf { key == "x" })
        }
    }
    val joined = remember {
        listOf("kra", "pradhhaanaya", "sathya", "vidhyaava", "sixhala", "laxkaava").map {
            GuideRow(engine.transliterate(it), it)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { ScreenTitle("How to type", "Type Sinhala the way it sounds, in English letters.") }

        item {
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Try the converter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("mama sixhalen liyanavaa") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (input.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            engine.transliterate(input),
                            fontSize = 26.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        item { SectionLabel("Vowels — ස්වර") }
        items(vowels) { GuideLine(it) }
        item { SectionLabel("Consonants — ව්‍යංජන") }
        items(consonants) { GuideLine(it) }
        item { SectionLabel("Signs") }
        items(signs) { GuideLine(it) }
        item { SectionLabel("Joined letters") }
        items(joined) { GuideLine(it) }

        item { SectionLabel("Tips") }
        item {
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "\"a\" is built in: ma → ම, maa → මා, mi → මි.",
                        "Shift gives the other letter: n → න, N → ණ; l → ල, L → ළ.",
                        "The first suggestion (gold) is what space will type. Tap any other to pick it; the last one keeps your English letters.",
                        "Auto-correct fixes common slips: kohomada → කොහොමද, lankava → ලංකාව. Press backspace right after to undo.",
                        "SinhalaX learns the names and words you use — on your phone only. Manage them in Settings → Learned words.",
                        "Swipe left or right on the space bar to move the cursor.",
                        "Hold delete to keep deleting. Hold a top-row key for a number.",
                        "Passwords always use English, with no suggestions.",
                    ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun GuideLine(row: GuideRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.letter,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(72.dp),
        )
        Text(row.keys, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        if (row.example != null) {
            Text(row.example, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
