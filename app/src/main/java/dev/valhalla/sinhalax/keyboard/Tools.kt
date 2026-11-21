package dev.valhalla.sinhalax.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valhalla.sinhalax.data.KeyboardSettings
import dev.valhalla.sinhalax.engine.Calculator
import dev.valhalla.sinhalax.engine.SecretText
import dev.valhalla.sinhalax.engine.TextRepeater
import dev.valhalla.sinhalax.store.EmojiImages
import dev.valhalla.sinhalax.store.EmojiPacks
import dev.valhalla.sinhalax.store.EmojiStyle
import dev.valhalla.sinhalax.store.LocalEmojiStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

// ================================================================== tool ids

/** Everything that can live on the toolbar or in the Tools hub. */
enum class ToolId(val label: String) {
    LANGUAGE("Language"),
    FANCY("Fancy text"),
    EMOJI("Emoji"),
    GIF("GIFs"),
    CLIPBOARD("Clipboard"),
    TRANSLATE("Translate"),
    VOICE("Voice"),
    SETTINGS("Tools & settings"),
    TEXT_EDIT("Text edit"),
    ONE_HAND("One-hand"),
    NUMPAD("Number pad"),
    CALCULATOR("Calculator"),
    REPEAT("Repeat text"),
    SHORTCUTS("Shortcuts"),
    INCOGNITO("Incognito"),
    THEMES("Themes"),
    FM("FM convert"),
    // Phase 2 (v0.8)
    UNDO("Undo"),
    SECRET("Secret text"),
    PHRASES("Phrase studio"),
    // Phase 3 (v0.9)
    EMOJI_STYLE("Emoji style"),
    FLOATING("Floating"),
    ;

    companion object {
        const val MAX_TOOLBAR = 8

        val DEFAULT_TOOLBAR = listOf(LANGUAGE, FANCY, EMOJI, GIF, CLIPBOARD, TRANSLATE, VOICE, SETTINGS)

        /** Saved toolbar ("LANGUAGE,FANCY,…") → list; "" or nothing valid = the default set. */
        fun parseToolbar(raw: String): List<ToolId> {
            if (raw.isBlank()) return DEFAULT_TOOLBAR
            val list = raw.split(',').mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }
                .distinct().take(MAX_TOOLBAR)
            return list.ifEmpty { DEFAULT_TOOLBAR }
        }

        fun encodeToolbar(list: List<ToolId>): String =
            if (list == DEFAULT_TOOLBAR) "" else list.joinToString(",") { it.name }
    }
}

/** Screens of the app that tools can open (MainActivity reads these). */
object AppRoutes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val THEMES = "themes"
    const val STUDIO = "studio"
    const val STUDIO_FM = "studio_fm"
    const val SHORTCUTS = "shortcuts"
    const val PRIVACY = "privacy"
    const val GUIDE = "guide"
}

/** What a tool needs to run: the controller, settings and ways to change them or open the app. */
class ToolContext(
    val controller: KeyboardController,
    val settings: KeyboardSettings,
    val updateSettings: ((KeyboardSettings) -> KeyboardSettings) -> Unit,
    val openApp: (String) -> Unit,
)

/** Icon for a tool, or null when it shows text instead (Aa, FM). */
fun ToolId.icon(language: Language): ImageVector? = when (this) {
    ToolId.LANGUAGE -> LanguageIcons.forLanguage(language)
    ToolId.FANCY, ToolId.FM -> null
    ToolId.EMOJI -> Icons.Outlined.EmojiEmotions
    ToolId.GIF -> GifIcon
    ToolId.CLIPBOARD -> Icons.Outlined.ContentPaste
    ToolId.TRANSLATE -> Icons.Outlined.Translate
    ToolId.VOICE -> Icons.Outlined.Mic
    ToolId.SETTINGS -> Icons.Outlined.Settings
    ToolId.TEXT_EDIT -> Icons.Outlined.OpenWith
    ToolId.ONE_HAND -> Icons.Outlined.PanTool
    ToolId.NUMPAD -> Icons.Outlined.Dialpad
    ToolId.CALCULATOR -> Icons.Outlined.Calculate
    ToolId.REPEAT -> Icons.Outlined.Repeat
    ToolId.SHORTCUTS -> Icons.Outlined.Bolt
    ToolId.INCOGNITO -> Icons.Outlined.VisibilityOff
    ToolId.THEMES -> Icons.Outlined.Palette
    ToolId.UNDO -> Icons.AutoMirrored.Outlined.Undo
    ToolId.SECRET -> Icons.Outlined.Lock
    ToolId.PHRASES -> Icons.Outlined.FormatQuote
    ToolId.EMOJI_STYLE -> Icons.Outlined.EmojiSymbols
    ToolId.FLOATING -> Icons.Outlined.PictureInPictureAlt
}

/** Text shown instead of an icon. */
fun ToolId.textIcon(): String = when (this) {
    ToolId.FANCY -> "Aa"
    ToolId.FM -> "FM"
    else -> ""
}

/** Toggles that show an ON badge in the hub. */
private val toggleTools = setOf(ToolId.ONE_HAND, ToolId.INCOGNITO, ToolId.SHORTCUTS, ToolId.FLOATING)

/** Lit up on the toolbar / green in the hub. */
fun ToolContext.isToolActive(id: ToolId): Boolean {
    val c = controller
    val hubOpen = c.panel == Panel.EMOJI
    return when (id) {
        ToolId.LANGUAGE, ToolId.THEMES, ToolId.FM, ToolId.UNDO -> false
        ToolId.SECRET -> c.panel == Panel.SECRET
        ToolId.PHRASES -> c.panel == Panel.PHRASES
        ToolId.EMOJI_STYLE -> c.panel == Panel.EMOJI_STYLE
        ToolId.FLOATING -> settings.floating
        ToolId.SETTINGS -> c.panel == Panel.TOOLS
        ToolId.FANCY -> c.panel == Panel.FANCY || c.fancyStyle != null
        ToolId.EMOJI -> hubOpen && !klipyTabShowing
        ToolId.GIF -> hubOpen && klipyTabShowing
        ToolId.CLIPBOARD -> c.panel == Panel.CLIPBOARD
        ToolId.TRANSLATE -> c.translateActive
        ToolId.VOICE -> c.panel == Panel.VOICE
        ToolId.TEXT_EDIT -> c.panel == Panel.TEXT_EDIT
        ToolId.NUMPAD -> c.panel == Panel.NUMPAD
        ToolId.CALCULATOR -> c.panel == Panel.CALCULATOR
        ToolId.REPEAT -> c.panel == Panel.REPEAT
        ToolId.ONE_HAND -> settings.oneHand.isNotEmpty()
        ToolId.INCOGNITO -> settings.incognito
        ToolId.SHORTCUTS -> settings.shortcutsEnabled
    }
}

/** Runs a tool (toolbar button or hub tile). */
fun ToolContext.runTool(id: ToolId) {
    val c = controller
    fun panelTool(p: Panel) = if (c.panel == p) c.showKeys() else c.openPanel(p)
    when (id) {
        ToolId.LANGUAGE -> c.toggleLanguage()
        ToolId.FANCY -> c.togglePanel(Panel.FANCY)
        ToolId.EMOJI -> openEmojiHub(c, gifs = false)
        ToolId.GIF -> openEmojiHub(c, gifs = true)
        ToolId.CLIPBOARD -> c.togglePanel(Panel.CLIPBOARD)
        ToolId.TRANSLATE -> if (c.translateActive) c.stopTranslate() else {
            c.startTranslate()
            if (c.panel != Panel.KEYS && c.panel != Panel.VOICE) c.showKeys()
        }
        ToolId.VOICE -> c.togglePanel(Panel.VOICE)
        // ⚙ opens the Tools hub (full app settings are one tap further: the gear in the hub).
        ToolId.SETTINGS -> if (c.panel == Panel.TOOLS) c.showKeys() else c.openPanel(Panel.TOOLS)
        ToolId.TEXT_EDIT -> panelTool(Panel.TEXT_EDIT)
        ToolId.NUMPAD -> panelTool(Panel.NUMPAD)
        ToolId.CALCULATOR -> panelTool(Panel.CALCULATOR)
        ToolId.REPEAT -> panelTool(Panel.REPEAT)
        ToolId.ONE_HAND -> {
            updateSettings { it.copy(oneHand = if (it.oneHand.isEmpty()) "right" else "") }
            c.showKeys() // show the effect straight away
        }
        ToolId.INCOGNITO -> updateSettings { it.copy(incognito = !it.incognito) }
        ToolId.SHORTCUTS -> openApp(AppRoutes.SHORTCUTS)
        ToolId.THEMES -> openApp(AppRoutes.THEMES)
        ToolId.FM -> openApp(AppRoutes.STUDIO_FM)
        // Word-by-word undo; when there's nothing SinhalaX can take back, the app's own undo.
        ToolId.UNDO -> if (!c.undo()) c.toolConnection()?.let(TextEditing::undo)
        ToolId.SECRET -> panelTool(Panel.SECRET)
        ToolId.PHRASES -> panelTool(Panel.PHRASES)
        ToolId.EMOJI_STYLE -> panelTool(Panel.EMOJI_STYLE)
        ToolId.FLOATING -> {
            updateSettings { it.copy(floating = !it.floating) }
            c.showKeys() // show the effect straight away
        }
    }
}

// ================================================================== Tools hub

private enum class HubTab(val label: String) { TOOLS("Tools"), LOOK("Look & feel"), APP("App") }

private var lastHubTab by mutableStateOf(HubTab.TOOLS)

/** One tile in the hub: a tool, or a plain action (Look & feel / App tabs). */
private data class HubTile(
    val key: String,
    val label: String,
    val icon: ImageVector?,
    val text: String = "",
    val active: Boolean = false,
    val badge: String? = null,
    val pinned: Boolean = false,
    val tool: ToolId? = null,
    val onClick: () -> Unit,
)

/**
 * The SinhalaX Tools hub (tap ⚙ or "SinhalaX" on the toolbar). Three tabs, plus a gear that opens
 * the app's full Settings:
 *  - Tools: everything grouped (Typing · Write & share · Privacy). Green + ON = switched on.
 *    Long-press a tool to pin it to (or remove it from) the toolbar.
 *  - Look & feel: your toolbar (remove / move / reset), quick switches, Themes.
 *  - App: open parts of the SinhalaX app.
 */
@Composable
fun ToolsHub(ctx: ToolContext, theme: KeyboardTheme, feedback: KeyFeedback, height: Dp) {
    var tab by remember { mutableStateOf(lastHubTab) }
    LaunchedEffect(tab) { lastHubTab = tab }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2500.milliseconds)
            message = null
        }
    }
    val toolbar = ToolId.parseToolbar(ctx.settings.toolbarItems)
    val language = ctx.controller.language

    fun togglePin(id: ToolId) {
        feedback.press(special = true)
        val current = ToolId.parseToolbar(ctx.settings.toolbarItems)
        val next = when {
            id in current -> current - id
            current.size >= ToolId.MAX_TOOLBAR -> {
                message = "Toolbar is full. Remove one in Look & feel."
                return
            }
            else -> current + id
        }
        message = if (id in current) "Removed ${id.label} from the toolbar" else "Pinned ${id.label} to the toolbar"
        ctx.updateSettings { it.copy(toolbarItems = ToolId.encodeToolbar(next)) }
    }

    fun toolTile(id: ToolId) = HubTile(
        key = id.name,
        label = id.label,
        icon = id.icon(language),
        text = id.textIcon(),
        active = ctx.isToolActive(id),
        badge = if (id in toggleTools && ctx.isToolActive(id)) "ON" else null,
        pinned = id in toolbar,
        tool = id,
    ) {
        feedback.press()
        ctx.runTool(id)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        // ---- tabs · ⚙ all settings
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.key.copy(alpha = 0.6f))
                    .padding(3.dp),
            ) {
                HubTab.entries.forEach { t ->
                    val selected = t == tab
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) theme.accent else theme.key.copy(alpha = 0f))
                            .clickable {
                                feedback.press()
                                tab = t
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            t.label,
                            color = if (selected) theme.onAccent else theme.toolbarText,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "All settings") {
                        feedback.press()
                        ctx.openApp(AppRoutes.SETTINGS)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Settings, "All settings", tint = theme.toolbarText, modifier = Modifier.size(22.dp))
            }
        }

        // ---- content
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (tab) {
                HubTab.TOOLS -> TileGrid(
                    theme,
                    sections = listOf(
                        "Typing" to listOf(
                            ToolId.TEXT_EDIT, ToolId.UNDO, ToolId.FLOATING, ToolId.ONE_HAND, ToolId.NUMPAD,
                            ToolId.SHORTCUTS, ToolId.CALCULATOR, ToolId.REPEAT, ToolId.FANCY, ToolId.FM,
                        ).map(::toolTile),
                        "Write & share" to listOf(
                            ToolId.PHRASES, ToolId.TRANSLATE, ToolId.VOICE, ToolId.EMOJI, ToolId.GIF, ToolId.CLIPBOARD,
                        ).map(::toolTile),
                        "Privacy" to listOf(ToolId.SECRET, ToolId.INCOGNITO).map(::toolTile),
                    ),
                    onLongPress = { tile -> tile.tool?.let(::togglePin) },
                )
                HubTab.LOOK -> Column(Modifier.fillMaxSize()) {
                    ToolbarEditor(ctx, theme, feedback, toolbar, language)
                    TileGrid(
                        theme,
                        sections = listOf(
                            "Quick switches" to listOf(
                                switchTile("sound", "Key sound", Icons.AutoMirrored.Outlined.VolumeUp, ctx.settings.sound) {
                                    feedback.press()
                                    ctx.updateSettings { it.copy(sound = !it.sound) }
                                },
                                switchTile("vibrate", "Vibrate", Icons.Outlined.Vibration, ctx.settings.vibrate) {
                                    feedback.press()
                                    ctx.updateSettings { it.copy(vibrate = !it.vibrate) }
                                },
                                switchTile("numberRow", "Number row", Icons.Outlined.Dialpad, ctx.settings.numberRow) {
                                    feedback.press()
                                    ctx.updateSettings { it.copy(numberRow = !it.numberRow) }
                                },
                                switchTile("hints", "Letter hints", Icons.Outlined.Lightbulb, ctx.settings.showHints) {
                                    feedback.press()
                                    ctx.updateSettings { it.copy(showHints = !it.showHints) }
                                },
                                toolTile(ToolId.EMOJI_STYLE),
                                toolTile(ToolId.FLOATING),
                                toolTile(ToolId.ONE_HAND),
                                toolTile(ToolId.THEMES),
                            ),
                        ),
                        onLongPress = { tile -> tile.tool?.let(::togglePin) },
                    )
                }
                HubTab.APP -> TileGrid(
                    theme,
                    sections = listOf(
                        "Open in the app" to listOf(
                            appTile("home", "SinhalaX", Icons.Outlined.Home) { ctx.openApp(AppRoutes.HOME) },
                            appTile("settings", "Settings", Icons.Outlined.Settings) { ctx.openApp(AppRoutes.SETTINGS) },
                            appTile("themes", "Themes", Icons.Outlined.Palette) { ctx.openApp(AppRoutes.THEMES) },
                            appTile("studio", "Studio", Icons.Outlined.AutoAwesome) { ctx.openApp(AppRoutes.STUDIO) },
                            appTile("shortcuts", "Shortcuts", Icons.Outlined.Bolt) { ctx.openApp(AppRoutes.SHORTCUTS) },
                            appTile("privacy", "Privacy", Icons.Outlined.PrivacyTip) { ctx.openApp(AppRoutes.PRIVACY) },
                            appTile("guide", "Guide", Icons.AutoMirrored.Outlined.HelpOutline) { ctx.openApp(AppRoutes.GUIDE) },
                        ).map { t -> t.copy(onClick = { feedback.press(); t.onClick() }) },
                    ),
                    onLongPress = {},
                )
            }
        }

        // ---- bottom bar: ABC · hint / message · delete
        PanelBar(ctx.controller, theme, feedback) {
            Text(
                message ?: if (tab == HubTab.APP) "Opens the SinhalaX app" else "Long-press a tool to pin it to the toolbar",
                color = if (message != null) theme.accent else theme.toolbarText.copy(alpha = 0.65f),
                fontSize = 11.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun switchTile(key: String, label: String, icon: ImageVector, on: Boolean, onClick: () -> Unit) =
    HubTile(key = key, label = label, icon = icon, active = on, badge = if (on) "ON" else "OFF", onClick = onClick)

private fun appTile(key: String, label: String, icon: ImageVector, onClick: () -> Unit) =
    HubTile(key = key, label = label, icon = icon, onClick = onClick)

@Composable
private fun TileGrid(
    theme: KeyboardTheme,
    sections: List<Pair<String, List<HubTile>>>,
    onLongPress: (HubTile) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { (title, tiles) ->
            item(key = "h_$title", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    title.uppercase(),
                    color = theme.toolbarText.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
            items(tiles, key = { "t_${title}_${it.key}" }) { tile ->
                ToolTile(tile, theme, onLongPress = { onLongPress(tile) })
            }
        }
    }
}

@Composable
private fun ToolTile(tile: HubTile, theme: KeyboardTheme, onLongPress: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .height(64.dp)
            .clip(shape)
            .background(if (tile.active) theme.accent.copy(alpha = 0.18f) else theme.key)
            .then(if (tile.active) Modifier.border(1.5.dp, theme.accent.copy(alpha = 0.55f), shape) else Modifier)
            .combinedClickable(
                onClickLabel = tile.label,
                onLongClickLabel = if (tile.tool != null) "Pin to toolbar" else null,
                onLongClick = if (tile.tool != null) onLongPress else null,
                onClick = tile.onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (tile.active) theme.accent else theme.text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (tile.icon != null) {
                Icon(tile.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            } else {
                Text(
                    tile.text,
                    color = tint,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (tile.text == "Aa") FontFamily.Serif else FontFamily.Default,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                tile.label,
                color = tint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        val badge = tile.badge
        if (badge != null) {
            Text(
                badge,
                color = if (tile.active) theme.onAccent else theme.text,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (tile.active) theme.accent else theme.specialKey)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (tile.pinned) {
            Text(
                "📌",
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp),
            )
        }
    }
}

/**
 * "Your toolbar": the buttons in order. Tap one to move it left, ✕ to remove it, Reset for the
 * default set. New ones are added by long-pressing a tool.
 */
@Composable
private fun ToolbarEditor(
    ctx: ToolContext,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    toolbar: List<ToolId>,
    language: Language,
) {
    fun save(list: List<ToolId>) = ctx.updateSettings { it.copy(toolbarItems = ToolId.encodeToolbar(list)) }
    Column(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "YOUR TOOLBAR  ·  tap to move left",
                color = theme.toolbarText.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            if (toolbar != ToolId.DEFAULT_TOOLBAR) {
                Text(
                    "Reset",
                    color = theme.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            feedback.press()
                            save(ToolId.DEFAULT_TOOLBAR)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            toolbar.forEachIndexed { index, id ->
                Row(
                    Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(theme.key)
                        .clickable(onClickLabel = "Move ${id.label} left") {
                            if (index > 0) {
                                feedback.press()
                                save(toolbar.toMutableList().apply { add(index - 1, removeAt(index)) })
                            }
                        }
                        .padding(start = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = id.icon(language)
                    if (icon != null) {
                        Icon(icon, null, tint = theme.text, modifier = Modifier.size(16.dp))
                    } else {
                        Text(id.textIcon(), color = theme.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier
                            .padding(start = 4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "Remove ${id.label}") {
                                feedback.press()
                                save(toolbar - id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, "Remove ${id.label}", tint = theme.hint, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ================================================================== shared bars

/** ABC · [middle] · delete, used under every tool panel. */
@Composable
private fun PanelBar(
    controller: KeyboardController,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    middle: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BarKey(theme, "Keyboard", onClick = {
            feedback.press()
            controller.showKeys()
        }) { Text("ABC", color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, content = middle)
        }
        BarKey(theme, "Delete", onClick = {
            feedback.press(special = true)
            controller.onAction(KeyAction.Delete)
        }) { Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = theme.text, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun BarKey(theme: KeyboardTheme, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(width = 56.dp, height = 38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(theme.specialKey)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** "← Title" header for tool panels (back goes to the Tools hub). */
@Composable
private fun ToolHeader(controller: KeyboardController, theme: KeyboardTheme, feedback: KeyFeedback, title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "←",
            color = theme.toolbarText,
            fontSize = 20.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = "Back to tools") {
                    feedback.press()
                    controller.openPanel(Panel.TOOLS)
                }
                .padding(horizontal = 8.dp),
        )
        Text(title, color = theme.toolbarText, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** A flat key used by the tool panels. */
@Composable
private fun RowScope.PadKey(
    theme: KeyboardTheme,
    label: String,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    accent: Boolean = false,
    special: Boolean = false,
    fontSize: Int = 16,
    description: String = label,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    accent -> theme.accent
                    special -> theme.specialKey
                    else -> theme.key
                },
            )
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (accent) theme.onAccent else theme.text,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Rows of keys filling the space evenly. */
@Composable
private fun PadRows(modifier: Modifier, rows: List<@Composable RowScope.() -> Unit>) {
    Column(
        modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = row,
            )
        }
    }
}

// ================================================================== Text edit

/** Cursor and selection moves on the app's text, through the InputConnection. */
internal object TextEditing {

    private fun key(ic: InputConnection, code: Int, meta: Int) {
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }

    private val SHIFT = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
    private val CTRL = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON

    /** Arrow keys; with [select] they extend the selection (Shift + arrow). */
    fun arrow(ic: InputConnection, keyCode: Int, select: Boolean) = key(ic, keyCode, if (select) SHIFT else 0)

    /** Jump to the previous / next word (or select up to it). */
    fun word(ic: InputConnection, forward: Boolean, select: Boolean) {
        val et = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 100_000 }, 0)
        val text = et?.text
        if (et == null || text == null) {
            // No text access (some web views): fall back to Ctrl + arrow.
            key(ic, if (forward) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT, CTRL or (if (select) SHIFT else 0))
            return
        }
        val anchor = et.selectionStart
        val focus = et.selectionEnd
        var i = focus.coerceIn(0, text.length)
        if (forward) {
            while (i < text.length && !isWordChar(text[i])) i++
            while (i < text.length && isWordChar(text[i])) i++
        } else {
            i = previousWordStart(text, i)
        }
        val off = et.startOffset
        if (select) ic.setSelection(off + anchor, off + i) else ic.setSelection(off + i, off + i)
    }

    /** Start / end of the text (or select to there). */
    fun edge(ic: InputConnection, end: Boolean, select: Boolean) {
        val et = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 100_000 }, 0)
        val text = et?.text
        if (et == null || text == null) {
            key(ic, if (end) KeyEvent.KEYCODE_MOVE_END else KeyEvent.KEYCODE_MOVE_HOME, CTRL or (if (select) SHIFT else 0))
            return
        }
        val off = et.startOffset
        val target = if (end) off + text.length else off
        if (select) ic.setSelection(off + et.selectionStart, target) else ic.setSelection(target, target)
    }

    fun selectAll(ic: InputConnection) = ic.performContextMenuAction(android.R.id.selectAll)
    fun copy(ic: InputConnection) = ic.performContextMenuAction(android.R.id.copy)
    fun cut(ic: InputConnection) = ic.performContextMenuAction(android.R.id.cut)
    fun paste(ic: InputConnection) = ic.performContextMenuAction(android.R.id.paste)

    /** Ctrl + Z / Ctrl + Shift + Z: the app's own undo / redo (most text boxes support it). */
    fun undo(ic: InputConnection) = key(ic, KeyEvent.KEYCODE_Z, CTRL)
    fun redo(ic: InputConnection) = key(ic, KeyEvent.KEYCODE_Z, CTRL or SHIFT)

    /** Start of the word before [from] (skipping spaces / punctuation first). */
    fun previousWordStart(text: CharSequence, from: Int): Int {
        var i = from.coerceIn(0, text.length)
        while (i > 0 && !isWordChar(text[i - 1])) i--
        while (i > 0 && isWordChar(text[i - 1])) i--
        return i
    }

    /**
     * ⌫ swipe: slide left to select whole words back from the cursor, let go to delete them
     * (like Gboard). Sliding back right selects fewer; ending on 0 words deletes nothing.
     */
    class DeleteSwipe(private val ic: InputConnection) {
        private val extracted = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 100_000 }, 0)
        private val text: CharSequence? = extracted?.text
        private val offset = extracted?.startOffset ?: 0
        private val anchor = extracted?.selectionEnd ?: 0
        private var start = anchor

        fun select(words: Int) {
            val t = text ?: return
            var i = anchor.coerceIn(0, t.length)
            repeat(words) { i = previousWordStart(t, i) }
            start = i
            ic.setSelection(offset + start, offset + anchor)
        }

        fun finish() {
            if (text != null && start < anchor) ic.commitText("", 1) else cancel()
        }

        fun cancel() {
            if (text != null) ic.setSelection(offset + anchor, offset + anchor)
        }
    }

    /** Letters, digits and the Sinhala vowel signs / virama / joiners all belong to a word. */
    private fun isWordChar(c: Char): Boolean {
        if (c.isLetterOrDigit() || c == '‍' || c == '‌' || c == '_') return true
        val type = Character.getType(c)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()
    }
}

/**
 * Text edit: arrows (▲▼◀▶), word jumps, start / end, Select mode (arrows extend the selection),
 * select all, copy, cut, paste, undo and redo. Works on the app's text box.
 */
@Composable
fun TextEditPanel(controller: KeyboardController, theme: KeyboardTheme, feedback: KeyFeedback, height: Dp) {
    var select by remember { mutableStateOf(false) }
    fun withIc(special: Boolean = false, block: (InputConnection) -> Unit) {
        feedback.press(special)
        controller.toolConnection()?.let(block)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        ToolHeader(controller, theme, feedback, "Text edit") {
            Text(
                if (select) "Selecting…" else "",
                color = theme.accent,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        PadRows(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            listOf<@Composable RowScope.() -> Unit>(
                {
                    PadKey(theme, "⇤ Start", special = true, fontSize = 13) { withIc { TextEditing.edge(it, end = false, select) } }
                    PadKey(theme, "▲", description = "Up") { withIc { TextEditing.arrow(it, KeyEvent.KEYCODE_DPAD_UP, select) } }
                    PadKey(theme, "End ⇥", special = true, fontSize = 13) { withIc { TextEditing.edge(it, end = true, select) } }
                    PadKey(theme, if (select) "Select\nON" else "Select", accent = select, fontSize = 13) {
                        feedback.press()
                        select = !select
                    }
                },
                {
                    PadKey(theme, "◀", description = "Left") { withIc { TextEditing.arrow(it, KeyEvent.KEYCODE_DPAD_LEFT, select) } }
                    PadKey(theme, "▼", description = "Down") { withIc { TextEditing.arrow(it, KeyEvent.KEYCODE_DPAD_DOWN, select) } }
                    PadKey(theme, "▶", description = "Right") { withIc { TextEditing.arrow(it, KeyEvent.KEYCODE_DPAD_RIGHT, select) } }
                    PadKey(theme, "Select all", special = true, fontSize = 13) {
                        withIc { TextEditing.selectAll(it) }
                        select = false
                    }
                },
                {
                    PadKey(theme, "◀ word", special = true, fontSize = 13) { withIc { TextEditing.word(it, forward = false, select) } }
                    PadKey(theme, "word ▶", special = true, fontSize = 13) { withIc { TextEditing.word(it, forward = true, select) } }
                    PadKey(theme, "Copy", fontSize = 13) {
                        withIc { TextEditing.copy(it) }
                        select = false
                    }
                    PadKey(theme, "Cut", fontSize = 13) {
                        withIc { TextEditing.cut(it) }
                        select = false
                    }
                },
                {
                    PadKey(theme, "↶ Undo", special = true, fontSize = 13) {
                        feedback.press()
                        if (!controller.undo()) controller.toolConnection()?.let(TextEditing::undo)
                    }
                    PadKey(theme, "Redo ↷", special = true, fontSize = 13) {
                        feedback.press()
                        if (!controller.redo()) controller.toolConnection()?.let(TextEditing::redo)
                    }
                    PadKey(theme, "Paste", accent = true, fontSize = 13) {
                        withIc { TextEditing.paste(it) }
                        select = false
                    }
                },
            ),
        )
        PanelBar(controller, theme, feedback) {}
    }
}

// ================================================================== Number pad

/** Big number pad for OTPs, phone numbers and USSD codes (*#). Digits go in as they are. */
@Composable
fun NumberPadPanel(controller: KeyboardController, theme: KeyboardTheme, feedback: KeyFeedback, height: Dp) {
    fun put(s: String) {
        feedback.press()
        controller.insertRaw(s)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(vertical = 4.dp),
    ) {
        PadRows(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            listOf<@Composable RowScope.() -> Unit>(
                {
                    PadKey(theme, "+", special = true) { put("+") }
                    PadKey(theme, "1", fontSize = 22) { put("1") }
                    PadKey(theme, "2", fontSize = 22) { put("2") }
                    PadKey(theme, "3", fontSize = 22) { put("3") }
                    PadKey(theme, "⌫", special = true, description = "Delete") {
                        feedback.press(special = true)
                        controller.onAction(KeyAction.Delete)
                    }
                },
                {
                    PadKey(theme, "−", special = true, description = "Minus") { put("-") }
                    PadKey(theme, "4", fontSize = 22) { put("4") }
                    PadKey(theme, "5", fontSize = 22) { put("5") }
                    PadKey(theme, "6", fontSize = 22) { put("6") }
                    PadKey(theme, "␣", special = true, description = "Space") {
                        feedback.press()
                        controller.insertRaw(" ")
                    }
                },
                {
                    PadKey(theme, ",", special = true) { put(",") }
                    PadKey(theme, "7", fontSize = 22) { put("7") }
                    PadKey(theme, "8", fontSize = 22) { put("8") }
                    PadKey(theme, "9", fontSize = 22) { put("9") }
                    PadKey(theme, "↵", accent = true, description = "Enter") {
                        feedback.press(special = true)
                        controller.onAction(KeyAction.Enter)
                    }
                },
                {
                    PadKey(theme, "ABC", special = true, fontSize = 14, description = "Keyboard") {
                        feedback.press()
                        controller.showKeys()
                    }
                    PadKey(theme, "*", special = true) { put("*") }
                    PadKey(theme, "0", fontSize = 22) { put("0") }
                    PadKey(theme, "#", special = true) { put("#") }
                    PadKey(theme, ".", special = true) { put(".") }
                },
            ),
        )
    }
}

// ================================================================== Calculator

/**
 * Calculator: type a sum on the pad, see the answer live, then Insert puts the answer
 * (or the whole "250 × 4 = 1000") into the chat. Works offline.
 */
@Composable
fun CalculatorPanel(controller: KeyboardController, theme: KeyboardTheme, feedback: KeyFeedback, height: Dp) {
    var expr by remember { mutableStateOf(lastCalculation) }
    LaunchedEffect(expr) { lastCalculation = expr }
    val result = Calculator.result(expr)
    fun add(s: String) {
        feedback.press()
        if (expr.length < 80) expr += s
    }
    fun brackets() {
        val open = expr.count { it == '(' } - expr.count { it == ')' }
        val last = expr.lastOrNull()
        add(if (open > 0 && last != null && (last.isDigit() || last == ')' || last == '%')) ")" else "(")
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        // Display: ABC · sum = answer · Insert
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarKey(theme, "Keyboard", onClick = {
                feedback.press()
                controller.showKeys()
            }) { Text("ABC", color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    expr.ifEmpty { "0" },
                    color = theme.text,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                )
                Text(
                    if (result != null && expr.isNotEmpty()) "= $result" else " ",
                    color = theme.accent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Row(
                Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (result != null) theme.accent else theme.accent.copy(alpha = 0.35f))
                    .combinedClickable(
                        enabled = result != null,
                        onClickLabel = "Insert answer",
                        onLongClickLabel = "Insert the sum and answer",
                        onLongClick = {
                            feedback.press()
                            controller.insertRaw("$expr = $result")
                        },
                        onClick = {
                            feedback.press()
                            controller.insertRaw(result.orEmpty())
                        },
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardReturn, null, tint = theme.onAccent, modifier = Modifier.size(16.dp))
                Text(" Insert", color = theme.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        PadRows(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            listOf<@Composable RowScope.() -> Unit>(
                {
                    PadKey(theme, "7", fontSize = 20) { add("7") }
                    PadKey(theme, "8", fontSize = 20) { add("8") }
                    PadKey(theme, "9", fontSize = 20) { add("9") }
                    PadKey(theme, "÷", special = true, fontSize = 20, description = "Divide") { add("÷") }
                    PadKey(theme, "C", special = true, description = "Clear") {
                        feedback.press()
                        expr = ""
                    }
                },
                {
                    PadKey(theme, "4", fontSize = 20) { add("4") }
                    PadKey(theme, "5", fontSize = 20) { add("5") }
                    PadKey(theme, "6", fontSize = 20) { add("6") }
                    PadKey(theme, "×", special = true, fontSize = 20, description = "Times") { add("×") }
                    PadKey(theme, "⌫", special = true, description = "Delete") {
                        feedback.press(special = true)
                        expr = expr.dropLast(1)
                    }
                },
                {
                    PadKey(theme, "1", fontSize = 20) { add("1") }
                    PadKey(theme, "2", fontSize = 20) { add("2") }
                    PadKey(theme, "3", fontSize = 20) { add("3") }
                    PadKey(theme, "−", special = true, fontSize = 20, description = "Minus") { add("−") }
                    PadKey(theme, "( )", special = true, description = "Brackets") { brackets() }
                },
                {
                    PadKey(theme, "0", fontSize = 20) { add("0") }
                    PadKey(theme, ".", fontSize = 20) { add(".") }
                    PadKey(theme, "%", special = true) { add("%") }
                    PadKey(theme, "+", special = true, fontSize = 20, description = "Plus") { add("+") }
                    PadKey(theme, "=", accent = true, fontSize = 20, description = "Equals") {
                        feedback.press()
                        if (result != null) expr = result
                    }
                },
            ),
        )
    }
}

private var lastCalculation = ""

// ================================================================== Repeat text

/**
 * Repeat text: repeats the selected text (or the line you just typed) 2–100 times, joined by
 * a space, a new line or nothing, optionally numbered. Replaces the original in the app.
 */
@Composable
fun RepeatPanel(controller: KeyboardController, theme: KeyboardTheme, feedback: KeyFeedback, height: Dp) {
    var count by remember { mutableIntStateOf(repeatCount) }
    var joiner by remember { mutableStateOf(repeatJoiner) }
    var numbered by remember { mutableStateOf(repeatNumbered) }
    var refresh by remember { mutableIntStateOf(0) }
    var source by remember { mutableStateOf<RepeatSource?>(null) }
    LaunchedEffect(count, joiner, numbered) {
        repeatCount = count
        repeatJoiner = joiner
        repeatNumbered = numbered
    }
    // Read what to repeat after composing (it ends the current word, which changes state).
    LaunchedEffect(refresh) { source = readRepeatSource(controller) }

    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        ToolHeader(controller, theme, feedback, "Repeat text")
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // What will be repeated
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.key)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    if (source == null) "Type or select some text in the app first" else "“${source!!.text}”",
                    color = if (source == null) theme.hint else theme.text,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Count: − 10 +, with quick picks
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Times", color = theme.toolbarText, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                SmallChip(theme, "−", false) {
                    feedback.press()
                    count = (count - 1).coerceAtLeast(2)
                }
                Text("$count", color = theme.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                SmallChip(theme, "+", false) {
                    feedback.press()
                    count = (count + 1).coerceAtMost(100)
                }
                listOf(5, 10, 50, 100).forEach { n ->
                    SmallChip(theme, "$n", count == n) {
                        feedback.press()
                        count = n
                    }
                }
            }
            // Join + numbered
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Join", color = theme.toolbarText, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                TextRepeater.Joiner.entries.forEach { j ->
                    SmallChip(theme, j.label, joiner == j) {
                        feedback.press()
                        joiner = j
                    }
                }
                SmallChip(theme, "1. 2. 3.", numbered) {
                    feedback.press()
                    numbered = !numbered
                }
            }
        }
        PanelBar(controller, theme, feedback) {
            Row(
                Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (source != null) theme.accent else theme.accent.copy(alpha = 0.35f))
                    .clickable(enabled = source != null, onClickLabel = "Repeat") {
                        feedback.press()
                        val s = source ?: return@clickable
                        applyRepeat(controller, s, TextRepeater.repeat(s.text, count, joiner, numbered))
                        refresh++
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Repeat, null, tint = theme.onAccent, modifier = Modifier.size(16.dp))
                Text(" Repeat ×$count", color = theme.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private var repeatCount = 10
private var repeatJoiner = TextRepeater.Joiner.SPACE
private var repeatNumbered = false

/** The text to repeat: the selection, else the current line before the cursor. */
private class RepeatSource(val text: String, val fromSelection: Boolean, val replaceLength: Int)

private fun readRepeatSource(controller: KeyboardController): RepeatSource? {
    val ic = controller.toolConnection() ?: return null
    val selected = ic.getSelectedText(0)?.toString()
    if (!selected.isNullOrBlank()) return RepeatSource(selected, true, selected.length)
    val before = ic.getTextBeforeCursor(1000, 0)?.toString().orEmpty()
    val line = before.substringAfterLast('\n')
    val text = line.trim()
    if (text.isEmpty()) return null
    return RepeatSource(text, false, line.length)
}

private fun applyRepeat(controller: KeyboardController, source: RepeatSource, result: String) {
    if (result.isEmpty()) return
    val ic = controller.toolConnection() ?: return
    if (!source.fromSelection) ic.deleteSurroundingText(source.replaceLength, 0)
    ic.commitText(result, 1) // replaces the selection when there is one
}

@Composable
private fun SmallChip(theme: KeyboardTheme, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) theme.accent else theme.key)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) theme.onAccent else theme.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ================================================================== Secret text

/**
 * Secret text: lock the selected text (or the line you just typed) with a 4–8 digit PIN, and it
 * becomes 🔐SX1:… in the chat. To read one, copy it (or select it) and unlock with the same PIN;
 * the message is shown only here in the keyboard, never pasted or kept.
 */
@Composable
fun SecretPanel(controller: KeyboardController, theme: KeyboardTheme, feedback: KeyFeedback, height: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var unlockMode by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<RepeatSource?>(null) }
    var secret by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    // Read what to lock / unlock after composing (it ends the current word).
    LaunchedEffect(unlockMode, refresh) {
        pin = ""
        revealed = null
        if (unlockMode) {
            secret = findSecret(controller, context)
            source = null
        } else {
            source = readRepeatSource(controller)
            secret = null
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000.milliseconds)
            message = null
        }
    }

    fun act() {
        if (busy || !SecretText.isValidPin(pin)) return
        feedback.press()
        busy = true
        val typedPin = pin
        scope.launch {
            if (unlockMode) {
                val text = secret
                val opened = if (text == null) null else withContext(Dispatchers.Default) { SecretText.unlock(text, typedPin) }
                if (opened == null) message = "Wrong PIN, or the message was changed." else revealed = opened
            } else {
                val src = source
                if (src != null) {
                    val locked = withContext(Dispatchers.Default) { SecretText.lock(src.text, typedPin) }
                    applyRepeat(controller, src, locked)
                    message = "Locked 🔐 Send it, and tell them the PIN another way."
                    refresh++
                }
            }
            pin = ""
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        ToolHeader(controller, theme, feedback, "Secret text") {
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.key)
                    .padding(2.dp),
            ) {
                listOf(false to "Lock", true to "Unlock").forEach { (mode, label) ->
                    Text(
                        label,
                        color = if (unlockMode == mode) theme.onAccent else theme.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (unlockMode == mode) theme.accent else theme.key)
                            .clickable {
                                feedback.press()
                                unlockMode = mode
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }

        val shown = revealed
        if (shown != null) {
            // The unlocked message: shown here only.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.key)
                        .padding(12.dp),
                ) {
                    Text("🔓 $shown", color = theme.text, fontSize = 15.sp, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(40.dp)) {
                    PadKey(theme, "Done", special = true, fontSize = 14) {
                        feedback.press()
                        revealed = null
                    }
                    PadKey(theme, "Insert into chat", accent = true, fontSize = 14, weight = 2f) {
                        feedback.press()
                        controller.insertText(shown)
                        revealed = null
                    }
                }
            }
            return@Column
        }

        // What will be locked / unlocked
        val subject = when {
            message != null -> message!!
            unlockMode && secret != null -> "Found a secret message ✓ Enter its PIN"
            unlockMode -> "Copy a 🔐 secret message (or select it) first"
            source != null -> "“${source!!.text}”"
            else -> "Type or select the text to lock first"
        }
        Text(
            subject,
            color = if (message != null) theme.accent else if (source == null && secret == null) theme.hint else theme.text,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
        )
        // PIN dots
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(SecretText.MAX_PIN) { i ->
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (i < pin.length) theme.accent else theme.key),
                )
            }
            Spacer(Modifier.weight(1f))
            Text("PIN 4–8 digits · everyday privacy", color = theme.hint, fontSize = 10.sp)
        }
        val ready = SecretText.isValidPin(pin) && !busy && (if (unlockMode) secret != null else source != null)
        PadRows(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            listOf<@Composable RowScope.() -> Unit>(
                {
                    for (d in listOf("1", "2", "3", "4", "5")) {
                        PadKey(theme, d, fontSize = 18) {
                            feedback.press()
                            if (pin.length < SecretText.MAX_PIN) pin += d
                        }
                    }
                },
                {
                    for (d in listOf("6", "7", "8", "9", "0")) {
                        PadKey(theme, d, fontSize = 18) {
                            feedback.press()
                            if (pin.length < SecretText.MAX_PIN) pin += d
                        }
                    }
                },
                {
                    PadKey(theme, "ABC", special = true, fontSize = 14, description = "Keyboard") {
                        feedback.press()
                        controller.showKeys()
                    }
                    PadKey(theme, "⌫", special = true, description = "Delete PIN digit") {
                        feedback.press(special = true)
                        pin = pin.dropLast(1)
                    }
                    Box(
                        Modifier
                            .weight(3f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (ready) theme.accent else theme.accent.copy(alpha = 0.35f))
                            .clickable(enabled = ready, onClickLabel = if (unlockMode) "Unlock" else "Lock") { act() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(color = theme.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text(
                                if (unlockMode) "🔓 Unlock" else "🔐 Lock & replace",
                                color = theme.onAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
            ),
        )
    }
}

/** A locked message to open: the selection in the app, else the clipboard, else the text before the cursor. */
private fun findSecret(controller: KeyboardController, context: Context): String? {
    val ic = controller.toolConnection()
    ic?.getSelectedText(0)?.toString()?.let { if (SecretText.containsSecret(it)) return it }
    val clip = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    }.getOrNull()
    if (SecretText.containsSecret(clip)) return clip
    val before = ic?.getTextBeforeCursor(2000, 0)?.toString()
    return before?.takeIf { SecretText.containsSecret(it) }
}

// ================================================================== Phrase studio

/**
 * Phrase studio: ready Sinhala phrases by topic (greetings, wishes, festivals…) plus your own
 * shortcuts ("Mine"), with a decoration to wrap them in (✨ … ✨, ꧁ … ꧂…). Tap to put one in.
 */
@Composable
fun PhrasePanel(ctx: ToolContext, theme: KeyboardTheme, feedback: KeyFeedback, height: Dp) {
    val controller = ctx.controller
    var category by remember { mutableStateOf(lastPhraseCategory) }
    var decoration by remember { mutableStateOf(lastDecoration) }
    LaunchedEffect(category, decoration) {
        lastPhraseCategory = category
        lastDecoration = decoration
    }
    val mine = remember(category) { controller.shortcutList() }
    val phrases: List<Pair<String?, String>> = if (category == MINE) {
        mine.map { (code, text) -> code to text }
    } else {
        PhraseData.categories.firstOrNull { it.label == category }?.phrases.orEmpty().map { null to it }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        // Topics
        LazyRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(listOf(MINE) + PhraseData.categories.map { it.label }) { label ->
                SmallChip(theme, label, label == category) {
                    feedback.press()
                    category = label
                }
            }
        }
        // Decorations
        LazyRow(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(PhraseData.Decoration.entries.toList()) { d ->
                SmallChip(theme, d.label, d == decoration) {
                    feedback.press()
                    decoration = d
                }
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (phrases.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Your shortcuts show here. Add your own phrases as shortcuts.",
                        color = theme.hint,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    SmallChip(theme, "Add shortcuts", true) {
                        feedback.press()
                        ctx.openApp(AppRoutes.SHORTCUTS)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(phrases, key = { it.first.orEmpty() + "|" + it.second }) { (code, phrase) ->
                        val text = decoration.apply(phrase)
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.key)
                                .clickable(onClickLabel = "Insert") {
                                    feedback.press()
                                    controller.insertText(text)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            if (code != null) {
                                Text(code, color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(text, color = theme.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        PanelBar(controller, theme, feedback) {
            Text("Tap a phrase to put it in", color = theme.toolbarText.copy(alpha = 0.65f), fontSize = 11.sp)
        }
    }
}

private const val MINE = "Mine"
private var lastPhraseCategory = "Greetings"
private var lastDecoration = PhraseData.Decoration.NONE

// ================================================================== Emoji style

/**
 * Emoji style: how emoji look in the keyboard (Phone default, Google, Twemoji, OpenMoji).
 * Images load as you browse; "Download" saves the whole set (~3 MB) for offline use.
 * Chats still get normal emoji, so the other person sees their own phone's style.
 */
@Composable
fun EmojiStylePanel(ctx: ToolContext, theme: KeyboardTheme, feedback: KeyFeedback, height: Dp) {
    val context = LocalContext.current
    val current = EmojiStyle.byId(ctx.settings.emojiStyle)
    val progress by EmojiPacks.progress.collectAsState()
    var savedVersion by remember { mutableIntStateOf(0) }
    val saved by produceState(emptyMap<EmojiStyle, Int>(), progress, savedVersion) {
        value = withContext(Dispatchers.IO) {
            EmojiStyle.entries.filter { it != EmojiStyle.SYSTEM }.associateWith { EmojiImages.savedCount(context, it) }
        }
    }
    val allEmoji = remember { EmojiData.categories.flatMap { it.emojis + it.icon }.distinct() }

    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        ToolHeader(ctx.controller, theme, feedback, "Emoji style")
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(EmojiStyle.entries.toList(), key = { it.id }) { style ->
                val selected = style == current
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (selected) theme.accent.copy(alpha = 0.16f) else theme.key)
                        .then(if (selected) Modifier.border(1.5.dp, theme.accent.copy(alpha = 0.55f), shape) else Modifier)
                        .clickable {
                            feedback.press()
                            ctx.updateSettings { it.copy(emojiStyle = style.id) }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (selected) "● " else "○ ") + style.label,
                            color = if (selected) theme.accent else theme.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (style != EmojiStyle.SYSTEM) {
                            val p = progress[style]
                            val count = saved[style] ?: 0
                            when {
                                p != null && p.running -> {
                                    Text("${p.done}/${p.total}  ", color = theme.hint, fontSize = 11.sp)
                                    SmallChip(theme, "Stop", false) {
                                        feedback.press()
                                        EmojiPacks.cancel(style)
                                    }
                                }
                                count >= allEmoji.size * 0.95 -> {
                                    Text("Offline ✓  ", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    SmallChip(theme, "Delete", false) {
                                        feedback.press()
                                        EmojiImages.delete(context, style)
                                        savedVersion++
                                    }
                                }
                                else -> SmallChip(theme, "Download 3 MB", false) {
                                    feedback.press()
                                    EmojiPacks.download(context, style, allEmoji)
                                }
                            }
                        }
                    }
                    // Sample in this style
                    CompositionLocalProvider(LocalEmojiStyle provides style) {
                        Row(
                            Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SAMPLE_EMOJI.forEach { EmojiGlyph(it, size = 26.dp, fontSize = 22.sp) }
                        }
                    }
                    if (style.credit.isNotEmpty()) {
                        Text(style.credit, color = theme.hint, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            item(key = "note") {
                Text(
                    "Only changes how emoji look in this keyboard. Chats still get normal emoji, so " +
                        "others see their own phone's style. Needs internet until downloaded.",
                    color = theme.hint,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private val SAMPLE_EMOJI = listOf("😀", "😂", "😍", "🥺", "👍", "🔥", "🎉", "🙏")

// ================================================================== One-hand

/**
 * One-hand mode: the keys shrink to one side; the free side has buttons to switch sides and to
 * go back to full width.
 */
@Composable
fun OneHandFrame(
    side: String,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    onChange: (String) -> Unit,
    keys: @Composable () -> Unit,
) {
    if (side.isEmpty()) {
        keys()
        return
    }
    val right = side == "right"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (right) OneHandStrip(theme, feedback, right, onChange)
        Box(Modifier.weight(0.82f)) { keys() }
        if (!right) OneHandStrip(theme, feedback, right, onChange)
    }
}

@Composable
private fun RowScope.OneHandStrip(theme: KeyboardTheme, feedback: KeyFeedback, right: Boolean, onChange: (String) -> Unit) {
    Column(
        Modifier.weight(0.18f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StripButton(theme, Icons.Outlined.SwapHoriz, if (right) "Move keys left" else "Move keys right") {
            feedback.press()
            onChange(if (right) "left" else "right")
        }
        StripButton(theme, Icons.Outlined.OpenInFull, "Full width") {
            feedback.press()
            onChange("")
        }
    }
}

@Composable
private fun StripButton(theme: KeyboardTheme, icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(theme.specialKey)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = theme.text, modifier = Modifier.size(22.dp))
    }
}
