package dev.valhalla.sinhalax.keyboard

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valhalla.sinhalax.store.EmojiImages
import dev.valhalla.sinhalax.store.EmojiStyle
import dev.valhalla.sinhalax.store.KlipyApi
import dev.valhalla.sinhalax.store.LocalEmojiStyle
import kotlinx.coroutines.launch

/** Recently used emoji and kaomoji, newest first. Kept on the phone only. */
object EmojiRecents {
    private const val MAX = 48
    private const val PREFS = "emoji_recents"
    private const val SEP = "\u001E"
    private var cache: SnapshotStateList<String>? = null

    fun list(context: Context): SnapshotStateList<String> = cache ?: mutableStateListOf<String>().also { list ->
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", null)
        raw?.split(SEP)?.filter { it.isNotEmpty() }?.let { list.addAll(it) }
        cache = list
    }

    fun add(context: Context, item: String) {
        val list = list(context)
        list.remove(item)
        list.add(0, item)
        while (list.size > MAX) list.removeAt(list.lastIndex)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("items", list.joinToString(SEP)).apply()
    }
}

/** Tabs of the emoji panel, in bottom-bar order. */
enum class EmojiTab(val label: String, val kind: KlipyApi.Kind? = null) {
    RECENT("Recent"),
    EMOJI("Emoji"),
    KAOMOJI("(◕‿◕)"),
    GIFS("GIFs", KlipyApi.Kind.GIFS),
    STICKERS("Stickers", KlipyApi.Kind.STICKERS),
    MEMES("Memes", KlipyApi.Kind.MEMES),
}

/** Remembered while the keyboard process lives, so the panel reopens where you left it. */
internal var lastTab by mutableStateOf<EmojiTab?>(null)

/** True while the open tab is a KLIPY one (GIFs, stickers, memes): lights up the toolbar's GIF button. */
internal val klipyTabShowing: Boolean get() = lastTab?.kind != null

/**
 * The toolbar has separate 😊 and GIF buttons that open the same panel on different tabs, so people
 * can see GIFs are there. Picks the tab the button should open (keeping the last one of that group).
 */
internal fun emojiHubTabFor(gifs: Boolean): EmojiTab {
    val last = lastTab
    return if (gifs) {
        if (last?.kind != null) last else EmojiTab.GIFS
    } else {
        if (last != null && last.kind == null) last else EmojiTab.EMOJI
    }
}

/**
 * One panel for everything you can send that isn't letters: recent, emoji (by category), kaomoji,
 * and KLIPY GIFs, stickers and memes. Bottom bar: ABC · search · tabs · delete.
 */
@Composable
fun EmojiHub(
    controller: KeyboardController,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    height: Dp,
    canSendMedia: Boolean,
    onSendMedia: (suspend (KlipyApi.Media, String?) -> StickerOutcome)?,
) {
    val context = LocalContext.current
    val recents = remember { EmojiRecents.list(context) }
    var tab by remember {
        mutableStateOf(lastTab ?: if (recents.isEmpty()) EmojiTab.EMOJI else EmojiTab.RECENT)
    }
    LaunchedEffect(tab) {
        lastTab = tab
        tab.kind?.let { lastKind = it }
    }
    // The toolbar's 😊 / GIF buttons switch tabs while the panel is already open.
    LaunchedEffect(lastTab) {
        lastTab?.let { if (it != tab) tab = it }
    }

    fun insert(text: String) {
        feedback.press()
        controller.insertText(text)
        if (!controller.incognito) EmojiRecents.add(context, text)
    }

    val barHeight = 44.dp
    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (tab) {
                EmojiTab.RECENT -> if (recents.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Emoji you use will show here.",
                            color = theme.toolbarText.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    SymbolGrid(recents.toList(), theme, onPick = ::insert)
                }
                EmojiTab.EMOJI -> CategorizedEmoji(theme, onPick = ::insert)
                EmojiTab.KAOMOJI -> SymbolGrid(EmojiData.kaomoji, theme, wide = true, onPick = ::insert)
                else -> MediaContent(controller, tab.kind!!, theme, feedback, height - barHeight, canSendMedia, onSendMedia)
            }
        }

        // ------------------------------------------------------------------ bottom bar
        Row(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(theme, onClick = {
                feedback.press()
                controller.showKeys()
            }) { Text("ABC", color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }

            Spacer(Modifier.width(6.dp)) // gap between ABC and 🔍

            BarButton(theme, onClick = {
                feedback.press()
                // Searching works on KLIPY content; from the emoji tabs it searches GIFs.
                if (tab.kind == null) tab = EmojiTab.GIFS
                lastTab = tab // the panel leaves the screen while typing, so remember now
                lastKind = tab.kind ?: KlipyApi.Kind.GIFS
                controller.startSearch()
            }) { Icon(Icons.Outlined.Search, "Search GIFs, stickers and memes", tint = theme.text, modifier = Modifier.size(22.dp)) }

            // All six tabs share the space evenly, so none is cut off under ⌫ (no scrolling).
            Row(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmojiTab.entries.forEach { t ->
                    TabButton(t, selected = tab == t, theme = theme, modifier = Modifier.weight(1f)) {
                        feedback.press()
                        tab = t
                    }
                }
            }

            BarButton(theme, onClick = {
                feedback.press(special = true)
                controller.onAction(KeyAction.Delete)
            }) { Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = theme.text, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun BarButton(theme: KeyboardTheme, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(width = 46.dp, height = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(theme.specialKey)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun TabButton(
    tab: EmojiTab,
    selected: Boolean,
    theme: KeyboardTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (selected) theme.accent else theme.toolbarText.copy(alpha = 0.85f)
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) theme.accent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClickLabel = tab.label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val icon: ImageVector? = when (tab) {
            EmojiTab.RECENT -> Icons.Outlined.History
            EmojiTab.EMOJI -> Icons.Outlined.EmojiEmotions
            EmojiTab.KAOMOJI -> null
            else -> tab.kind?.icon
        }
        if (icon != null) {
            Icon(icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(22.dp))
        } else {
            Text(
                tab.label,
                color = tint,
                fontSize = if (tab == EmojiTab.KAOMOJI) 10.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Emoji grouped by category, with a category strip on top that jumps to each group. */
@Composable
private fun CategorizedEmoji(theme: KeyboardTheme, onPick: (String) -> Unit) {
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Grid index where each category header sits (one header item + its emoji).
    val headerIndex = remember {
        var i = 0
        EmojiData.categories.map { c -> i.also { i += 1 + c.emojis.size } }
    }
    val current = headerIndex.indexOfLast { it <= state.firstVisibleItemIndex }.coerceAtLeast(0)

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(EmojiData.categories.indices.toList()) { i ->
                val c = EmojiData.categories[i]
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (i == current) theme.accent.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable(onClickLabel = c.name) { scope.launch { state.scrollToItem(headerIndex[i]) } },
                    contentAlignment = Alignment.Center,
                ) { EmojiGlyph(c.icon, size = 22.dp, fontSize = 18.sp) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(44.dp),
            state = state,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            EmojiData.categories.forEach { c ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_${c.name}") {
                    Text(
                        c.name,
                        color = theme.toolbarText.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
                    )
                }
                items(c.emojis, key = { "${c.name}_$it" }) { e -> EmojiCell(e, onPick) }
            }
        }
    }
}

@Composable
private fun SymbolGrid(items: List<String>, theme: KeyboardTheme, wide: Boolean = false, onPick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 118.dp else 44.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
    ) {
        items(items, key = { it }) { item ->
            if (wide || item.length > 8) {
                Box(
                    Modifier
                        .height(46.dp)
                        .clickable { onPick(item) },
                    contentAlignment = Alignment.Center,
                ) { Text(item, color = theme.text, fontSize = 14.sp, maxLines = 1) }
            } else {
                EmojiCell(item, onPick)
            }
        }
    }
}

@Composable
private fun EmojiCell(emoji: String, onPick: (String) -> Unit) {
    Box(
        Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onPick(emoji) },
        contentAlignment = Alignment.Center,
    ) { EmojiGlyph(emoji, size = 30.dp, fontSize = 26.sp) }
}

/**
 * One emoji in the chosen Emoji style (Tools → Emoji style). The phone's own emoji shows while
 * the image loads, when offline, or with "Phone default".
 */
@Composable
fun EmojiGlyph(emoji: String, size: Dp, fontSize: TextUnit) {
    val style = LocalEmojiStyle.current
    if (style == EmojiStyle.SYSTEM) {
        Text(emoji, fontSize = fontSize)
        return
    }
    val context = LocalContext.current
    val image by produceState(EmojiImages.cached(style, emoji), style, emoji) {
        if (value == null) value = EmojiImages.load(context.applicationContext, style, emoji)
    }
    val bitmap = image
    if (bitmap != null) {
        Image(bitmap, contentDescription = emoji, modifier = Modifier.size(size), filterQuality = FilterQuality.Medium)
    } else {
        Text(emoji, fontSize = fontSize)
    }
}
