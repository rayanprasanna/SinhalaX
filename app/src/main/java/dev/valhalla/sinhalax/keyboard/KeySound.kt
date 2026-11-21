package dev.valhalla.sinhalax.keyboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Plays a downloaded key sound with low latency. */
class KeySoundPlayer(path: String) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    @Volatile
    private var ready = false
    private val soundId: Int = pool.load(path, 1)

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (id == soundId && status == 0) ready = true }
    }

    fun play(volume: Float) {
        if (ready) pool.play(soundId, volume, volume, 1, 0, 1f)
    }

    fun release() = pool.release()
}

/** Loads the sound pack file while it exists; released when it changes or the keyboard closes. */
@Composable
fun rememberKeySound(path: String): KeySoundPlayer? {
    val player = remember(path) {
        if (path.isNotEmpty() && File(path).exists()) runCatching { KeySoundPlayer(path) }.getOrNull() else null
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    return player
}

/** Decodes the background image at roughly screen width, off the main thread. */
@Composable
fun rememberBackgroundImage(path: String, blur: Int = 0): ImageBitmap? {
    val context = LocalContext.current
    val targetWidth = context.resources.displayMetrics.widthPixels
    val image by produceState<ImageBitmap?>(initialValue = null, path, blur) {
        value = if (path.isEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) { if (blur > 0) decodeBlurred(path, targetWidth, blur) else decode(path, targetWidth) }
        }
    }
    return image
}

/**
 * Soft blur that works on every Android version: decode the photo tiny, then scale it back up
 * with filtering. blur 1 = soft (1/12 size), 2 = strong (1/28).
 */
private fun decodeBlurred(path: String, targetWidth: Int, blur: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    val tinyWidth = (targetWidth / if (blur >= 2) 28 else 12).coerceAtLeast(8)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= tinyWidth) sample *= 2
    val small = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val scale = tinyWidth.toFloat() / small.width
    val tiny = Bitmap.createScaledBitmap(small, tinyWidth, (small.height * scale).toInt().coerceAtLeast(1), true)
    val outWidth = targetWidth / 3
    val out = Bitmap.createScaledBitmap(tiny, outWidth, (tiny.height * outWidth.toFloat() / tiny.width).toInt().coerceAtLeast(1), true)
    out.asImageBitmap()
}.getOrNull()

private fun decode(path: String, targetWidth: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}.getOrNull()
