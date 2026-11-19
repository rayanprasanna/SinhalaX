package dev.valhalla.sinhalax.ui.screens

import androidx.compose.material3.FilterChip
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.valhalla.sinhalax.engine.FancyText
import dev.valhalla.sinhalax.engine.FmConverter
import dev.valhalla.sinhalax.ime.StickerSender
import dev.valhalla.sinhalax.store.KlipyApi
import dev.valhalla.sinhalax.keyboard.icon
import dev.valhalla.sinhalax.store.AnimatedRemoteImage
import dev.valhalla.sinhalax.store.RemoteImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Fancy text", "FM converter", "GIFs")
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            ScreenTitle("Studio", "Make your text stand out.")
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                tabs.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                    ) { Text(label, maxLines = 1) }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> FancyStudio()
                1 -> FmStudio()
                else -> MediaStudio()
            }
        }
    }
}

// ------------------------------------------------------------------ shared

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("SinhalaX", text))
    // Android 13+ shows its own "Copied" confirmation.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun pasteText(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()

// ------------------------------------------------------------------ fancy text

@Composable
private fun FancyStudio() {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("SinhalaX") }
    val text = input.ifBlank { "SinhalaX" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Your text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            Text(
                "Tap any style to copy it. Styles change English letters and numbers; frames work with Sinhala too. " +
                    "Tip: tap Aa on the keyboard to type in a style directly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        }
        item { SectionLabel("Styles") }
        items(FancyText.all, key = { it.id }) { style ->
            ResultRow(style.name, style.apply(text)) { copy(context, style.apply(text)) }
        }
        item { SectionLabel("Frames") }
        items(FancyText.frames) { (start, end) ->
            val framed = start + text + end
            ResultRow(null, framed) { copy(context, framed) }
        }
    }
}

@Composable
private fun ResultRow(label: String?, value: String, onCopy: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClickLabel = "Copy", onClick = onCopy)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(value, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Outlined.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ------------------------------------------------------------------ FM converter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FmStudio() {
    val context = LocalContext.current
    var toFm by rememberSaveable { mutableStateOf(true) }
    var input by rememberSaveable { mutableStateOf("") }
    val output = remember(input, toFm) {
        if (input.isBlank()) "" else if (toFm) FmConverter.fromUnicode(input) else FmConverter.toUnicode(input)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = toFm, onClick = { toFm = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text("Unicode → FM")
                }
                SegmentedButton(selected = !toFm, onClick = { toFm = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text("FM → Unicode")
                }
            }
        }
        item {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(if (toFm) "Sinhala Unicode text" else "FM Abhaya text") },
                placeholder = { Text(if (toFm) "ශ්‍රී ලංකාව" else "Y%S ,xldj") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    IconButton(onClick = { pasteText(context)?.let { input = it } }) {
                        Icon(Icons.Outlined.ContentPaste, "Paste")
                    }
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (toFm) "FM Abhaya" else "Unicode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    // Swap: the result becomes the new input.
                    input = output
                    toFm = !toFm
                }, enabled = output.isNotEmpty()) { Icon(Icons.Outlined.SwapVert, "Swap") }
            }
            SettingsCard {
                SelectionContainer {
                    Text(
                        output.ifEmpty { "The converted text appears here." },
                        fontSize = 18.sp,
                        fontFamily = if (toFm) FontFamily.Monospace else FontFamily.Default,
                        color = if (output.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                            .padding(16.dp),
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { copy(context, output) }, enabled = output.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Copy")
                }
                OutlinedButton(onClick = { input = "" }, enabled = input.isNotEmpty()) { Text("Clear") }
            }
        }
        item {
            InfoCard(
                Icons.Outlined.ContentCopy,
                if (toFm) "Using FM text" else "Using Unicode text",
                if (toFm) {
                    "Paste into Photoshop, Illustrator, Canva or Word, then choose the FM Abhaya font " +
                        "(or another FM font such as FM Malithi). It will look like Sinhala there."
                } else {
                    "Turns text from old FM-font documents into normal Sinhala that works in any app, " +
                        "on the web and on phones."
                },
            )
        }
        item {
            Text(
                "Converter based on the Pandukabhaya FM Abhaya mapping (akuruAI) and the UCSC LTRL Sinhala " +
                    "converter, both MIT License.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ GIFs, stickers, memes (KLIPY)

@Composable
private fun MediaStudio() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(KlipyApi.Kind.GIFS) }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<KlipyApi.Media>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sharingKey by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(kind, submitted, retry) {
        if (!KlipyApi.isConfigured) {
            error = "This needs a KLIPY API key. Add KLIPY_API_KEY to local.properties and rebuild."
            return@LaunchedEffect
        }
        items = null
        error = null
        try {
            items = if (submitted.isBlank()) KlipyApi.trending(context, kind, perPage = 48).items
            else KlipyApi.search(context, kind, submitted, perPage = 48).items
        } catch (e: Exception) {
            error = "Couldn't load ${kind.label}. Check your connection."
        }
    }

    val wide = kind.isWide
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 150.dp else 96.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KlipyApi.Kind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(k.label) },
                            leadingIcon = { Icon(k.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search KLIPY") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitted = query.trim() }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (submitted.isBlank()) "Trending · tap to share" else "Results for \"$submitted\" · tap to share",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    // Attribution required by the KLIPY API terms.
                    Text("Powered by KLIPY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        val list = items
        when {
            error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (KlipyApi.isConfigured) FilledTonalButton(onClick = { retry++ }, modifier = Modifier.padding(top = 8.dp)) { Text("Try again") }
                }
            }
            list == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Nothing found.", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> items(list, key = { it.cacheKey }) { media ->
                Box(
                    Modifier
                        .aspectRatio(if (wide) media.aspect else 1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(onClickLabel = "Share ${media.title}") {
                            if (sharingKey != null) return@clickable
                            sharingKey = media.cacheKey
                            scope.launch {
                                shareMedia(context, media, submitted.ifBlank { null })
                                sharingKey = null
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedRemoteImage(
                        animatedUrl = media.animatedUrl,
                        stillUrl = media.thumbUrl,
                        cacheKey = media.cacheKey,
                        placeholder = Color.Transparent,
                        contentDescription = media.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (kind.cropToFill) 0.dp else 8.dp),
                        fit = !kind.cropToFill,
                    )
                    if (sharingKey == media.cacheKey) CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        }
    }
}

private suspend fun shareMedia(context: Context, media: KlipyApi.Media, query: String?) {
    val mime = media.sendOrder.firstOrNull() ?: return
    try {
        val file = KlipyApi.downloadForSending(context, media.files.getValue(mime), media.cacheKey, KlipyApi.extensionFor(mime))
        shareFile(context, file, mime, "Share")
        KlipyApi.Recents.add(context, media)
        KlipyApi.reportShare(context, media.kind, media.slug, query)
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't share this. Try again.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, file: java.io.File, mime: String, title: String) {
    val uri = FileProvider.getUriForFile(context, StickerSender.authority(context), file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
