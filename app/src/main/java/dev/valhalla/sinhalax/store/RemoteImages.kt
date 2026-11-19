package dev.valhalla.sinhalax.store

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Tiny image loader for store thumbnails: memory cache + disk cache, no library. */
object RemoteImages {

    private val memory = LruCache<String, ImageBitmap>(48)

    suspend fun load(context: Context, url: String, cacheKey: String): ImageBitmap? {
        memory.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File(context.cacheDir, "store_thumbs/$cacheKey.img")
                if (!file.exists()) StoreApi.download(url, file, maxBytes = 5L * 1024 * 1024)
                BitmapFactory.decodeFile(file.path)?.asImageBitmap()
            }.getOrNull()
        }?.also { memory.put(cacheKey, it) }
    }
}

@Composable
fun RemoteImage(
    url: String,
    cacheKey: String,
    placeholder: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    /** Fit (whole image visible, e.g. stickers) instead of crop. */
    fit: Boolean = false,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url, cacheKey) {
        value = RemoteImages.load(context.applicationContext, url, cacheKey)
    }
    Box(modifier.background(placeholder)) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** "#RRGGBB" → Color, with a fallback for bad input. */
fun parseHexColor(hex: String?, fallback: Color = Color(0xFF333333)): Color =
    runCatching { Color(hex!!.toColorInt()) }.getOrDefault(fallback)

// ------------------------------------------------------------------ animated (GIFs, stickers, memes)

/**
 * Decodes animated WebP / GIF previews with Android's ImageDecoder (Android 9+), scaled down to
 * grid size so dozens of moving tiles stay light. Files are cached on disk like still thumbnails.
 */
object AnimatedImages {
    @RequiresApi(28)
    suspend fun load(context: Context, url: String, cacheKey: String, maxPx: Int): Drawable? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File(context.cacheDir, "store_thumbs/${cacheKey}_anim.img")
                if (!file.exists()) StoreApi.download(url, file, maxBytes = 6L * 1024 * 1024)
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = minOf(1f, maxPx.toFloat() / maxOf(w, h).coerceAtLeast(1))
                    if (scale < 1f) {
                        decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                    }
                }
            }.getOrNull()
        }
    }
}

/**
 * Shows an animated preview when there is one (and the phone is Android 9+), otherwise the still
 * image. The still image is shown while the animation downloads, so tiles never look empty.
 */
@Composable
fun AnimatedRemoteImage(
    animatedUrl: String?,
    stillUrl: String,
    cacheKey: String,
    placeholder: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fit: Boolean = false,
) {
    if (animatedUrl == null || Build.VERSION.SDK_INT < 28) {
        RemoteImage(stillUrl, cacheKey, placeholder, contentDescription, modifier, fit)
        return
    }
    val context = LocalContext.current
    val maxPx = with(LocalDensity.current) { 180.dp.roundToPx() }
    val drawable by produceState<Drawable?>(initialValue = null, animatedUrl, cacheKey) {
        value = if (Build.VERSION.SDK_INT >= 28) {
            AnimatedImages.load(context.applicationContext, animatedUrl, cacheKey, maxPx)
        } else {
            null
        }
    }
    Box(modifier.background(placeholder)) {
        val d = drawable
        if (d == null) {
            RemoteImage(stillUrl, cacheKey, Color.Transparent, contentDescription, Modifier.fillMaxSize(), fit)
        } else {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = if (fit) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                        this.contentDescription = contentDescription
                    }
                },
                update = { view ->
                    if (view.drawable !== d) {
                        (view.drawable as? Animatable)?.stop()
                        view.setImageDrawable(d)
                        (d as? Animatable)?.start()
                    }
                },
                onRelease = { view ->
                    (view.drawable as? Animatable)?.stop()
                    view.setImageDrawable(null)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}