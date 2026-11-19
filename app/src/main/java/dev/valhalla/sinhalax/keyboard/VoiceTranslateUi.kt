package dev.valhalla.sinhalax.keyboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ shared state

/** What the voice panel shows. Filled by the IME service's speech recogniser. */
@Stable
class VoiceState {
    var listening by mutableStateOf(false)

    /** Microphone level, 0..1, for the pulsing ring. */
    var level by mutableFloatStateOf(0f)

    /** Words heard so far in this phrase (not final yet). */
    var partial by mutableStateOf("")

    /** Problem or hint to show instead of "Listening…", or null. */
    var message by mutableStateOf<String?>(null)
}

/** Voice typing as the keyboard UI sees it (implemented by the IME service; null in the app preview). */
interface VoiceInput {
    val state: VoiceState

    /** Starts listening in [language]. Asks for the microphone permission first if needed. */
    fun start(language: Language)

    /** Stops listening and keeps what was heard. */
    fun finish()

    /** Stops listening (panel closed). */
    fun cancel()
}

/** What the translate bar shows. Filled by the IME service. */
@Stable
class TranslateState {
    var result by mutableStateOf("")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    /** "Translated by Microsoft" / "Translated by Google", shown in the status line. */
    var attribution by mutableStateOf("")
}

/** "සිංහල → English" or "English → සිංහල", following the keyboard language. */
fun translateDirectionLabel(language: Language): String =
    if (language == Language.SINHALA) "සිංහල → English" else "English → සිංහල"

// ------------------------------------------------------------------ translate bar

/**
 * Shown above the suggestions while translating (tap-to-translate, like Z Sri):
 *  row 1: ✕ close · direction chip ("සිං → En", tap to swap) · status · ✓ Translate
 *  row 2: the translate box with a blinking cursor (letters typed on the keys go here).
 */
@Composable
fun TranslateBar(
    controller: KeyboardController,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    height: Dp,
    state: TranslateState?,
) {
    val blink = rememberInfiniteTransition(label = "translateCursor")
    val cursorAlpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
        label = "translateCursorAlpha",
    )
    val sinhala = controller.language == Language.SINHALA
    val busy = state?.busy == true || controller.translateRequest != null
    val hasText = controller.translateText.isNotBlank()

    Column(Modifier.fillMaxWidth()) {
        // ---- row 1: close · direction · status · Translate
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Close translate") {
                        feedback.press()
                        controller.stopTranslate()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, "Close translate", tint = theme.toolbarText, modifier = Modifier.size(20.dp))
            }
            Row(
                Modifier
                    .padding(start = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(theme.accent.copy(alpha = 0.16f))
                    .clickable(enabled = !busy, onClickLabel = "Swap languages") {
                        feedback.press()
                        controller.toggleLanguage()
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (sinhala) "සිං" else "En", color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("  →  ", color = theme.accent, fontSize = 13.sp)
                Text(if (sinhala) "En" else "සිං", color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Icon(
                    Icons.Outlined.SwapHoriz, null, tint = theme.accent,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(16.dp),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                val error = state?.error
                when {
                    busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = theme.accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        Text("  Translating…", color = theme.toolbarText.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
                    }
                    error != null -> Text(
                        error, color = theme.toolbarText.copy(alpha = 0.85f), fontSize = 11.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                    )
                }
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (hasText && !busy) theme.accent else theme.accent.copy(alpha = 0.35f))
                    .clickable(enabled = hasText && !busy, onClickLabel = "Translate") {
                        feedback.press()
                        controller.submitTranslate()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Check, null, tint = theme.onAccent, modifier = Modifier.size(16.dp))
                Text(" Translate", color = theme.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ---- row 2: the box
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.key)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                val text = controller.translateText
                if (text.isEmpty()) {
                    Text(
                        if (sinhala) "Singlish වලින් ලියන්න…" else "Type here to translate…",
                        color = theme.hint,
                        fontSize = 15.sp,
                        maxLines = 1,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text,
                        color = theme.text,
                        fontSize = 15.sp,
                        maxLines = 2,
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
            if (controller.translateText.isNotEmpty()) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Clear") {
                            feedback.press()
                            controller.clearTranslateText()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, "Clear", tint = theme.hint, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Line under the translate box (where the tools usually are) when there are no suggestions:
 * the last translation, or a hint. The provider's attribution sits at the end.
 */
@Composable
fun TranslateStatus(controller: KeyboardController, theme: KeyboardTheme, state: TranslateState?) {
    Row(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val last = state?.result.orEmpty()
        if (last.isNotEmpty()) {
            Icon(Icons.Outlined.Check, null, tint = theme.accent, modifier = Modifier.size(16.dp))
            Text(
                " $last",
                color = theme.accent,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else {
            Text(
                if (state == null) "Translate works in the keyboard." else "Tap Translate (or enter) to put it in your message",
                color = theme.toolbarText.copy(alpha = 0.7f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.weight(1f))
        if (!state?.attribution.isNullOrEmpty()) {
            Text(state.attribution, color = theme.toolbarText.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ voice panel

/**
 * Voice typing: a big microphone with a ring that follows your voice, the words as they are heard,
 * and a language switch (Sinhala uses si-LK, English en-US). Words go straight into the chat
 * (or into the translate box while translating). Listening starts when the panel opens.
 */
@Composable
fun VoicePanel(
    controller: KeyboardController,
    theme: KeyboardTheme,
    feedback: KeyFeedback,
    height: Dp,
    voice: VoiceInput?,
) {
    val state = voice?.state
    val language = controller.language
    LaunchedEffect(voice, language) { voice?.start(language) }
    DisposableEffect(voice) {
        onDispose {
            voice?.cancel()
            controller.endVoiceText()
        }
    }

    val listening = state?.listening == true
    val level by animateFloatAsState(if (listening) state.level ?: 0f else 0f, tween(90), label = "micLevel")
    val sinhala = language == Language.SINHALA

    Column(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val status = when {
                voice == null -> "Voice typing works in the keyboard."
                state?.message != null -> state.message!!
                listening -> if (sinhala) "කතා කරන්න… (සිංහල)" else "Speak now… (English)"
                else -> "Tap the mic to speak"
            }
            Text(status, color = theme.toolbarText.copy(alpha = 0.8f), fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
                // Ring that grows with the voice level.
                Box(
                    Modifier
                        .size(112.dp)
                        .graphicsLayer {
                            val s = 0.62f + 0.38f * level
                            scaleX = s
                            scaleY = s
                        }
                        .clip(CircleShape)
                        .background(theme.accent.copy(alpha = if (listening) 0.25f else 0f)),
                )
                Box(
                    Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(if (listening) theme.accent else theme.key)
                        .clickable(onClickLabel = if (listening) "Stop" else "Speak") {
                            feedback.press()
                            if (listening) voice.finish() else voice?.start(language)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = if (listening) "Stop" else "Speak",
                        tint = if (listening) theme.onAccent else theme.text,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                state?.partial.orEmpty(),
                color = theme.text,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.StartEllipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(44.dp),
            )
        }

        // Bottom bar: ABC · language · delete
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceBarButton(theme, "Keyboard") {
                feedback.press()
                controller.showKeys()
            }
            Spacer(Modifier.width(6.dp))
            Row(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.specialKey)
                    .clickable(enabled = !controller.restrictedField, onClickLabel = "Switch voice language") {
                        feedback.press()
                        controller.toggleLanguage()
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LanguageIcons.forLanguage(language), null, tint = theme.text, modifier = Modifier.size(20.dp))
                Text(
                    if (sinhala) "  සිංහල" else "  English",
                    color = theme.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(width = 46.dp, height = 36.dp)
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
}

@Composable
private fun VoiceBarButton(theme: KeyboardTheme, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 52.dp, height = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(theme.specialKey)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("ABC", color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
