package dev.valhalla.sinhalax.keyboard

import android.content.res.Configuration
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import dev.valhalla.sinhalax.data.KeyboardSettings
import dev.valhalla.sinhalax.engine.SinglishTransliterator
import dev.valhalla.sinhalax.store.EmojiStyle
import dev.valhalla.sinhalax.store.KlipyApi
import dev.valhalla.sinhalax.store.LocalEmojiStyle

/**
 * The full keyboard: suggestion/tool strip + keys, emoji or clipboard panel.
 * Used by the IME service and, with a null InputConnection, as the live preview inside the app.
 */
@Composable
fun SinhalaXKeyboard(
    controller: KeyboardController,
    settings: KeyboardSettings,
    clipItems: List<String>,
    modifier: Modifier = Modifier,
    applySystemInsets: Boolean = true,
    onOpenApp: () -> Unit = {},
    onDeleteClip: (String) -> Unit = {},
    onClearClips: () -> Unit = {},
    /** Whether the current text box accepts images (stickers). */
    canSendSticker: Boolean = false,
    /** Sends a sticker into the current app; null in the in-app preview. */
    onSendSticker: (suspend (KlipyApi.Media, String?) -> StickerOutcome)? = null,
    /** Voice typing; null in the in-app preview. */
    voice: VoiceInput? = null,
    /** Translation status for the translate bar; null in the in-app preview. */
    translation: TranslateState? = null,
    /** Saves a settings change made from the keyboard (Tools hub, one-hand, toolbar). */
    onUpdateSettings: ((KeyboardSettings) -> KeyboardSettings) -> Unit = {},
    /** Opens a screen of the app (AppRoutes); defaults to just opening the app. */
    onOpenAppScreen: (String) -> Unit = { onOpenApp() },
    /** Floating keyboard is only possible in the real keyboard (not the app's preview). */
    allowFloating: Boolean = false,
    /** Floating card's rectangle in window pixels (null = docked), for the touchable region. */
    onFloatingBounds: (android.graphics.Rect?) -> Unit = {},
) {
    val tools = ToolContext(controller, settings, onUpdateSettings, onOpenAppScreen)
    // "My theme" (DIY studio) is used unless a downloaded background is applied on top of it.
    val custom = if (settings.themeId == CustomThemeSpec.ID && settings.backgroundPath.isEmpty()) {
        remember(settings.customTheme) { CustomThemeSpec.decode(settings.customTheme) ?: CustomThemeSpec() }
    } else {
        null
    }
    val backgroundImage = rememberBackgroundImage(custom?.photoPath ?: settings.backgroundPath, blur = custom?.photoBlur ?: 0)
    val photoDim = if (custom != null && backgroundImage != null) custom.photoDim else 0f
    val baseTheme = KeyboardThemes.byId(settings.themeId)
    val theme = if (custom != null) {
        remember(custom) { custom.toTheme() }
    } else if (backgroundImage != null) {
        baseTheme.onImage(
            keyText = parseHex(settings.backgroundKeyTextColor, Color.White),
            dominant = parseHex(settings.backgroundDominantColor, baseTheme.background.first()),
        )
    } else {
        baseTheme
    }
    val keySound = rememberKeySound(settings.soundPackPath)
    val feedback = rememberKeyFeedback(settings.vibrate, settings.sound, keySound, settings.soundVolume)
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeight = screenHeightDp()
    // Landscape: no number row (digits stay on long-press of q–p) and a slimmer top strip,
    // and the whole keyboard is fitted into about half the screen height, so the app above
    // (e.g. WhatsApp's chat and message box) stays usable.
    val numberRow = settings.numberRow && !landscape
    val rows = if (numberRow) 5 else 4
    val stripHeight = if (landscape) 38.dp else 46.dp
    val rowHeight = if (landscape) {
        val budget = screenHeight * 0.5f - stripHeight.value - 8f - settings.bottomPaddingDp
        (budget / rows).coerceIn(30f, 42f * settings.heightScale).dp
    } else {
        (50f * settings.heightScale).dp
    }
    val panelHeight = rowHeight * rows + 8.dp
    val layoutSettings = if (landscape && settings.numberRow) settings.copy(numberRow = false) else settings
    val background: Brush = if (theme.background.size > 1) {
        Brush.verticalGradient(theme.background)
    } else {
        SolidColor(theme.background.first())
    }

    val floatingOn = allowFloating && settings.floating
    // The keyboard itself; docked at the bottom, or inside the floating card.
    val keyboard: @Composable (Modifier, Boolean) -> Unit = { outer, insets ->
        Column(
            outer
                .fillMaxWidth()
                .background(background)
                .drawBehind {
                    backgroundImage?.let { drawCentreCrop(it) }
                    if (photoDim > 0f) drawRect(Color.Black.copy(alpha = photoDim))
                }
                .then(if (insets) Modifier.navigationBarsPadding() else Modifier)
                .padding(bottom = if (floatingOn) 0.dp else settings.bottomPaddingDp.dp),
        ) {
            if (controller.searchActive) {
                // Searching GIFs / stickers / memes: search box, quick chips and live results above
                // the normal letter keys (like Gboard). Letters go into the search box, never the chat.
                MediaSearchPanel(
                    controller, theme, feedback,
                    barHeight = stripHeight,
                    resultsHeight = if (landscape) 84.dp else 132.dp,
                    onSend = onSendSticker,
                )
                KeysPanel(controller, theme, layoutSettings, feedback, rowHeight)
                return@Column
            }
            if (controller.translateActive) {
                // Translating: the box sits above the suggestions; letters type into it, and
                // ✓ Translate puts the translation into the app's text field.
                TranslateBar(controller, theme, feedback, stripHeight, translation)
            }
            TopStrip(controller, theme, feedback, tools, stripHeight, translation)
            when (controller.panel) {
                Panel.KEYS -> OneHandFrame(
                    if (floatingOn) "" else settings.oneHand, theme, feedback,
                    onChange = { side -> onUpdateSettings { it.copy(oneHand = side) } },
                ) {
                    KeysPanel(controller, theme, layoutSettings, feedback, rowHeight)
                }
                Panel.TOOLS -> ToolsHub(tools, theme, feedback, panelHeight)
                Panel.TEXT_EDIT -> TextEditPanel(controller, theme, feedback, panelHeight)
                Panel.NUMPAD -> NumberPadPanel(controller, theme, feedback, panelHeight)
                Panel.CALCULATOR -> CalculatorPanel(controller, theme, feedback, panelHeight)
                Panel.REPEAT -> RepeatPanel(controller, theme, feedback, panelHeight)
                Panel.SECRET -> SecretPanel(controller, theme, feedback, panelHeight)
                Panel.PHRASES -> PhrasePanel(tools, theme, feedback, panelHeight)
                Panel.EMOJI_STYLE -> EmojiStylePanel(tools, theme, feedback, panelHeight)
                Panel.VOICE -> VoicePanel(controller, theme, feedback, panelHeight, voice)
                Panel.EMOJI -> EmojiHub(controller, theme, feedback, panelHeight, canSendSticker, onSendSticker)
                Panel.CLIPBOARD -> ClipboardPanel(
                    controller, theme, feedback, clipItems, panelHeight, onDeleteClip, onClearClips,
                )
                Panel.FANCY -> FancyPanel(controller, theme, feedback, panelHeight)
            }
        }
    }

    CompositionLocalProvider(LocalEmojiStyle provides EmojiStyle.byId(settings.emojiStyle)) {
        if (floatingOn) {
            FloatingFrame(settings, theme, background, feedback, onUpdateSettings, onFloatingBounds) {
                keyboard(Modifier, false)
            }
        } else {
            keyboard(modifier, applySystemInsets)
        }
    }
}

/** Non-interactive-safe keyboard for the app's Themes and Settings screens. */
@Composable
fun KeyboardPreview(settings: KeyboardSettings, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as? dev.valhalla.sinhalax.SinhalaXApp
    val controller = remember {
        app?.prediction?.ensureLoaded()
        KeyboardController(
            SinglishTransliterator(),
            appConnection = { null },
            sendAppKey = {},
            predictor = { app?.prediction?.predictor?.value },
        )
    }
    LaunchedEffect(settings) { controller.applySettings(settings) }

    var expanded by rememberSaveable { mutableStateOf(true) }
    // Never more than ~45% of the screen; the rest scrolls inside, so the list above stays usable.
    val maxHeight = (screenHeightDp() * 0.45f).dp

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Keyboard preview",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = if (expanded) "Hide preview" else "Show preview",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                SinhalaXKeyboard(
                    controller = controller,
                    settings = settings,
                    clipItems = listOf("ඔබ copy කරන දේවල් මෙතන පෙනේ", "Copied text appears here"),
                    applySystemInsets = false,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ feedback

class KeyFeedback(
    private val view: View,
    private val audio: AudioManager?,
    private val vibrate: Boolean,
    private val sound: Boolean,
    private val keySound: KeySoundPlayer? = null,
    private val volume: Float = 0.6f,
) {
    fun press(special: Boolean = false) {
        if (vibrate) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (sound && keySound != null) {
            keySound.play(volume)
        } else if (sound) {
            audio?.playSoundEffect(
                if (special) AudioManager.FX_KEYPRESS_DELETE else AudioManager.FX_KEYPRESS_STANDARD,
                -1f,
            )
        }
    }

    fun tick() {
        if (vibrate) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

@Composable
private fun rememberKeyFeedback(
    vibrate: Boolean,
    sound: Boolean,
    keySound: KeySoundPlayer?,
    volume: Float,
): KeyFeedback {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view, vibrate, sound, keySound, volume) {
        KeyFeedback(view, context.getSystemService(AudioManager::class.java), vibrate, sound, keySound, volume)
    }
}

private fun parseHex(hex: String, fallback: Color): Color =
    if (hex.isBlank()) fallback else runCatching { Color(hex.toColorInt()) }.getOrDefault(fallback)

/**
 * Screen height in dp, from the display. (Not LocalWindowInfo.containerSize: in a keyboard that is
 * the keyboard's own window, which would make the size depend on itself.) Updates on rotation.
 */
@Composable
private fun screenHeightDp(): Float {
    val configuration = LocalConfiguration.current
    val resources = LocalContext.current.resources
    return remember(configuration) {
        val metrics = resources.displayMetrics
        metrics.heightPixels / metrics.density
    }
}

/** Draws [image] to fill the area, cropping the overflow evenly (like ContentScale.Crop). */
private fun DrawScope.drawCentreCrop(image: ImageBitmap) {
    if (size.width <= 0f || size.height <= 0f) return
    val scale = maxOf(size.width / image.width, size.height / image.height)
    val srcWidth = (size.width / scale).toInt().coerceIn(1, image.width)
    val srcHeight = (size.height / scale).toInt().coerceIn(1, image.height)
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - srcWidth) / 2, (image.height - srcHeight) / 2),
        srcSize = IntSize(srcWidth, srcHeight),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
    )
}

// ------------------------------------------------------------------ top strip

@Composable
private fun TopStrip(
    controller: KeyboardController,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    tools: ToolContext,
    height: Dp = 46.dp,
    translation: TranslateState? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val candidates = controller.candidates
        if (candidates.isNotEmpty()) {
            candidates.forEachIndexed { index, word ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(theme.toolbarText.copy(alpha = 0.25f)),
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            feedback.press()
                            controller.pickCandidate(word)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = word,
                        color = if (index == 0) theme.accent else theme.toolbarText,
                        fontSize = if (index == 0) 19.sp else 17.sp,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else if (controller.translateActive) {
            TranslateStatus(controller, theme, translation)
        } else {
            // SinhalaX wordmark (opens the Tools hub), then your toolbar buttons spread evenly.
            Wordmark(theme, active = controller.panel == Panel.TOOLS, incognito = controller.incognito) {
                feedback.press()
                if (controller.panel == Panel.TOOLS) controller.showKeys() else controller.openPanel(Panel.TOOLS)
            }
            val toolModifier = Modifier.weight(1f)
            ToolId.parseToolbar(tools.settings.toolbarItems).forEach { id ->
                ToolbarButton(id, tools, theme, feedback, toolModifier)
            }
        }
    }
}

/** One toolbar button for [id] (your toolbar is set in Tools → Look & feel). */
@Composable
private fun ToolbarButton(id: ToolId, tools: ToolContext, theme: KeyboardTheme, feedback: KeyFeedback, modifier: Modifier) {
    val controller = tools.controller
    val active = tools.isToolActive(id)
    val onClick = {
        if (id != ToolId.LANGUAGE || !controller.restrictedField) {
            feedback.press()
            tools.runTool(id)
        }
    }
    when (id) {
        ToolId.FANCY -> FancyTextButton(theme = theme, active = active, modifier = modifier, onClick = onClick)
        else -> {
            val description = when (id) {
                ToolId.LANGUAGE -> if (controller.language == Language.SINHALA) "Sinhala, switch to English" else "English, switch to Sinhala"
                ToolId.GIF -> "GIFs, stickers and memes (KLIPY)"
                ToolId.SETTINGS -> if (active) "Close tools" else "Tools and settings"
                else -> id.label
            }
            val icon = id.icon(controller.language)
            if (icon != null) {
                ToolbarIcon(icon, description, theme, active, modifier, onClick)
            } else {
                ToolbarLabel(id.textIcon(), description, theme, active, modifier, onClick)
            }
        }
    }
}

/**
 * Opens the emoji panel on the emoji side (😊) or the KLIPY side (GIF). Tapping the button that is
 * already showing closes the panel; tapping the other one just switches tab.
 */
internal fun openEmojiHub(controller: KeyboardController, gifs: Boolean) {
    val open = controller.panel == Panel.EMOJI
    if (open && klipyTabShowing == gifs) {
        controller.togglePanel(Panel.EMOJI) // close
        return
    }
    lastTab = emojiHubTabFor(gifs)
    if (!open) controller.togglePanel(Panel.EMOJI)
}

/**
 * "SinhalaX" at the top left: tap to open the Tools hub (tap again to close it).
 * A small 🕶 shows while Incognito is on.
 */
@Composable
private fun Wordmark(theme: KeyboardTheme, active: Boolean, incognito: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) theme.accent.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClickLabel = if (active) "Close tools" else "SinhalaX tools", onClick = onClick)
            .padding(start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sinhala", color = theme.toolbarText, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("X", color = theme.accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        if (incognito) {
            Icon(
                Icons.Outlined.VisibilityOff,
                contentDescription = "Incognito on",
                tint = theme.accent,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(14.dp),
            )
        }
    }
}

/** Text instead of an icon (FM). */
@Composable
private fun ToolbarLabel(
    text: String,
    description: String,
    theme: KeyboardTheme,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = 38.dp)
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .background(if (active) theme.accent.copy(alpha = 0.2f) else Color.Transparent)
                .clickable(onClickLabel = description, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = if (active) theme.accent else theme.toolbarText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    description: String,
    theme: KeyboardTheme,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = 38.dp)
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .background(if (active) theme.accent.copy(alpha = 0.2f) else Color.Transparent)
                .clickable(onClickLabel = description, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (active) theme.accent else theme.toolbarText,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** The "Aa" fancy-text button in the toolbar. */
@Composable
private fun FancyTextButton(
    theme: KeyboardTheme,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = 38.dp)
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .background(if (active) theme.accent.copy(alpha = 0.2f) else Color.Transparent)
                .clickable(onClickLabel = "Fancy text", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Aa",
                color = if (active) theme.accent else theme.toolbarText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
            )
        }
    }
}

// ------------------------------------------------------------------ keys

@Composable
private fun KeysPanel(
    controller: KeyboardController,
    theme: KeyboardTheme,
    settings: KeyboardSettings,
    feedback: KeyFeedback,
    rowHeight: Dp,
) {
    val rows = when (controller.mode) {
        KeyMode.LETTERS -> KeyboardLayouts.letters(settings.numberRow)
        KeyMode.SYMBOLS -> KeyboardLayouts.symbols
        KeyMode.SYMBOLS2 -> KeyboardLayouts.symbols2
    }
    val hueShift = if (theme.rgbBorders) {
        val transition = rememberInfiniteTransition(label = "rgb")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 6000, easing = LinearEasing)),
            label = "hue",
        )
        value
    } else {
        0f
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 4.dp),
    ) {
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
            ) {
                val total = row.sumOf { it.weight.toDouble() }.toFloat()
                var used = 0f
                row.forEach { spec ->
                    val centre = (used + spec.weight / 2f) / total
                    used += spec.weight
                    if (spec.action == KeyAction.None) {
                        Spacer(Modifier.weight(spec.weight))
                    } else {
                        Key(
                            spec = spec,
                            controller = controller,
                            theme = theme,
                            settings = settings,
                            feedback = feedback,
                            hue = (centre * 300f + hueShift) % 360f,
                            modifier = Modifier
                                .weight(spec.weight)
                                .fillMaxHeight()
                                .padding(3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(
    spec: KeySpec,
    controller: KeyboardController,
    theme: KeyboardTheme,
    settings: KeyboardSettings,
    feedback: KeyFeedback,
    hue: Float,
    modifier: Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    // Slide feedback: space shows ◀ ▶ while moving the cursor; ⌫ shows how many words are selected.
    var sliding by remember { mutableStateOf(false) }
    var swipeWords by remember { mutableIntStateOf(0) }
    val action = spec.action
    val repeatable = action == KeyAction.Delete
    // ⌫ with slide gestures: slide left to select words, let go to delete them.
    val swipeDelete by rememberUpdatedState(repeatable && settings.slideGestures)
    val isEnter = action == KeyAction.Enter
    val stepPx = with(LocalDensity.current) { 14.dp.toPx() }

    val onTap by rememberUpdatedState {
        feedback.press(special = spec.special)
        controller.onAction(action)
    }
    val onLongPress by rememberUpdatedState(
        spec.longPress?.let { extra ->
            {
                feedback.press()
                controller.onAction(KeyAction.Text(extra))
            }
        },
    )
    val onDrag by rememberUpdatedState(
        if (action == KeyAction.Space && settings.slideGestures) {
            { direction: Int ->
                feedback.tick()
                controller.moveCursor(direction)
            }
        } else {
            null
        },
    )

    val shape = RoundedCornerShape(theme.keyRadius)
    val base = when {
        isEnter -> theme.accent
        spec.special -> theme.specialKey
        else -> theme.key
    }
    val border: BorderStroke? = when {
        theme.rgbBorders -> BorderStroke(1.5.dp, Color.hsv(hue, 0.9f, 1f))
        theme.keyBorder != null -> BorderStroke(1.dp, theme.keyBorder)
        else -> null
    }

    Box(
        modifier
            .graphicsLayer {
                val scale = if (pressed) 0.94f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (pressed) theme.keyPressed else base)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .pointerInput(spec) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    // Without swipe-delete, ⌫ deletes on touch. With it, a tap deletes on release so a
                    // swipe doesn't also eat a letter; holding still still repeats after 400 ms.
                    if (repeatable && !swipeDelete) onTap()

                    var timeout = if (repeatable) 400L else 320L
                    var longFired = false
                    var dragged = false
                    var dragAccum = 0f
                    var released = false
                    var totalY = 0f
                    var repeated = false
                    var deleteX = 0f
                    var deleteSwipe: TextEditing.DeleteSwipe? = null

                    while (true) {
                        val event = withTimeoutOrNull(timeout) { awaitPointerEvent() }
                        if (event == null) {
                            when {
                                repeatable && deleteSwipe == null -> {
                                    onTap()
                                    repeated = true
                                    timeout = 50L
                                }
                                repeatable -> timeout = 60_000L
                                !dragged && !longFired && onLongPress != null -> {
                                    longFired = true
                                    onLongPress?.invoke()
                                    timeout = 60_000L
                                }
                                else -> timeout = 60_000L
                            }
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp() || !change.pressed) {
                            released = !change.isConsumed
                            change.consume()
                            break
                        }
                        // A parent (the scrollable preview in the app) took this gesture: cancel the key.
                        // `released` stays false, so no tap fires.
                        if (change.isConsumed) break
                        totalY += change.positionChange().y
                        if (swipeDelete && !repeated) {
                            deleteX += change.positionChange().x
                            val startAt = stepPx * 1.5f
                            if (deleteSwipe == null && deleteX <= -startAt) {
                                deleteSwipe = controller.toolConnection()?.let { TextEditing.DeleteSwipe(it) }
                                sliding = deleteSwipe != null
                            }
                            val swipe = deleteSwipe
                            if (swipe != null) {
                                // One more word for every ~28 dp further left; sliding back right selects fewer.
                                val words = if (deleteX > -startAt) 0 else (((-deleteX - startAt) / (stepPx * 2)).toInt() + 1)
                                if (words != swipeWords) {
                                    swipeWords = words
                                    swipe.select(words)
                                    feedback.tick()
                                }
                                change.consume()
                            }
                        }
                        val drag = onDrag
                        if (drag != null && kotlin.math.abs(totalY) < stepPx) {
                            dragAccum += change.positionChange().x
                            while (dragAccum >= stepPx) {
                                drag(1); dragAccum -= stepPx; dragged = true
                            }
                            while (dragAccum <= -stepPx) {
                                drag(-1); dragAccum += stepPx; dragged = true
                            }
                            // Only a horizontal space-bar drag is ours; everything else may scroll.
                            if (dragged) {
                                sliding = true
                                change.consume()
                            }
                        }
                    }
                    pressed = false
                    val swipe = deleteSwipe
                    when {
                        swipe != null -> if (released) swipe.finish() else swipe.cancel()
                        swipeDelete -> if (released && !repeated) onTap()
                        released && !repeatable && !longFired && !dragged -> onTap()
                    }
                    sliding = false
                    swipeWords = 0
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (sliding) {
            Text(
                if (action == KeyAction.Space) "◀   ▶" else if (swipeWords > 0) "⌫ $swipeWords" else "◀",
                color = theme.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        } else {
            KeyContent(spec, controller, theme, settings, if (isEnter) theme.onAccent else theme.text)
        }
    }
}

@Composable
private fun KeyContent(
    spec: KeySpec,
    controller: KeyboardController,
    theme: KeyboardTheme,
    settings: KeyboardSettings,
    color: Color,
) {
    val font = settings.keyFontScale
    when (spec.action) {
        KeyAction.Shift -> {
            val active = controller.shift != ShiftState.OFF
            Icon(
                imageVector = when (controller.shift) {
                    ShiftState.OFF -> ShiftIcons.Off
                    ShiftState.ONCE -> ShiftIcons.Once
                    ShiftState.LOCKED -> ShiftIcons.Locked
                },
                contentDescription = when (controller.shift) {
                    ShiftState.OFF -> "Shift"
                    ShiftState.ONCE -> "Shift on"
                    ShiftState.LOCKED -> "Caps lock on"
                },
                tint = if (active) theme.accent else color,
                modifier = Modifier.size((22 * font).dp),
            )
        }
        KeyAction.Delete -> Icon(
            Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = color, modifier = Modifier.size(22.dp),
        )
        KeyAction.Enter -> Icon(
            imageVector = when (
                when {
                    controller.searchActive -> EnterAction.SEARCH
                    controller.translateActive -> EnterAction.DONE // ✓ keeps the translation
                    else -> controller.enterAction
                }
            ) {
                EnterAction.SEARCH -> Icons.Outlined.Search
                EnterAction.SEND -> Icons.AutoMirrored.Outlined.Send
                EnterAction.GO, EnterAction.NEXT -> Icons.AutoMirrored.Outlined.ArrowForward
                EnterAction.PREVIOUS -> Icons.AutoMirrored.Outlined.ArrowBack
                EnterAction.DONE -> Icons.Outlined.Check
                EnterAction.NEWLINE -> Icons.AutoMirrored.Outlined.KeyboardReturn
            },
            contentDescription = "Enter",
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        KeyAction.ToggleLanguage -> Icon(
            LanguageIcons.forLanguage(controller.language),
            if (controller.language == Language.SINHALA) "Sinhala, switch to English" else "English, switch to Sinhala",
            tint = if (controller.restrictedField) color.copy(alpha = 0.3f) else color,
            modifier = Modifier.size(22.dp),
        )
        KeyAction.Space -> Text(
            text = if (controller.language == Language.SINHALA) "SinhalaX · සිංහල" else "SinhalaX · English",
            color = theme.hint.copy(alpha = 0.8f),
            fontSize = (13 * font).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        else -> {
            val label = controller.displayLabel(spec.label)
            val hint = if (settings.showHints) {
                controller.sinhalaHint(spec.label)
                    ?: spec.longPress?.takeIf { controller.language == Language.ENGLISH && controller.mode == KeyMode.LETTERS }
            } else {
                null
            }
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Short keys (landscape, small height setting): smaller text, hint tucked in the corner.
            val compact = maxHeight < 46.dp
            val letterSp = (if (label.length == 1) (if (compact) 16 else 21) else (if (compact) 12 else 15)) * font
            if (hint == null) {
                Text(
                    text = label,
                    color = color,
                    fontSize = letterSp.sp,
                    lineHeight = (letterSp + 2).sp,
                    maxLines = 1,
                )
            } else if (compact) {
                Box(Modifier.fillMaxSize()) {
                    Text(
                        text = hint,
                        color = theme.hint,
                        fontSize = (8 * font).sp,
                        lineHeight = (9 * font).sp,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 1.dp, end = 3.dp),
                    )
                    Text(
                        text = label,
                        color = color,
                        fontSize = letterSp.sp,
                        lineHeight = (letterSp + 1).sp,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 3.dp),
                    )
                }
            } else {
                // Hint gets its own row at the top; the letter is centred in the space below.
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 2.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    Text(
                        text = hint,
                        color = theme.hint,
                        fontSize = (10 * font).sp,
                        lineHeight = (12 * font).sp,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.End),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = color,
                            fontSize = ((if (label.length == 1) 19 else 14) * font).sp,
                            lineHeight = ((if (label.length == 1) 21 else 16) * font).sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            }
        }
    }
}

// ------------------------------------------------------------------ clipboard

@Composable
private fun ClipboardPanel(
    controller: KeyboardController,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    clips: List<String>,
    height: Dp,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (clips.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Copy text in any app and it will appear here.\nStored only on this phone.",
                    color = theme.toolbarText.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            ) {
                items(clips) { clip ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.key)
                            .clickable {
                                feedback.press()
                                controller.insertText(clip)
                            }
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            clip,
                            color = theme.text,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { onDelete(clip) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Close, "Remove", tint = theme.hint, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        PanelBottomBar(controller, theme, feedback) {
            if (clips.isNotEmpty()) PanelTab("Clear all", theme, onClear)
        }
    }
}

// ------------------------------------------------------------------ shared panel bar

@Composable
private fun PanelBottomBar(
    controller: KeyboardController,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    middle: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PanelTab("ABC", theme) {
            feedback.press()
            controller.showKeys()
        }
        Spacer(Modifier.weight(1f))
        middle()
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(width = 56.dp, height = 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.specialKey)
                .clickable {
                    feedback.press(special = true)
                    controller.onAction(KeyAction.Delete)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = theme.text, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PanelTab(label: String, theme: KeyboardTheme, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(theme.specialKey)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = theme.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
