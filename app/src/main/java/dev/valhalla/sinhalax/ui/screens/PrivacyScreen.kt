package dev.valhalla.sinhalax.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                ScreenTitle("Security & privacy")
            }
        }
        item {
            InfoCard(
                Icons.Outlined.CloudOff,
                "Your typing never goes online",
                "Typing, suggestions and fancy text all work offline. The app goes online only for the Themes store " +
                    "(backgrounds and sounds you pick) and for GIFs, stickers, memes and AI Emoji, while you have them open. " +
                    "Nothing you type in other apps is ever sent.",
            )
        }
        item {
            InfoCard(
                Icons.Outlined.Public,
                "GIFs, stickers and AI Emoji by KLIPY",
                "When you open them, SinhalaX asks KLIPY for lists and sends a random ID made for this install " +
                    "(not your name, number or phone ID). Search words you type in the Studio, and descriptions you give " +
                    "for AI Emoji, are sent to KLIPY. When you send one, KLIPY is told which item was shared. " +
                    "KLIPY's own privacy policy applies.",
            )
        }
        item {
            InfoCard(
                Icons.Outlined.Key,
                "No keylogging",
                "What you type is converted on your phone and handed straight to the app you're typing in. It is never stored or uploaded.",
            )
        }
        item {
            InfoCard(
                Icons.Outlined.ContentPaste,
                "Clipboard stays on your phone",
                "Clipboard history is saved only on this device. Anything apps mark as sensitive, and anything copied in a password field, is skipped. You can turn it off or clear it in Settings.",
            )
        }
        item {
            InfoCard(
                Icons.Outlined.Memory,
                "On-device intelligence",
                "Suggestions, auto-correct and learned words all run on your phone. Learned words never leave it, " +
                    "are never collected in password or incognito fields, and can be viewed, deleted or turned off in Settings.",
            )
        }
        item {
            InfoCard(
                Icons.Outlined.Public,
                "Future online features",
                "The theme store only asks the SinhalaX server for lists of backgrounds and sounds, then downloads the ones you pick. " +
                    "Future features like GIF search will be clearly marked and only send what you type into that search box — never your normal typing.",
            )
        }
    }
}
