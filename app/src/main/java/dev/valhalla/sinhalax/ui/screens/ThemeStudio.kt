package dev.valhalla.sinhalax.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.valhalla.sinhalax.SinhalaXApp
import dev.valhalla.sinhalax.data.KeyboardSettings
import dev.valhalla.sinhalax.keyboard.CustomThemeSpec
import dev.valhalla.sinhalax.keyboard.KeyboardThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class StudioSection(val label: String) {
    BACKGROUND("Background"),
    KEYS("Keys"),
    TEXT("Text & accent"),
    PHOTO("Photo"),
}

/**
 * DIY theme studio (Themes → DIY): build "My theme" — background (solid or gradient), key colour
 * (solid or glass), key shape and outline, text and accent colours, and your own photo with dim
 * and blur. Every change shows in the keyboard preview below straight away ([onDraft]);
 * Apply saves it. The selected swatch gets an accent outline and a check.
 */
@Composable
fun ThemeStudio(app: SinhalaXApp, settings: KeyboardSettings, onDraft: (KeyboardSettings?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember(settings.customTheme) { CustomThemeSpec.decode(settings.customTheme) }
    // Start from "My theme" if there is one, else from the theme in use now.
    var spec by remember {
        mutableStateOf(saved ?: CustomThemeSpec.from(KeyboardThemes.byId(settings.themeId)))
    }
    var section by rememberSaveable { mutableStateOf(StudioSection.BACKGROUND) }
    var importing by remember { mutableStateOf(false) }

    // Live preview of the draft (no downloaded background in the way).
    LaunchedEffect(spec) {
        onDraft(settings.copy(themeId = CustomThemeSpec.ID, customTheme = spec.encode(), backgroundPath = ""))
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val path = withContext(Dispatchers.IO) { importPhoto(context, uri, keep = setOfNotNull(saved?.photoPath)) }
            importing = false
            if (path == null) {
                Toast.makeText(context, "Couldn't open that photo", Toast.LENGTH_SHORT).show()
            } else {
                // Glass keys look best on a photo.
                spec = spec.copy(photoPath = path, key = if (spec.key ushr 24 == 0xFFL) 0x33FFFFFF else spec.key)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StudioSection.entries.forEach { s ->
                FilterChip(selected = section == s, onClick = { section = s }, label = { Text(s.label) })
            }
        }
        Spacer(Modifier.height(8.dp))

        when (section) {
            StudioSection.BACKGROUND -> {
                Label("Colour or gradient")
                SwatchGrid(
                    items = CustomThemeSpec.BACKGROUNDS,
                    isSelected = { (a, b) -> a == spec.background && b == spec.background2 },
                    brush = { (a, b) -> if (b == 0L) Brush.linearGradient(listOf(Color(a), Color(a))) else Brush.verticalGradient(listOf(Color(a), Color(b))) },
                    onPick = { (a, b) -> spec = spec.copy(background = a, background2 = b) },
                )
                if (spec.photoPath.isNotEmpty()) {
                    Hint("Your photo covers the background. Remove it under Photo to see these colours.")
                }
            }
            StudioSection.KEYS -> {
                Label("Key colour")
                SwatchGrid(
                    items = CustomThemeSpec.KEYS,
                    isSelected = { it == spec.key },
                    brush = { Brush.linearGradient(listOf(Color(it), Color(it))) },
                    checker = true,
                    onPick = { spec = spec.copy(key = it) },
                )
                Hint("The see-through ones are glass keys: best on a photo or gradient.")
                Label("Key shape")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CustomThemeSpec.KeyShape.entries.forEach { shape ->
                        ShapeOption(shape, selected = spec.shape == shape, spec) { spec = spec.copy(shape = shape) }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Key outline", style = MaterialTheme.typography.titleSmall)
                        Text("A thin accent-coloured edge on every key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = spec.outline, onCheckedChange = { spec = spec.copy(outline = it) })
                }
            }
            StudioSection.TEXT -> {
                Label("Letters")
                SwatchGrid(
                    items = CustomThemeSpec.TEXTS,
                    isSelected = { it == spec.text },
                    brush = { Brush.linearGradient(listOf(Color(it), Color(it))) },
                    onPick = { spec = spec.copy(text = it) },
                )
                Label("Accent (enter key, highlights, Sinhala hints)")
                SwatchGrid(
                    items = CustomThemeSpec.ACCENTS,
                    isSelected = { it == spec.accent },
                    brush = { Brush.linearGradient(listOf(Color(it), Color(it))) },
                    onPick = { spec = spec.copy(accent = it) },
                )
                ContrastWarning(spec)
            }
            StudioSection.PHOTO -> {
                Label("Your photo behind the keys")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !importing,
                        onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(18.dp))
                        Text(if (importing) "  Opening…" else if (spec.photoPath.isEmpty()) "  Choose photo" else "  Change photo")
                    }
                    if (spec.photoPath.isNotEmpty()) {
                        OutlinedButton(onClick = { spec = spec.copy(photoPath = "") }) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                            Text("  Remove")
                        }
                    }
                }
                if (spec.photoPath.isNotEmpty()) {
                    Label("Darken ${(spec.photoDim * 100).toInt()}% (keeps letters readable)")
                    Slider(
                        value = spec.photoDim,
                        onValueChange = { spec = spec.copy(photoDim = it) },
                        valueRange = 0f..0.8f,
                    )
                    Label("Blur")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "None", 1 to "Soft", 2 to "Strong").forEach { (level, label) ->
                            FilterChip(
                                selected = spec.photoBlur == level,
                                onClick = { spec = spec.copy(photoBlur = level) },
                                label = { Text(label) },
                            )
                        }
                    }
                } else {
                    Hint("Pick any photo from your phone. It's copied into SinhalaX and never uploaded.")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                spec = saved ?: CustomThemeSpec.from(KeyboardThemes.byId(settings.themeId))
            }) { Text("Undo changes") }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val applied = spec
                    scope.launch {
                        // Like a built-in theme, "My theme" replaces a downloaded background.
                        if (settings.backgroundPath.isNotEmpty()) app.store.removeBackground()
                        app.settings.update { it.copy(themeId = CustomThemeSpec.ID, customTheme = applied.encode()) }
                        withContext(Dispatchers.IO) { cleanupPhotos(context, keep = setOf(applied.photoPath)) }
                        Toast.makeText(context, "My theme applied", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                Text("  Apply")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Swatches, 7 per row, with an accent outline + check on the chosen one. */
@Composable
private fun <T> SwatchGrid(
    items: List<T>,
    isSelected: (T) -> Boolean,
    brush: (T) -> Brush,
    onPick: (T) -> Unit,
    checker: Boolean = false,
    size: Dp = 40.dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item ->
                    val selected = isSelected(item)
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .size(size)
                            .clip(shape)
                            // Grey + white backing so translucent "glass" swatches are visible.
                            .then(if (checker) Modifier.background(Brush.linearGradient(listOf(Color(0xFF8A8F98), Color(0xFFE6E8EC)))) else Modifier)
                            .background(brush(item))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = shape,
                            )
                            .clickable { onPick(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A mini key in each shape, drawn in the current colours. */
@Composable
private fun ShapeOption(shape: CustomThemeSpec.KeyShape, selected: Boolean, spec: CustomThemeSpec, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                if (selected) 2.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(52.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(spec.background)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 30.dp, height = 28.dp)
                    .clip(RoundedCornerShape((shape.radiusDp * 0.7f).dp))
                    .background(Color(spec.key))
                    .then(
                        if (spec.outline) Modifier.border(1.dp, Color(spec.accent).copy(alpha = 0.6f), RoundedCornerShape((shape.radiusDp * 0.7f).dp)) else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("අ", color = Color(spec.text), fontWeight = FontWeight.Bold)
            }
        }
        Text(shape.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Warns when letters and keys are too close in brightness to read. */
@Composable
private fun ContrastWarning(spec: CustomThemeSpec) {
    val bg = Color(spec.background)
    val key = Color(spec.key)
    // Key colour over the background (glass keys mostly show the background).
    val keyLum = key.luminance() * key.alpha + bg.luminance() * (1 - key.alpha)
    val textLum = Color(spec.text).luminance()
    val ratio = (maxOf(keyLum, textLum) + 0.05f) / (minOf(keyLum, textLum) + 0.05f)
    if (ratio < 3f && spec.photoPath.isEmpty()) {
        Text(
            "⚠ Letters may be hard to read on these keys. Try a lighter or darker letter colour.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

// ------------------------------------------------------------------ photo files

private fun themeDir(context: Context) = File(context.filesDir, "theme").apply { mkdirs() }

/**
 * Copies the picked photo into the app (max 1600 px wide, JPEG) so the keyboard can read it later.
 * Older unused copies are removed, except those in [keep] (the applied theme's photo).
 */
private fun importPhoto(context: Context, uri: Uri, keep: Set<String>): String? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1600) sample *= 2
    val bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val target = File(themeDir(context), "custom_${System.currentTimeMillis()}.jpg")
    target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    cleanupPhotos(context, keep + target.path)
    target.path
}.getOrNull()

private fun cleanupPhotos(context: Context, keep: Set<String>) {
    themeDir(context).listFiles()?.forEach { f ->
        if (f.name.startsWith("custom_") && f.path !in keep) f.delete()
    }
}
