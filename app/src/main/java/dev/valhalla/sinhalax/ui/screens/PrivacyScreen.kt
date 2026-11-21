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
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VisibilityOff
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
                    "(backgrounds and sounds you pick), for GIFs, stickers and memes while you have them open, and " +
                    "for Translate, voice typing and emoji style images while you use them. Nothing else you type in other apps is ever sent.",
            )
        }
        item {
            InfoCard(
                Icons.Outlined.Public,
                "GIFs, stickers and memes by KLIPY",
                "When you open them, SinhalaX asks KLIPY for lists and sends a random ID made for this install " +
                    "(not your name, number or phone ID). Words you type into the \"Search KLIPY\" box — in the " +
                    "keyboard or the Studio — are sent to KLIPY as you type, to find results. Nothing typed into " +
                    "chats or other apps is. When you send one, KLIPY is told which item was shared. " +
                    "Recently sent items are listed on this phone only. KLIPY's own privacy policy applies.",
            )
        }
        item {
            InfoCard(
                Icons.Outlined.Translate,
                "Translate",
                "Only what you type into the keyboard's translate box is sent to the translation service (Microsoft Translator or Google Cloud Translation), while " +
                    "the box is open, to get the translation. Text typed into apps the normal way is not. " +
                    "That service's privacy policy applies to that text.",
            )
        }
        item {
            InfoCard(
                Icons.Outlined.Mic,
                "Voice typing",
                "The mic uses your phone's own speech service (usually Google's) only while the voice panel is " +
                    "listening. SinhalaX doesn't record or keep audio; it receives the words and types them. " +
                    "The microphone permission is asked for the first time you tap the mic.",
            )
        }
        item {
            InfoCard(
                Icons.Outlined.VisibilityOff,
                "Incognito and shortcuts",
                "Turn on Incognito (⚙ on the keyboard toolbar → Tools) and the keyboard learns no words and keeps " +
                    "no clipboard, emoji or GIF history until you turn it off. Your shortcuts are saved only on this phone.",
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
