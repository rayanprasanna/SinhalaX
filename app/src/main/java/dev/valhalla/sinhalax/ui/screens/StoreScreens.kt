package dev.valhalla.sinhalax.ui.screens

import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import dev.valhalla.sinhalax.store.SoundIcons
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.valhalla.sinhalax.SinhalaXApp
import dev.valhalla.sinhalax.data.KeyboardSettings
import dev.valhalla.sinhalax.store.RemoteImage
import dev.valhalla.sinhalax.store.StoreApi
import dev.valhalla.sinhalax.store.parseHexColor
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ backgrounds

@Composable
fun BackgroundStore(app: SinhalaXApp, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val backgrounds = remember { mutableStateListOf<StoreApi.RemoteBackground>() }
    var categories by remember { mutableStateOf<List<StoreApi.Category>>(emptyList()) }
    var category by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var applyingId by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    suspend fun loadPage(next: Int) {
        loading = true
        error = null
        try {
            val result = StoreApi.backgrounds(next, category)
            if (next == 1) backgrounds.clear()
            backgrounds += result.items
            page = result.page
            totalPages = result.totalPages
        } catch (e: Exception) {
            error = "Couldn't reach the theme store. Check your connection."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        categories = runCatching { StoreApi.backgroundCategories() }.getOrDefault(emptyList())
    }
    LaunchedEffect(category, reloadKey) { loadPage(1) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (settings.backgroundPath.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "A background is in use.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { scope.launch { app.store.removeBackground() } }) { Text("Remove") }
                }
            }
        }
        if (categories.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                    }
                    items(categories) { c ->
                        FilterChip(
                            selected = category == c.name,
                            onClick = { category = c.name },
                            label = { Text("${c.name} (${c.count})") },
                        )
                    }
                }
            }
        }

        items(backgrounds, key = { it.id }) { bg ->
            BackgroundCard(
                bg = bg,
                selected = bg.id == settings.backgroundId && settings.backgroundPath.isNotEmpty(),
                applying = applyingId == bg.id,
            ) {
                if (applyingId != null) return@BackgroundCard
                applyingId = bg.id
                scope.launch {
                    try {
                        app.store.applyBackground(bg)
                    } catch (e: Exception) {
                        error = "Couldn't download \"${bg.title}\". Try again."
                    } finally {
                        applyingId = null
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> CircularProgressIndicator(Modifier.size(28.dp))
                    error != null -> StoreError(error!!) {
                        if (backgrounds.isEmpty()) reloadKey++ else scope.launch { loadPage(page + 1) }
                    }
                    backgrounds.isEmpty() -> Text(
                        "No backgrounds here yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    page < totalPages -> OutlinedButton(onClick = { scope.launch { loadPage(page + 1) } }) { Text("Load more") }
                }
            }
        }
    }
}

@Composable
private fun BackgroundCard(
    bg: StoreApi.RemoteBackground,
    selected: Boolean,
    applying: Boolean,
    onClick: () -> Unit,
) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f),
        ) {
            RemoteImage(
                url = bg.thumbUrl,
                cacheKey = bg.sha256.take(20) + "_thumb",
                placeholder = parseHexColor(bg.dominantColor),
                contentDescription = bg.title,
                modifier = Modifier.fillMaxSize(),
            )
            // Sample label in the colour the keyboard will use.
            Text(
                "අ a",
                color = parseHexColor(bg.keyTextColor, Color.White),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
            when {
                applying -> CircularProgressIndicator(
                    Modifier
                        .align(Alignment.Center)
                        .size(28.dp),
                )
                selected -> Icon(
                    Icons.Filled.CheckCircle, "In use",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
        }
        Text(
            bg.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

// ------------------------------------------------------------------ sounds

/**
 * Sound packs as an icon grid. One tap = download (if needed), play it once and switch the keyboard to it.
 */
@Composable
fun SoundStore(app: SinhalaXApp, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sounds by remember { mutableStateOf<List<StoreApi.RemoteSound>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var applyingId by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }

    fun playFile(path: String) {
        runCatching {
            player.reset()
            player.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build(),
            )
            player.setVolume(settings.soundVolume, settings.soundVolume)
            player.setDataSource(path)
            player.setOnPreparedListener { it.start() }
            player.prepareAsync()
        }
    }

    LaunchedEffect(reloadKey) {
        error = null
        sounds = null
        try {
            sounds = StoreApi.soundPacks().items
        } catch (e: Exception) {
            error = "Couldn't reach the theme store. Check your connection."
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(84.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsCard {
                SwitchRow("Sound on keypress", null, settings.sound) { v ->
                    scope.launch { app.settings.update { it.copy(sound = v) } }
                }
                SliderRow(
                    "Volume", settings.soundVolume, 0.1f..1f,
                    format = { "${(it * 100).toInt()}%" },
                ) { v -> scope.launch { app.settings.update { it.copy(soundVolume = v) } } }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Tap a sound to hear it and use it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }

        item(key = "system") {
            SoundTile(
                icon = Icons.Outlined.PhoneAndroid,
                title = "Default",
                selected = settings.soundPackId.isEmpty(),
                applying = false,
            ) {
                audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, settings.soundVolume)
                scope.launch { app.store.useSystemSound() }
            }
        }

        val list = sounds
        when {
            error != null -> item(span = { GridItemSpan(maxLineSpan) }) { StoreError(error!!) { reloadKey++ } }
            list == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
            list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "More sounds are coming soon.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
            else -> items(list, key = { it.id }) { sound ->
                SoundTile(
                    icon = SoundIcons.forSound(sound.title, sound.description),
                    title = sound.title,
                    selected = sound.id == settings.soundPackId,
                    applying = applyingId == sound.id,
                ) {
                    if (applyingId != null) return@SoundTile
                    applyingId = sound.id
                    scope.launch {
                        try {
                            val path = app.store.applySound(sound)
                            playFile(path)
                        } catch (e: Exception) {
                            error = "Couldn't download \"${sound.title}\". Try again."
                        } finally {
                            applyingId = null
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundTile(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    applying: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = "Use $title", onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (applying) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ------------------------------------------------------------------ shared

@Composable
private fun StoreError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text("Try again") }
    }
}
