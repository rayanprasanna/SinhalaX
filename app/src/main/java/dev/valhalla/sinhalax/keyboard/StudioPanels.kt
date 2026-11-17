package dev.valhalla.sinhalax.keyboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valhalla.sinhalax.engine.FancyText
import dev.valhalla.sinhalax.store.KlipyApi
import dev.valhalla.sinhalax.store.RemoteImage
import kotlinx.coroutines.launch

/** Outcome of tapping a GIF / sticker / meme, shown as a short message in the panel. */
enum class StickerOutcome { SENT, NOT_SUPPORTED, FAILED }

/** Sticker icon (square with a peeled corner) drawn as a vector, so it looks the same on every phone. */
val StickerIcon: ImageVector by lazy {
    ImageVector.Builder("Sticker", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(14f, 20.5f); lineTo(7f, 20.5f)
            quadTo(3.5f, 20.5f, 3.5f, 17f); lineTo(3.5f, 7f)
            quadTo(3.5f, 3.5f, 7f, 3.5f); lineTo(17f, 3.5f)
            quadTo(20.5f, 3.5f, 20.5f, 7f); lineTo(20.5f, 14f)
            lineTo(14f, 20.5f); close()
            moveTo(14f, 20.5f); lineTo(14f, 16.5f)
            quadTo(14f, 14f, 16.5f, 14f); lineTo(20.5f, 14f)
            moveTo(8.5f, 13f); quadTo(10.5f, 15.5f, 13f, 14.5f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(9.5f, 9f); arcToRelative(1.1f, 1.1f, 0f, true, true, -2.2f, 0f); arcToRelative(1.1f, 1.1f, 0f, true, true, 2.2f, 0f); close()
            moveTo(15.7f, 9f); arcToRelative(1.1f, 1.1f, 0f, true, true, -2.2f, 0f); arcToRelative(1.1f, 1.1f, 0f, true, true, 2.2f, 0f); close()
        }
    }.build()
}

// ------------------------------------------------------------------ fancy text

@Composable
fun FancyPanel(
    controller: KeyboardController,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    height: Dp,
) {
    val styles = remember { FancyText.all.filter { it.keyboardSafe } }
    val active = controller.fancyStyle
    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Text(
            "Fancy text for English letters and numbers",
            color = theme.toolbarText.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                FancyChip("Normal", "Normal", active == null, theme) {
                    feedback.press()
                    controller.setFancy(null)
                }
            }
            items(styles, key = { it.id }) { style ->
                FancyChip(style.apply("Fancy"), style.name, active?.id == style.id, theme) {
                    feedback.press()
                    controller.setFancy(style)
                }
            }
        }
    }
}

@Composable
private fun FancyChip(sample: String, name: String, selected: Boolean, theme: KeyboardTheme, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .clip(shape)
            .background(if (selected) theme.accent.copy(alpha = 0.25f) else theme.key)
            .then(if (selected) Modifier.border(1.5.dp, theme.accent, shape) else Modifier)
            .clickable(onClickLabel = name, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(sample, color = if (selected) theme.accent else theme.text, fontSize = 17.sp, maxLines = 1)
        Text(name, color = theme.hint, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------ GIFs, stickers, memes, AI Emoji (KLIPY)

/** Last tab used, remembered while the keyboard process lives. */
private var lastKind: KlipyApi.Kind = KlipyApi.Kind.GIFS

/**
 * KLIPY media: trending + category chips per kind (no typing needed inside the keyboard).
 * Goes online only while this panel is open.
 */
@Composable
fun MediaPanel(
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    height: Dp,
    canSend: Boolean,
    onSend: (suspend (KlipyApi.Media, String?) -> StickerOutcome)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(lastKind) }
    var categories by remember { mutableStateOf<List<KlipyApi.Category>>(emptyList()) }
    var selected by remember { mutableStateOf<KlipyApi.Category?>(null) }
    var items by remember { mutableStateOf<List<KlipyApi.Media>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var sendingKey by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(kind) {
        lastKind = kind
        selected = null
        categories = emptyList()
        if (KlipyApi.isConfigured) categories = runCatching { KlipyApi.categories(kind) }.getOrDefault(emptyList())
    }
    LaunchedEffect(kind, selected, retry) {
        if (!KlipyApi.isConfigured) {
            error = "GIFs and stickers aren't set up in this build."
            return@LaunchedEffect
        }
        items = null
        error = null
        try {
            val cat = selected
            items = if (cat == null) KlipyApi.trending(context, kind).items else KlipyApi.search(context, kind, cat.query).items
        } catch (e: Exception) {
            error = "Couldn't load ${kind.label}. Check your connection."
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { CategoryChip("Trending", selected == null, theme) { selected = null } }
            items(categories, key = { it.query }) { c -> CategoryChip(c.name, selected == c, theme) { selected = c } }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val list = items
            when {
                error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = theme.toolbarText, fontSize = 13.sp, textAlign = TextAlign.Center)
                    if (KlipyApi.isConfigured) {
                        Text(
                            "Try again",
                            color = theme.accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable { retry++ },
                        )
                    }
                }
                list == null -> CircularProgressIndicator(color = theme.accent, modifier = Modifier.size(28.dp))
                list.isEmpty() -> Text("Nothing here yet.", color = theme.toolbarText, fontSize = 13.sp)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (kind == KlipyApi.Kind.GIFS || kind == KlipyApi.Kind.MEMES) 100.dp else 72.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(list, key = { it.cacheKey }) { media ->
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.key.copy(alpha = 0.35f))
                                .clickable(onClickLabel = "Send ${media.title}") {
                                    val send = onSend ?: run {
                                        message = "Open the keyboard in a chat to send."
                                        return@clickable
                                    }
                                    if (sendingKey != null) return@clickable
                                    feedback.press()
                                    sendingKey = media.cacheKey
                                    scope.launch {
                                        message = when (send(media, selected?.query)) {
                                            StickerOutcome.SENT -> null
                                            StickerOutcome.NOT_SUPPORTED -> "This app doesn't accept ${kind.label} here."
                                            StickerOutcome.FAILED -> "Couldn't send. Try again."
                                        }
                                        sendingKey = null
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            RemoteImage(
                                url = media.thumbUrl,
                                cacheKey = media.cacheKey,
                                placeholder = Color.Transparent,
                                contentDescription = media.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(if (kind == KlipyApi.Kind.GIFS || kind == KlipyApi.Kind.MEMES) 0.dp else 4.dp),
                                fit = kind != KlipyApi.Kind.GIFS && kind != KlipyApi.Kind.MEMES,
                            )
                            if (kind == KlipyApi.Kind.GIFS) {
                                Text(
                                    "GIF",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                            if (sendingKey == media.cacheKey) {
                                CircularProgressIndicator(color = theme.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }

        if (message != null || (!canSend && onSend != null)) {
            Text(
                message ?: "This text box may not accept images.",
                color = theme.toolbarText.copy(alpha = 0.8f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(KlipyApi.Kind.entries.toList()) { k ->
                    KindTab(k.label, kind == k, theme) {
                        feedback.press()
                        kind = k
                    }
                }
            }
            // Attribution required by the KLIPY API terms.
            Text(
                "Powered by KLIPY",
                color = theme.toolbarText.copy(alpha = 0.75f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun KindTab(label: String, selected: Boolean, theme: KeyboardTheme, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) theme.accent else theme.toolbarText.copy(alpha = 0.8f),
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) theme.accent.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, theme: KeyboardTheme, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) theme.accent else theme.key)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (selected) theme.onAccent else theme.text, fontSize = 13.sp, maxLines = 1)
    }
}
