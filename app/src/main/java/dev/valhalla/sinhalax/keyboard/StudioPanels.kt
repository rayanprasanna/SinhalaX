package dev.valhalla.sinhalax.keyboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
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
import dev.valhalla.sinhalax.store.AnimatedRemoteImage
import dev.valhalla.sinhalax.store.RemoteImage
import kotlinx.coroutines.delay
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

/** "GIF" lettering in a rounded frame, drawn to match [StickerIcon] and [MemeIcon]. */
val GifIcon: ImageVector by lazy {
    ImageVector.Builder("Gif", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(6f, 4.5f); lineTo(18f, 4.5f)
            quadTo(21f, 4.5f, 21f, 7.5f); lineTo(21f, 16.5f)
            quadTo(21f, 19.5f, 18f, 19.5f); lineTo(6f, 19.5f)
            quadTo(3f, 19.5f, 3f, 16.5f); lineTo(3f, 7.5f)
            quadTo(3f, 4.5f, 6f, 4.5f); close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineCap = StrokeCap.Round,
        ) {
            // G
            moveTo(10.2f, 9.5f); lineTo(8.2f, 9.5f)
            quadTo(6.6f, 9.5f, 6.6f, 11.1f); lineTo(6.6f, 12.9f)
            quadTo(6.6f, 14.5f, 8.2f, 14.5f); lineTo(8.7f, 14.5f)
            quadTo(10.2f, 14.5f, 10.2f, 13f); lineTo(10.2f, 12.2f); lineTo(8.8f, 12.2f)
            // I
            moveTo(12.6f, 9.5f); lineTo(12.6f, 14.5f)
            // F
            moveTo(17.6f, 9.5f); lineTo(15f, 9.5f); lineTo(15f, 14.5f)
            moveTo(15f, 12f); lineTo(17f, 12f)
        }
    }.build()
}

/** A laughing face between top and bottom caption lines, the classic meme layout. */
val MemeIcon: ImageVector by lazy {
    ImageVector.Builder("Meme", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(6.5f, 3.5f); lineTo(17.5f, 3.5f)
            quadTo(20.5f, 3.5f, 20.5f, 6.5f); lineTo(20.5f, 17.5f)
            quadTo(20.5f, 20.5f, 17.5f, 20.5f); lineTo(6.5f, 20.5f)
            quadTo(3.5f, 20.5f, 3.5f, 17.5f); lineTo(3.5f, 6.5f)
            quadTo(3.5f, 3.5f, 6.5f, 3.5f); close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(8f, 7f); lineTo(16f, 7f) // top caption
            moveTo(8f, 17.3f); lineTo(16f, 17.3f) // bottom caption
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(8.6f, 10.6f); quadTo(9.6f, 9.4f, 10.6f, 10.6f) // laughing eyes
            moveTo(13.4f, 10.6f); quadTo(14.4f, 9.4f, 15.4f, 10.6f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(8.8f, 12.2f); lineTo(15.2f, 12.2f) // open mouth
            quadTo(14.8f, 15.2f, 12f, 15.2f)
            quadTo(9.2f, 15.2f, 8.8f, 12.2f); close()
        }
    }.build()
}

/** Icon for each KLIPY kind (bottom-bar tabs, Studio chips, search bar). */
val KlipyApi.Kind.icon: ImageVector
    get() = when (this) {
        KlipyApi.Kind.GIFS -> GifIcon
        KlipyApi.Kind.STICKERS -> StickerIcon
        KlipyApi.Kind.MEMES -> MemeIcon
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

// ------------------------------------------------------------------ GIFs, stickers, memes (KLIPY)

/** Last tab used, remembered while the keyboard process lives. */
internal var lastKind: KlipyApi.Kind = KlipyApi.Kind.GIFS

/** "Recent" is a pseudo-category: items the user sent before, stored on the phone. */
private const val RECENT = "\u0000recent"

/** Shared loading logic: Recent, Trending (query null) or a search. */
@Composable
private fun rememberMediaItems(
    kind: KlipyApi.Kind,
    query: String?,
    recent: Boolean,
    debounceMs: Long,
    retry: Int,
): Pair<List<KlipyApi.Media>?, String?> {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<KlipyApi.Media>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(kind, query, recent, retry) {
        if (recent) {
            error = null
            items = KlipyApi.Recents.of(context, kind)
            return@LaunchedEffect
        }
        if (!KlipyApi.isConfigured) {
            error = "GIFs and stickers aren't set up in this build."
            return@LaunchedEffect
        }
        if (debounceMs > 0) delay(debounceMs) // wait until the user pauses typing
        items = null
        error = null
        try {
            items = if (query.isNullOrBlank()) KlipyApi.trending(context, kind).items
            else KlipyApi.search(context, kind, query).items
        } catch (e: Exception) {
            error = "Couldn't load ${kind.label}. Check your connection."
        }
    }
    return items to error
}

/** Sends [media] and turns the outcome into a short message (null = sent). */
private suspend fun sendAndDescribe(
    context: android.content.Context,
    kind: KlipyApi.Kind,
    media: KlipyApi.Media,
    query: String?,
    send: suspend (KlipyApi.Media, String?) -> StickerOutcome,
): String? = when (send(media, query)) {
    StickerOutcome.SENT -> {
        KlipyApi.Recents.add(context, media)
        null
    }
    StickerOutcome.NOT_SUPPORTED -> "This app doesn't accept ${kind.label} here."
    StickerOutcome.FAILED -> "Couldn't send. Try again."
}

/**
 * KLIPY media of one [kind]: search pill + Recent / Trending / category chips + grid.
 * Shown inside the emoji panel's GIF / Sticker / Meme tabs. Online only while open.
 */
@Composable
fun MediaContent(
    controller: KeyboardController,
    kind: KlipyApi.Kind,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    height: Dp,
    canSend: Boolean,
    onSend: (suspend (KlipyApi.Media, String?) -> StickerOutcome)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<KlipyApi.Category>>(emptyList()) }
    // The active query comes from the search box or a category chip (both live in the controller).
    val query = controller.submittedSearch
    var showRecent by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var sendingKey by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(kind) {
        lastKind = kind
        categories = emptyList()
        if (KlipyApi.isConfigured) categories = runCatching { KlipyApi.categories(kind) }.getOrDefault(emptyList())
    }
    LaunchedEffect(query) { if (query != null) showRecent = false }
    val (items, error) = rememberMediaItems(kind, query, showRecent, debounceMs = 0, retry = retry)

    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                SearchPill(query, kind, theme) {
                    feedback.press()
                    controller.startSearch()
                }
            }
            item {
                CategoryChip("Recent", showRecent, theme) {
                    controller.clearSearch()
                    showRecent = true
                }
            }
            item {
                CategoryChip("Trending", query == null && !showRecent, theme) {
                    controller.clearSearch()
                    showRecent = false
                }
            }
            items(categories, key = { it.query }) { c ->
                CategoryChip(c.name, query == c.query, theme) { controller.submitSearch(c.query) }
            }
        }

        MediaGrid(
            kind = kind,
            items = items,
            error = error,
            emptyText = when {
                showRecent -> "${kind.label} you send will show here."
                query != null -> "No ${kind.label} for \"$query\"."
                else -> "Nothing here yet."
            },
            theme = theme,
            sendingKey = sendingKey,
            onRetry = { retry++ },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { media ->
            val send = onSend ?: run {
                message = "Open the keyboard in a chat to send."
                return@MediaGrid
            }
            if (sendingKey != null) return@MediaGrid
            feedback.press()
            sendingKey = media.cacheKey
            scope.launch {
                message = sendAndDescribe(context, kind, media, query, send)
                sendingKey = null
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message ?: if (!canSend && onSend != null) "This text box may not accept images." else "",
                color = theme.toolbarText.copy(alpha = 0.8f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Attribution required by the KLIPY API terms.
            Text("Powered by KLIPY", color = theme.toolbarText.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Grid of KLIPY items with loading / error / empty states. */
@Composable
private fun MediaGrid(
    kind: KlipyApi.Kind,
    items: List<KlipyApi.Media>?,
    error: String?,
    emptyText: String,
    theme: KeyboardTheme,
    sendingKey: String?,
    onRetry: () -> Unit,
    modifier: Modifier,
    minCell: Dp = if (kind.isWide) 100.dp else 72.dp,
    onClick: (KlipyApi.Media) -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, color = theme.toolbarText, fontSize = 13.sp, textAlign = TextAlign.Center)
                if (KlipyApi.isConfigured) {
                    Text(
                        "Try again",
                        color = theme.accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable(onClick = onRetry),
                    )
                }
            }
            items == null -> CircularProgressIndicator(color = theme.accent, modifier = Modifier.size(28.dp))
            items.isEmpty() -> Text(emptyText, color = theme.toolbarText, fontSize = 13.sp, textAlign = TextAlign.Center)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minCell),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items, key = { it.cacheKey }) { media ->
                    MediaCell(media, theme, sending = sendingKey == media.cacheKey) { onClick(media) }
                }
            }
        }
    }
}

@Composable
private fun MediaCell(media: KlipyApi.Media, theme: KeyboardTheme, sending: Boolean, onClick: () -> Unit) {
    val kind = media.kind
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(theme.key.copy(alpha = 0.35f))
            .clickable(onClickLabel = "Send ${media.title}", onClick = onClick),
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
                .padding(if (kind.cropToFill) 0.dp else 4.dp),
            fit = !kind.cropToFill,
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
        if (sending) CircularProgressIndicator(color = theme.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
    }
}

/** The tappable search field at the start of the chip row; shows the active search, if any. */
@Composable
private fun SearchPill(query: String?, kind: KlipyApi.Kind, theme: KeyboardTheme, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (query != null) theme.accent.copy(alpha = 0.2f) else theme.key)
            .clickable(onClickLabel = "Search ${kind.label}", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = if (query != null) theme.accent else theme.text, modifier = Modifier.size(16.dp))
        Text(
            query ?: "Search",
            color = if (query != null) theme.accent else theme.text,
            fontSize = 13.sp,
            fontWeight = if (query != null) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 4.dp)
                .widthIn(max = 140.dp),
        )
    }
}

/**
 * Search mode (shown above the normal letter keys): search box, quick chips, and results that
 * update live as the user types. Tapping a result sends it straight away.
 */
@Composable
fun MediaSearchPanel(
    controller: KeyboardController,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    barHeight: Dp,
    resultsHeight: Dp,
    onSend: (suspend (KlipyApi.Media, String?) -> StickerOutcome)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(lastKind) }
    val typed = controller.searchQuery.trim()
    var categories by remember { mutableStateOf<List<KlipyApi.Category>>(emptyList()) }
    var showRecent by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var sendingKey by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(kind) {
        if (KlipyApi.isConfigured) categories = runCatching { KlipyApi.categories(kind) }.getOrDefault(emptyList())
    }
    LaunchedEffect(typed) { if (typed.isNotEmpty()) showRecent = false }
    val (items, error) = rememberMediaItems(
        kind, typed.ifEmpty { null }, showRecent, debounceMs = if (typed.isEmpty()) 0 else 400, retry = retry,
    )

    MediaSearchBar(controller, theme, feedback, barHeight, kind)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // GIF / Sticker / Meme switch, like Gboard: same words, other kind of result.
        items(KlipyApi.Kind.entries.toList(), key = { "kind_" + it.name }) { k ->
            KindChip(k, selected = k == kind, theme = theme) {
                if (k != kind) {
                    feedback.press()
                    kind = k
                    lastKind = k
                    // Back in the emoji panel, open the tab of the kind you were last searching.
                    EmojiTab.entries.firstOrNull { it.kind == k }?.let { lastTab = it }
                    categories = emptyList()
                }
            }
        }
        item { CategoryChip("Recent", showRecent, theme) { showRecent = true; controller.setSearchText("") } }
        item { CategoryChip("Trending", typed.isEmpty() && !showRecent, theme) { showRecent = false; controller.setSearchText("") } }
        items(categories, key = { it.query }) { c ->
            CategoryChip(c.name, typed.equals(c.query, ignoreCase = true), theme) {
                showRecent = false
                controller.setSearchText(c.query)
            }
        }
    }

    MediaGrid(
        kind = kind,
        items = items,
        error = error,
        emptyText = when {
            showRecent -> "${kind.label} you send will show here."
            typed.isNotEmpty() -> "No ${kind.label} for \"$typed\"."
            else -> "Nothing here yet."
        },
        theme = theme,
        sendingKey = sendingKey,
        onRetry = { retry++ },
        minCell = if (kind.isWide) 110.dp else 76.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(resultsHeight),
    ) { media ->
        val send = onSend ?: run {
            message = "Open the keyboard in a chat to send."
            return@MediaGrid
        }
        if (sendingKey != null) return@MediaGrid
        feedback.press()
        sendingKey = media.cacheKey
        scope.launch {
            message = sendAndDescribe(context, kind, media, typed.ifEmpty { null }, send)
            sendingKey = null
        }
    }
    if (message != null) {
        Text(
            message!!,
            color = theme.toolbarText.copy(alpha = 0.8f),
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/**
 * Search box: back, field with "Search KLIPY …" placeholder (KLIPY's attribution guidance for search
 * boxes) and a blinking cursor, clear. Letters typed on the keys below go here, never into the chat.
 */
@Composable
fun MediaSearchBar(
    controller: KeyboardController,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    height: Dp,
    kind: KlipyApi.Kind = lastKind,
) {
    val blink = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClickLabel = "Back") {
                    feedback.press()
                    controller.cancelSearch()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = theme.toolbarText, modifier = Modifier.size(20.dp))
        }
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(theme.key)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(kind.icon, null, tint = theme.hint, modifier = Modifier.size(18.dp))
            Box(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                val q = controller.searchQuery
                if (q.isEmpty()) {
                    Text("Search KLIPY ${kind.label}", color = theme.hint, fontSize = 15.sp, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        q,
                        color = theme.text,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Box(
                        Modifier
                            .padding(start = 1.dp)
                            .size(width = 2.dp, height = 18.dp)
                            .background(theme.accent.copy(alpha = cursorAlpha)),
                    )
                }
            }
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClickLabel = "Clear") {
                    feedback.press()
                    controller.setSearchText("")
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, "Clear", tint = theme.toolbarText, modifier = Modifier.size(20.dp))
        }
    }
}

/** Icon-only chip that switches search between GIFs, stickers and memes. */
@Composable
private fun KindChip(kind: KlipyApi.Kind, selected: Boolean, theme: KeyboardTheme, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 40.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) theme.accent else theme.key)
            .clickable(onClickLabel = kind.label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            kind.icon,
            contentDescription = kind.label,
            tint = if (selected) theme.onAccent else theme.text,
            modifier = Modifier.size(20.dp),
        )
    }
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
