package dev.valhalla.sinhalax.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.valhalla.sinhalax.R
import dev.valhalla.sinhalax.ui.ImeStatus
import dev.valhalla.sinhalax.ui.theme.Navy
import dev.valhalla.sinhalax.ui.theme.NavyDeep
import dev.valhalla.sinhalax.ui.theme.Success

/**
 * First run: enable and select SinhalaX. Adapts to the screen:
 *  - landscape (or short screens): logo + title on the left, steps on the right;
 *  - portrait: one column.
 * Both scroll when there isn't room (small phones, large font sizes).
 */
@Composable
fun OnboardingScreen(status: ImeStatus, onSkip: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Navy, NavyDeep)))
            .safeDrawingPadding(),
    ) {
        // Read the size here: maxWidth / maxHeight can't be used inside the Row / Column below.
        val twoPane = maxWidth > maxHeight && maxWidth >= 560.dp
        val sideLogoSize = if (maxHeight < 400.dp) 80.dp else 104.dp
        if (twoPane) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Column(
                    Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Welcome(logoSize = sideLogoSize)
                }
                Column(
                    Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Steps(status, onSkip)
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Welcome(logoSize = 112.dp)
                Spacer(Modifier.height(32.dp))
                Steps(status, onSkip)
            }
        }
    }
}

/** Logo, title and tagline. */
@Composable
private fun Welcome(logoSize: Dp) {
    Image(
        painterResource(R.drawable.sinhalax_logo),
        contentDescription = "SinhalaX",
        modifier = Modifier
            .size(logoSize)
            .clip(RoundedCornerShape(logoSize / 4)),
    )
    Spacer(Modifier.height(20.dp))
    Text(
        "Welcome to SinhalaX",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "සිංහලෙන් ලියන්න — fast, beautiful and private.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** The two setup steps and "Skip for now", at a readable width on wide screens. */
@Composable
private fun Steps(status: ImeStatus, onSkip: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepCard(
            number = 1,
            title = "Enable SinhalaX",
            body = "Turn on SinhalaX in your keyboard list. Android will show a standard warning for every third-party keyboard.",
            done = status.enabled,
            active = !status.enabled,
            action = "Open settings",
        ) { ImeStatus.openEnableSettings(context) }

        Spacer(Modifier.height(12.dp))

        StepCard(
            number = 2,
            title = "Select SinhalaX",
            body = "Choose SinhalaX as the keyboard you type with.",
            done = status.selected,
            active = status.enabled && !status.selected,
            action = "Choose keyboard",
        ) { ImeStatus.showPicker(context) }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onSkip) { Text("Skip for now") }
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    body: String,
    done: Boolean,
    active: Boolean,
    action: String,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (done) Success else MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(Icons.Outlined.Check, null, tint = NavyDeep, modifier = Modifier.size(18.dp))
                    } else {
                        Text("$number", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (active) {
                Spacer(Modifier.height(14.dp))
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(action) }
            }
        }
    }
}