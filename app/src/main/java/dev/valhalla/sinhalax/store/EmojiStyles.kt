package dev.valhalla.sinhalax.store

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

/**
 * Emoji styles for the keyboard's emoji panel, from free, openly licensed sets only.
 * (Apple, Samsung, Facebook and Microsoft emoji are copyrighted and can't be shipped.)
 *
 * Only how emoji LOOK in the keyboard changes: they still go into chats as normal emoji text,
 * so the other person sees their own phone's style.
 *
 * Images come from public CDNs (jsDelivr, with unpkg / GitHub as backups), are cached in the
 * app's own storage and can be downloaded in one go for offline use (~3 MB per set).
 * File names were checked against each set's data for all 1,136 emoji in the panel.
 */
enum class EmojiStyle(val id: String, val label: String, val credit: String) {
    SYSTEM("system", "Phone default", ""),
    GOOGLE("google", "Google (Noto)", "Noto Emoji by Google · Apache License 2.0"),
    TWEMOJI("twemoji", "Twemoji", "Twemoji by Twitter / jdecked · CC BY 4.0"),
    OPENMOJI("openmoji", "OpenMoji", "OpenMoji (HfG Schwäbisch Gmünd) · CC BY-SA 4.0"),
    ;

    /** Where this emoji's image can be downloaded, best first. */
    fun urls(emoji: String): List<String> {
        val cps = emoji.codePoints().toArray().map { "%04x".format(it) }
        return when (this) {
            SYSTEM -> emptyList()
            GOOGLE, TWEMOJI -> {
                // emoji-datasource: fully-qualified code points, lower case, joined by "-".
                val name = cps.joinToString("-") + ".png"
                val (pkg, dir) = if (this == GOOGLE) "emoji-datasource-google" to "google" else "emoji-datasource-twitter" to "twitter"
                listOf(
                    "https://cdn.jsdelivr.net/npm/$pkg@$DATASOURCE_VERSION/img/$dir/64/$name",
                    "https://unpkg.com/$pkg@$DATASOURCE_VERSION/img/$dir/64/$name",
                )
            }
            OPENMOJI -> {
                // OpenMoji: upper case; a trailing FE0F is dropped except in ZWJ sequences.
                var code = cps.joinToString("-").uppercase()
                if (code.endsWith("-FE0F") && "200D" !in code) code = code.removeSuffix("-FE0F")
                listOf(
                    "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@$OPENMOJI_VERSION/color/72x72/$code.png",
                    "https://raw.githubusercontent.com/hfg-gmuend/openmoji/$OPENMOJI_VERSION/color/72x72/$code.png",
                )
            }
        }
    }

    /** Local file name (stable, safe). */
    fun fileName(emoji: String): String = emoji.codePoints().toArray().joinToString("-") { "%x".format(it) } + ".png"

    companion object {
        const val DATASOURCE_VERSION = "16.0.0"
        const val OPENMOJI_VERSION = "17.0.0"

        fun byId(id: String): EmojiStyle = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** The style the emoji panel draws with (provided by the keyboard root). */
val LocalEmojiStyle = staticCompositionLocalOf { EmojiStyle.SYSTEM }

/** Loads emoji images: memory → app storage → download (and saves it for next time). */
object EmojiImages {

    private val memory = LruCache<String, ImageBitmap>(500)

    /** Emoji that failed this session (offline, missing): don't retry on every scroll. */
    private val failed = Collections.synchronizedSet(HashSet<String>())

    fun dir(context: Context, style: EmojiStyle) = File(context.filesDir, "emoji/${style.id}")

    fun cached(style: EmojiStyle, emoji: String): ImageBitmap? = memory.get(style.id + emoji)

    suspend fun load(context: Context, style: EmojiStyle, emoji: String): ImageBitmap? {
        if (style == EmojiStyle.SYSTEM) return null
        val key = style.id + emoji
        memory.get(key)?.let { return it }
        if (key in failed) return null
        return withContext(Dispatchers.IO) {
            val file = File(dir(context, style), style.fileName(emoji))
            if (!file.exists() && !fetch(style, emoji, file)) {
                failed += key
                return@withContext null
            }
            val bitmap = runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
            if (bitmap == null) {
                file.delete()
                failed += key
            } else {
                memory.put(key, bitmap)
            }
            bitmap
        }
    }

    /** Downloads one image, trying each mirror. */
    internal suspend fun fetch(style: EmojiStyle, emoji: String, target: File): Boolean {
        for (url in style.urls(emoji)) {
            val ok = runCatching { StoreApi.download(url, target, maxBytes = 256L * 1024) }.isSuccess
            if (ok && target.length() > 0) return true
        }
        return false
    }

    /** How many images of [style] are saved on the phone. */
    fun savedCount(context: Context, style: EmojiStyle): Int =
        dir(context, style).list()?.count { it.endsWith(".png") } ?: 0

    fun delete(context: Context, style: EmojiStyle) {
        dir(context, style).deleteRecursively()
        memory.evictAll()
        failed.clear()
    }

    /** Lets a retry happen (e.g. after going online or a fresh download). */
    fun clearFailures() = failed.clear()
}

/** "Download for offline": fetches a whole set in the background, with progress. */
object EmojiPacks {

    data class Progress(val done: Int, val total: Int, val running: Boolean, val failed: Int = 0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<EmojiStyle, Job>()
    private val _progress = MutableStateFlow<Map<EmojiStyle, Progress>>(emptyMap())
    val progress: StateFlow<Map<EmojiStyle, Progress>> = _progress

    fun isRunning(style: EmojiStyle) = jobs[style]?.isActive == true

    fun download(context: Context, style: EmojiStyle, emojis: List<String>) {
        if (style == EmojiStyle.SYSTEM || isRunning(style)) return
        val app = context.applicationContext
        val list = emojis.distinct()
        EmojiImages.clearFailures()
        jobs[style] = scope.launch {
            val dir = EmojiImages.dir(app, style)
            var done = 0
            var failed = 0
            fun report(running: Boolean) = _progress.update { it + (style to Progress(done, list.size, running, failed)) }
            report(true)
            val gate = Semaphore(6)
            list.chunked(48).forEach { chunk ->
                chunk.map { emoji ->
                    async {
                        gate.withPermit {
                            val file = File(dir, style.fileName(emoji))
                            val ok = file.exists() || EmojiImages.fetch(style, emoji, file)
                            synchronized(this@EmojiPacks) {
                                done++
                                if (!ok) failed++
                            }
                        }
                    }
                }.awaitAll()
                report(true)
            }
            report(false)
        }
    }

    fun cancel(style: EmojiStyle) {
        jobs.remove(style)?.cancel()
        _progress.update { it - style }
    }
}
