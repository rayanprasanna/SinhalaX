package dev.valhalla.sinhalax.store

import android.content.Context
import android.util.Base64
import dev.valhalla.sinhalax.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

/**
 * Minimal client for the KLIPY API (https://docs.klipy.com): GIFs, stickers, memes and AI Emoji.
 * Only called when the user opens the media panel in the keyboard or the Studio.
 *
 * KLIPY terms to keep in mind: show KLIPY attribution wherever content is shown, don't cache content
 * beyond search-result thumbnails, and don't present SinhalaX as a KLIPY product.
 */
object KlipyApi {

    private const val BASE = "https://api.klipy.com/api/v1"
    private val key: String get() = BuildConfig.KLIPY_API_KEY

    val isConfigured: Boolean get() = key.isNotBlank()

    /** Content kinds and the formats to send, best first, for each. */
    enum class Kind(val path: String, val label: String, val sendPreference: List<String>) {
        GIFS("gifs", "GIFs", listOf("image/gif", "image/webp", "image/png")),
        STICKERS("stickers", "Stickers", listOf("image/webp", "image/png", "image/gif")),
        MEMES("static-memes", "Memes", listOf("image/jpeg", "image/png", "image/webp", "image/gif")),
        AI_EMOJI("emojis", "AI Emoji", listOf("image/png", "image/webp", "image/gif")),
    }

    data class Media(
        val kind: Kind,
        val id: String,
        val slug: String,
        val title: String,
        /** Small image for the grid. */
        val thumbUrl: String,
        /** Best URL per MIME type, e.g. "image/gif" -> url. */
        val files: Map<String, String>,
        /** Width / height of the grid image, for nicer layouts. */
        val aspect: Float,
    ) {
        val cacheKey: String get() = "klipy_${kind.path}_" + slug.ifBlank { id }.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60)
    }

    data class Page(val items: List<Media>, val page: Int, val hasNext: Boolean)

    data class Category(val name: String, val query: String)

    sealed interface EmojiStatus {
        data object Processing : EmojiStatus
        data class Done(val bytes: ByteArray, val mimeType: String) : EmojiStatus
        data object Failed : EmojiStatus
    }

    // ------------------------------------------------------------------ lists

    suspend fun trending(context: Context, kind: Kind, page: Int = 1, perPage: Int = 30): Page =
        list(context, kind, "trending", page, perPage, emptyMap())

    suspend fun search(context: Context, kind: Kind, query: String, page: Int = 1, perPage: Int = 30): Page =
        list(context, kind, "search", page, perPage, mapOf("q" to query))

    suspend fun categories(kind: Kind): List<Category> {
        val json = get("/${kind.path}/categories?locale=${locale()}")
        val array = json.optJSONObject("data")?.optJSONArray("categories") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("category").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Category(name.replaceFirstChar { it.titlecase() }, o.optString("query", name))
        }
    }

    /** Tells KLIPY an item was sent (their analytics and ranking). Never throws. */
    suspend fun reportShare(context: Context, kind: Kind, slug: String, query: String?) {
        if (slug.isBlank()) return
        runCatching {
            postJson(
                "/${kind.path}/share/${encode(slug)}",
                JSONObject().put("customer_id", customerId(context)).put("q", query.orEmpty()),
            )
        }
    }

    // ------------------------------------------------------------------ AI Emoji generation

    /** Starts generating an AI Emoji from [prompt] (max 300 characters). Returns the generation id. */
    suspend fun generateEmoji(prompt: String): String {
        val json = postJson("/emojis/generate", JSONObject().put("prompt", prompt.take(300)))
        return json.optJSONObject("data")?.optString("id")?.takeIf { it.isNotBlank() }
            ?: throw IOException("KLIPY didn't return a generation id")
    }

    suspend fun emojiStatus(id: String): EmojiStatus {
        val data = get("/emojis/generated/${encode(id)}").optJSONObject("data") ?: return EmojiStatus.Failed
        return when (data.optString("status")) {
            "success" -> {
                val result = data.optJSONObject("result") ?: return EmojiStatus.Failed
                val bytes = Base64.decode(result.optString("base64_encoded").substringAfter("base64,"), Base64.DEFAULT)
                EmojiStatus.Done(bytes, result.optString("mime_type", "image/png").ifBlank { "image/png" })
            }
            "failed" -> EmojiStatus.Failed
            else -> EmojiStatus.Processing
        }
    }

    // ------------------------------------------------------------------ files

    /** Downloads [url] into the app cache for sending. Old files are cleaned up (KLIPY content isn't to be kept). */
    suspend fun downloadForSending(context: Context, url: String, name: String, extension: String): File =
        withContext(Dispatchers.IO) {
            val target = sendFile(context, name, extension)
            StoreApi.download(url, target, maxBytes = 12L * 1024 * 1024)
            target
        }

    /** Saves generated bytes (AI Emoji) for sending. */
    suspend fun saveForSending(context: Context, bytes: ByteArray, name: String, extension: String): File =
        withContext(Dispatchers.IO) { sendFile(context, name, extension).apply { writeBytes(bytes) } }

    private fun sendFile(context: Context, name: String, extension: String): File {
        val dir = File(context.cacheDir, "stickers").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 10 * 60_000
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val safe = name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60).ifBlank { "media" }
        return File(dir, "$safe.$extension")
    }

    fun extensionFor(mime: String): String = when (mime) {
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/jpeg" -> "jpg"
        else -> "png"
    }

    // ------------------------------------------------------------------ internals

    private suspend fun list(
        context: Context,
        kind: Kind,
        endpoint: String,
        page: Int,
        perPage: Int,
        extra: Map<String, String>,
    ): Page {
        val query = buildString {
            append('/').append(kind.path).append('/').append(endpoint)
            append("?page=").append(page)
            append("&per_page=").append(perPage.coerceIn(8, 50))
            append("&customer_id=").append(encode(customerId(context)))
            append("&locale=").append(locale())
            append("&content_filter=high")
            extra.forEach { (k, v) -> append('&').append(k).append('=').append(encode(v)) }
        }
        val data = get(query).optJSONObject("data") ?: return Page(emptyList(), page, false)
        val array = data.optJSONArray("data")
        val items = buildList {
            if (array != null) {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    if (o.optString("type") == "ad") continue // ad units need a WebView; not shown yet
                    parse(kind, o)?.let(::add)
                }
            }
        }
        return Page(items, data.optInt("current_page", page), data.optBoolean("has_next", false))
    }

    private fun parse(kind: Kind, o: JSONObject): Media? {
        val file = o.optJSONObject("file") ?: return null
        fun entry(size: String, format: String): JSONObject? =
            file.optJSONObject(size)?.optJSONObject(format)?.takeIf { it.optString("url").startsWith("http") }

        // Grid image: a small still image decodes fastest; fall back to whatever exists.
        val thumb = listOf("xs", "sm", "md").firstNotNullOfOrNull { size ->
            listOf("png", "webp", "jpg", "gif").firstNotNullOfOrNull { entry(size, it) }
        } ?: return null

        val formats = listOf("gif" to "image/gif", "webp" to "image/webp", "png" to "image/png", "jpg" to "image/jpeg")
        val files = LinkedHashMap<String, String>()
        for (size in listOf("md", "sm", "hd", "xs")) {
            for ((format, mime) in formats) {
                val url = entry(size, format)?.optString("url")
                if (url != null && mime !in files) files[mime] = url
            }
        }
        if (files.isEmpty()) return null
        val w = thumb.optInt("width", 1).coerceAtLeast(1)
        val h = thumb.optInt("height", 1).coerceAtLeast(1)
        return Media(
            kind = kind,
            id = o.opt("id")?.toString().orEmpty(),
            slug = o.optString("slug"),
            title = o.optString("title"),
            thumbUrl = thumb.optString("url"),
            files = files,
            aspect = (w.toFloat() / h).coerceIn(0.5f, 2.5f),
        )
    }

    private suspend fun get(pathAndQuery: String): JSONObject = withContext(Dispatchers.IO) {
        if (!isConfigured) throw IOException("KLIPY API key missing")
        val c = open("$BASE/$key$pathAndQuery")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("KLIPY returned $code")
            JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    private suspend fun postJson(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        if (!isConfigured) throw IOException("KLIPY API key missing")
        val c = open("$BASE/$key$path")
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) throw IOException("KLIPY returned $code")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "SinhalaX-Android")
        }

    /** A random ID for this install (not linked to the user or device). KLIPY uses it for recents and ranking. */
    private fun customerId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences("klipy", Context.MODE_PRIVATE)
        return prefs.getString("customer_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("customer_id", it).apply()
        }
    }

    private fun locale(): String {
        val l = Locale.getDefault()
        return "${l.language.ifBlank { "en" }}_${l.country.ifBlank { "LK" }}"
    }

    private fun encode(v: String): String = URLEncoder.encode(v, "UTF-8").replace("+", "%20")
}
