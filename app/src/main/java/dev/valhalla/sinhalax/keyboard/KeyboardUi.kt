package dev.valhalla.sinhalax.keyboard

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxWithConstraints
import android.content.res.Configuration
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valhalla.sinhalax.data.KeyboardSettings
import dev.valhalla.sinhalax.engine.SinglishTransliterator
import androidx.core.graphics.toColorInt

/**
 * The full keyboard: suggestion/tool strip + keys, emoji or clipboard panel.
 * Used by the IME service and, with a null InputConnection, as the live preview inside the app.
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun SinhalaXKeyboard(
    controller: KeyboardController,
    settings: KeyboardSettings,
    clipItems: List<String>,
    modifier: Modifier = Modifier,
    applySystemInsets: Boolean = true,
    onOpenApp: () -> Unit = {},
    onHide: () -> Unit = {},
    onDeleteClip: (String) -> Unit = {},
    onClearClips: () -> Unit = {},
) {
    val backgroundImage = rememberBackgroundImage(settings.backgroundPath)
    val baseTheme = KeyboardThemes.byId(settings.themeId)
    val theme = if (backgroundImage != null) {
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
    // Landscape: no number row (digits stay on long-press of q–p) and a slimmer top strip,
    // and the whole keyboard is fitted into about half the screen height, so the app above
    // (e.g. WhatsApp's chat and message box) stays usable.
    val numberRow = settings.numberRow && !landscape
    val rows = if (numberRow) 5 else 4
    val stripHeight = if (landscape) 38.dp else 46.dp
    val rowHeight = if (landscape) {
        val budget = configuration.screenHeightDp * 0.5f - stripHeight.value - 8f - settings.bottomPaddingDp
        (budget / rows).coerceIn(30f, 42f * settings.heightScale).dp
    } else {
        (50f * settings.heightScale).dp
    }
    val panelHeight = rowHeight * rows + 8.dp
    val layoutSettings = if (numberRow == settings.numberRow) settings else settings.copy(numberRow = false)
    val background: Brush = if (theme.background.size > 1) {
        Brush.verticalGradient(theme.background)
    } else {
        SolidColor(theme.background.first())
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(background)
            .drawBehind { backgroundImage?.let { drawCentreCrop(it) } }
            .then(if (applySystemInsets) Modifier.navigationBarsPadding() else Modifier)
            .padding(bottom = settings.bottomPaddingDp.dp),
    ) {
        TopStrip(controller, theme, feedback, onOpenApp, onHide, stripHeight)
        when (controller.panel) {
            Panel.KEYS -> KeysPanel(controller, theme, layoutSettings, feedback, rowHeight)
            Panel.EMOJI -> EmojiPanel(controller, theme, feedback, panelHeight)
            Panel.CLIPBOARD -> ClipboardPanel(
                controller, theme, feedback, clipItems, panelHeight, onDeleteClip, onClearClips,
            )
        }
    }
}

/** Non-interactive-safe keyboard for the app's Themes and Settings screens. */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun KeyboardPreview(settings: KeyboardSettings, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as? dev.valhalla.sinhalax.SinhalaXApp
    val controller = remember {
        app?.prediction?.ensureLoaded()
        KeyboardController(
            SinglishTransliterator(),
            inputConnection = { null },
            sendKey = {},
            predictor = { app?.prediction?.predictor?.value },
        )
    }
    LaunchedEffect(settings) { controller.applySettings(settings) }

    var expanded by rememberSaveable { mutableStateOf(true) }
    // Never more than ~45% of the screen; the rest scrolls inside, so the list above stays usable.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp

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
    onOpenApp: () -> Unit,
    onHide: () -> Unit,
    height: Dp = 46.dp,
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
        } else {
            LanguageChip(controller, theme) {
                feedback.press()
                controller.toggleLanguage()
            }
            Spacer(Modifier.weight(1f))
            ToolbarIcon(Icons.Outlined.EmojiEmotions, "Emoji", theme, controller.panel == Panel.EMOJI) {
                feedback.press()
                controller.togglePanel(Panel.EMOJI)
            }
            ToolbarIcon(Icons.Outlined.ContentPaste, "Clipboard", theme, controller.panel == Panel.CLIPBOARD) {
                feedback.press()
                controller.togglePanel(Panel.CLIPBOARD)
            }
            ToolbarIcon(Icons.Outlined.Settings, "Settings", theme, false, onOpenApp)
            ToolbarIcon(Icons.Outlined.KeyboardHide, "Hide keyboard", theme, false, onHide)
        }
    }
}

@Composable
private fun LanguageChip(controller: KeyboardController, theme: KeyboardTheme, onClick: () -> Unit) {
    val sinhala = controller.language == Language.SINHALA
    val dim = theme.toolbarText.copy(alpha = 0.45f)
    Row(
        Modifier
            .clip(CircleShape)
            .background(theme.accent.copy(alpha = 0.14f))
            .clickable(enabled = !controller.restrictedField, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("සිං", color = if (sinhala) theme.accent else dim, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("  ·  ", color = dim, fontSize = 14.sp)
        Text("En", color = if (!sinhala) theme.accent else dim, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    description: String,
    theme: KeyboardTheme,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (active) theme.accent.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick),
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
    val action = spec.action
    val repeatable = action == KeyAction.Delete
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
        if (action == KeyAction.Space) {
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
                    if (repeatable) onTap()

                    var timeout = if (repeatable) 400L else 320L
                    var longFired = false
                    var dragged = false
                    var dragAccum = 0f
                    var released = false
                    var totalY = 0f

                    while (true) {
                        val event = withTimeoutOrNull(timeout) { awaitPointerEvent() }
                        if (event == null) {
                            when {
                                repeatable -> {
                                    onTap()
                                    timeout = 50L
                                }
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
                            if (dragged) change.consume()
                        }
                    }
                    pressed = false
                    if (released && !repeatable && !longFired && !dragged) onTap()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        KeyContent(spec, controller, theme, settings, if (isEnter) theme.onAccent else theme.text)
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
            imageVector = when (controller.enterAction) {
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
            Icons.Outlined.Language, "Switch language",
            tint = if (controller.restrictedField) color.copy(alpha = 0.3f) else color,
            modifier = Modifier.size(20.dp),
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

// ------------------------------------------------------------------ emoji

@Composable
private fun EmojiPanel(
    controller: KeyboardController,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    height: Dp,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (tab == 0) {
            LazyVerticalGrid(columns = GridCells.Adaptive(46.dp), modifier = Modifier.weight(1f)) {
                items(EmojiData.emojis) { emoji ->
                    Box(
                        Modifier
                            .height(46.dp)
                            .clickable {
                                feedback.press()
                                controller.insertText(emoji)
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 26.sp) }
                }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(118.dp), modifier = Modifier.weight(1f)) {
                items(EmojiData.kaomoji) { face ->
                    Box(
                        Modifier
                            .height(46.dp)
                            .clickable {
                                feedback.press()
                                controller.insertText(face)
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(face, color = theme.text, fontSize = 14.sp, maxLines = 1) }
                }
            }
        }
        PanelBottomBar(controller, theme, feedback) {
            PanelTab("😊", tab == 0, theme) { tab = 0 }
            PanelTab("(◕‿◕)", tab == 1, theme) { tab = 1 }
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
            if (clips.isNotEmpty()) PanelTab("Clear all", false, theme, onClear)
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
        PanelTab("ABC", false, theme) {
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
private fun PanelTab(label: String, selected: Boolean, theme: KeyboardTheme, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) theme.accent.copy(alpha = 0.2f) else theme.specialKey)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) theme.accent else theme.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}