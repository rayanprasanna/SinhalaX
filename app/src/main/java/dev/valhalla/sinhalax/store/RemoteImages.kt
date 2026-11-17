package dev.valhalla.sinhalax.store

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
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** "#RRGGBB" → Color, with a fallback for bad input. */
fun parseHexColor(hex: String?, fallback: Color = Color(0xFF333333)): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
