package dev.valhalla.sinhalax.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valhalla.sinhalax.SinhalaXApp
import dev.valhalla.sinhalax.data.KeyboardSettings
import dev.valhalla.sinhalax.keyboard.KeyboardPreview
import dev.valhalla.sinhalax.keyboard.KeyboardTheme
import dev.valhalla.sinhalax.keyboard.KeyboardThemes
import kotlinx.coroutines.launch

@Composable
fun ThemesScreen(app: SinhalaXApp, settings: KeyboardSettings) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Built-in", "DIY", "Backgrounds", "Sounds")
    // The DIY studio's unsaved theme, shown in the preview while that tab is open.
    var draft by remember { mutableStateOf<KeyboardSettings?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            ScreenTitle("Themes", "Changes show in the keyboard below.")
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                tabs.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                        icon = {}, // no check mark: four tabs need the room
                    ) {
                        // Shrinks long labels ("Backgrounds") to fit instead of cutting them off.
                        Text(
                            label,
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
                        )
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> BuiltInThemes(app, settings)
                1 -> ThemeStudio(app, settings, onDraft = { draft = it })
                2 -> BackgroundStore(app, settings)
                else -> SoundStore(app, settings)
            }
        }
        KeyboardPreview(if (tab == 1) draft ?: settings else settings)
    }
}

@Composable
private fun BuiltInThemes(app: SinhalaXApp, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(KeyboardThemes.all, key = { it.id }) { theme ->
            val selected = theme.id == settings.themeId && settings.backgroundPath.isEmpty()
            ThemeCard(theme, selected) {
                scope.launch {
                    // A built-in theme replaces any downloaded background.
                    if (settings.backgroundPath.isNotEmpty()) app.store.removeBackground()
                    app.settings.update { it.copy(themeId = theme.id) }
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(theme: KeyboardTheme, selected: Boolean, onClick: () -> Unit) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.border(2.dp, outline, RoundedCornerShape(18.dp)),
    ) {
        Column(Modifier.padding(10.dp)) {
            ThemeSwatch(
                theme,
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(theme.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(Icons.Filled.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            if (theme.rgbBorders) {
                Text("Animated RGB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ThemeSwatch(theme: KeyboardTheme, modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val bg = if (theme.background.size > 1) Brush.verticalGradient(theme.background) else SolidColor(theme.background.first())
            drawRect(bg)
            val rows = 3
            val cols = 6
            val gap = 4.dp.toPx()
            val keyW = (size.width - gap * (cols + 1)) / cols
            val keyH = (size.height - gap * (rows + 1)) / rows
            val radius = CornerRadius(4.dp.toPx())
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val topLeft = Offset(gap + c * (keyW + gap), gap + r * (keyH + gap))
                    val keySize = Size(keyW, keyH)
                    val isEnter = r == rows - 1 && c == cols - 1
                    drawRoundRect(if (isEnter) theme.accent else theme.key, topLeft, keySize, radius)
                    if (theme.rgbBorders) {
                        drawRoundRect(
                            Color.hsv((c * 55f + r * 25f) % 360f, 0.9f, 1f), topLeft, keySize, radius,
                            style = Stroke(1.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}
