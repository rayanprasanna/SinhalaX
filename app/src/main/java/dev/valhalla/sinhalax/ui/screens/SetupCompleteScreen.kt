package dev.valhalla.sinhalax.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.valhalla.sinhalax.R
import dev.valhalla.sinhalax.ui.theme.Navy
import dev.valhalla.sinhalax.ui.theme.NavyDeep
import dev.valhalla.sinhalax.ui.theme.Success
import kotlinx.coroutines.delay

/** How long the "setting up → all set" screen stays before Home appears. */
const val SetupCompleteDurationMs = 2_000L
private const val LoadingPhaseMs = 1_100L

/** Shown once, right after the user selects SinhalaX as their keyboard. */
@Composable
fun SetupCompleteScreen() {
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LoadingPhaseMs)
        done = true
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Navy, NavyDeep)))
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painterResource(R.drawable.sinhalax_logo),
            contentDescription = "SinhalaX",
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    val s = if (done) 1f else scale
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(30.dp)),
        )
        Spacer(Modifier.height(32.dp))

        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = done,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith fadeOut() },
                label = "status",
            ) { finished ->
                if (finished) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Success),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = NavyDeep, modifier = Modifier.size(32.dp))
                    }
                } else {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        Text(
            if (done) "You're all set!" else "Setting up SinhalaX…",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (done) "සිංහලෙන් ලියන්න පටන් ගන්න." else "Loading your dictionary and themes",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
