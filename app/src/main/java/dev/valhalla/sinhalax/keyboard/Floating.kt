package dev.valhalla.sinhalax.keyboard

import android.content.res.Configuration
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valhalla.sinhalax.data.KeyboardSettings
import kotlin.math.roundToInt

/** Card widths the ⇔ button cycles through (fraction of the screen width). */
private val FLOAT_WIDTHS = listOf(0.7f, 0.85f, 1f)

/**
 * Floating keyboard: the keyboard as a card you can drag anywhere over the app (Gboard-style).
 * The IME window covers the screen, but only this card takes touches: [onBounds] reports its
 * rectangle (window pixels) so the service can limit the touchable region and tell the app
 * not to resize. Handle bar: ⇔ cycles the width, drag the middle to move, ⤓ docks it again.
 */
@Composable
fun FloatingFrame(
    settings: KeyboardSettings,
    theme: KeyboardTheme,
    background: Brush,
    feedback: KeyFeedback,
    onUpdateSettings: ((KeyboardSettings) -> KeyboardSettings) -> Unit,
    onBounds: (Rect?) -> Unit,
    content: @Composable () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val reportBounds by rememberUpdatedState(onBounds)
    DisposableEffect(Unit) { onDispose { reportBounds(null) } }

    // Keep the card clear of the status and navigation bars.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        // Read the size here: maxWidth / maxHeight can't be used inside the Column below.
        val density = LocalDensity.current
        val widthFraction = if (landscape) settings.floatWidth.coerceAtMost(0.6f) else settings.floatWidth
        val cardWidth = maxWidth * widthFraction
        val freeWidthPx = with(density) { (maxWidth - cardWidth).toPx() }.coerceAtLeast(0f)
        val maxHeightPx = with(density) { maxHeight.toPx() }

        var cardHeightPx by remember { mutableFloatStateOf(0f) }
        var fx by remember(settings.floatX) { mutableFloatStateOf(settings.floatX.coerceIn(0f, 1f)) }
        var fy by remember(settings.floatY) { mutableFloatStateOf(settings.floatY.coerceIn(0f, 1f)) }
        val freeHeightPx = (maxHeightPx - cardHeightPx).coerceAtLeast(0f)
        val shape = RoundedCornerShape(18.dp)

        Column(
            Modifier
                .offset { IntOffset((fx * freeWidthPx).roundToInt(), (fy * freeHeightPx).roundToInt()) }
                .width(cardWidth)
                .shadow(16.dp, shape)
                .clip(shape)
                .onGloballyPositioned { coords ->
                    cardHeightPx = coords.size.height.toFloat()
                    val b = coords.boundsInWindow()
                    reportBounds(Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt()))
                },
        ) {
            // ---- handle: ⇔ width · drag area · ⤓ dock
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(background),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "⇔",
                    color = theme.toolbarText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Change width") {
                            feedback.press()
                            val next = FLOAT_WIDTHS.firstOrNull { it > settings.floatWidth + 0.01f } ?: FLOAT_WIDTHS.first()
                            onUpdateSettings { it.copy(floatWidth = next) }
                        }
                        .padding(horizontal = 12.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(freeWidthPx, freeHeightPx) {
                            detectDragGestures(
                                onDragEnd = { onUpdateSettings { it.copy(floatX = fx, floatY = fy) } },
                                onDragCancel = { onUpdateSettings { it.copy(floatX = fx, floatY = fy) } },
                            ) { change, drag ->
                                change.consume()
                                if (freeWidthPx > 0f) fx = (fx + drag.x / freeWidthPx).coerceIn(0f, 1f)
                                if (freeHeightPx > 0f) fy = (fy + drag.y / freeHeightPx).coerceIn(0f, 1f)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Grip
                    Box(
                        Modifier
                            .size(width = 44.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(theme.toolbarText.copy(alpha = 0.45f)),
                    )
                }
                Box(
                    Modifier
                        .padding(horizontal = 6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Dock keyboard") {
                            feedback.press()
                            onUpdateSettings { it.copy(floating = false) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.VerticalAlignBottom, "Dock keyboard", tint = theme.toolbarText, modifier = Modifier.size(18.dp))
                }
            }
            content()
        }
    }
}
