package dev.valhalla.sinhalax.store

import android.content.Context
import dev.valhalla.sinhalax.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

/**
 * Minimal client for the KLIPY API (https://docs.klipy.com): GIFs, stickers and memes.
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
        /** Still pictures with captions (KLIPY "static memes"), sent as PNG/JPG. */
        MEMES("static-memes", "Memes", listOf("image/png", "image/jpeg", "image/webp", "image/gif"));

        /** GIFs and memes are rectangular pictures (wider grid cells); stickers are cut-outs. */
        val isWide: Boolean get() = this != STICKERS

        /** Only GIFs are cropped to fill their cell; stickers and memes are fitted so no caption is cut off. */
        val cropToFill: Boolean get() = this == GIFS

        /** Memes are still images; GIFs and stickers get moving previews. */
        val animates: Boolean get() = this != MEMES
    }

    data class Media(
        val kind: Kind,
        val id: String,
        val slug: String,
        val title: String,
        /** Small still image for the grid (used on Android 8 and older, and while loading). */
        val thumbUrl: String,
        /** Small animated WebP/GIF for the grid, so GIFs, stickers and memes move like in Gboard. */
        val animatedUrl: String? = null,
        /** Best URL per MIME type, e.g. "image/gif" -> url. */
        val files: Map<String, String>,
        /** Width / height of the grid image, for nicer layouts. */
        val aspect: Float,
    ) {
        val cacheKey: String get() = "klipy_${kind.path}_" + slug.ifBlank { id }.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60)

        /**
         * Formats to try when sending, best first, limited to what this item has.
         *
         * Animated stickers go as GIF first: WhatsApp turns any WebP that comes from a keyboard into
         * a *still* "Custom sticker" (first frame only), but plays a GIF as a moving GIF, the way
         * Z Sri and Gboard do. Still stickers keep WebP so their see-through background survives.
         */
        val sendOrder: List<String>
            get() {
                val base = if (kind == Kind.STICKERS && animatedUrl != null) {
                    listOf("image/gif") + kind.sendPreference.filter { it != "image/gif" }
                } else {
                    kind.sendPreference
                }
                return base.filter { it in files }
            }
    }

    data class Page(val items: List<Media>, val page: Int, val hasNext: Boolean)

    data class Category(val name: String, val query: String)

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

    // ------------------------------------------------------------------ files

    /** Downloads [url] into the app cache for sending. Old files are cleaned up (KLIPY content isn't to be kept). */
    suspend fun downloadForSending(context: Context, url: String, name: String, extension: String): File =
        withContext(Dispatchers.IO) {
            val target = sendFile(context, name, extension)
            StoreApi.download(url, target, maxBytes = 25L * 1024 * 1024)
            target
        }

    private fun sendFile(context: Context, name: String, extension: String): File {
        val dir = File(context.cacheDir, "stickers").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 10 * 60_000
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val safe = name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60).ifBlank { "media" }
        return File(dir, "$safe.$extension")
    }

    fun extensionFor(mime: String): String = when (mime) {
        "video/mp4" -> "mp4"
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
        // Memes carry caption text, so they start from "sm" to keep the words readable.
        val thumbSizes = if (kind == Kind.MEMES) listOf("sm", "md", "xs") else listOf("xs", "sm", "md")
        val thumb = thumbSizes.firstNotNullOfOrNull { size ->
            listOf("png", "webp", "jpg", "gif").firstNotNullOfOrNull { entry(size, it) }
        } ?: return null

        val formats = listOf("gif" to "image/gif", "webp" to "image/webp", "png" to "image/png", "jpg" to "image/jpeg")
        // Animated preview: small first (grids show many at once), WebP is lighter than GIF.
        val animated = if (!kind.animates) null else listOf("sm", "xs", "md").firstNotNullOfOrNull { size ->
            entry(size, "webp") ?: entry(size, "gif")
        }?.optString("url")

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
            animatedUrl = animated,
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

    // ------------------------------------------------------------------ recently sent

    /**
     * GIFs, stickers and memes the user sent recently (newest first), for the "Recent" chip.
     * Only KLIPY's links and titles are kept, never the files themselves.
     */
    object Recents {
        private const val MAX = 30
        private const val PREFS = "klipy_recents"

        fun all(context: Context): List<Media> = runCatching {
            val raw = prefs(context).getString("items", null) ?: return emptyList()
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::fromJson) }
        }.getOrDefault(emptyList())

        fun of(context: Context, kind: Kind): List<Media> = all(context).filter { it.kind == kind }

        fun add(context: Context, media: Media) {
            val list = (listOf(media) + all(context).filterNot { it.kind == media.kind && it.cacheKey == media.cacheKey }).take(MAX)
            val array = JSONArray()
            list.forEach { array.put(toJson(it)) }
            prefs(context).edit().putString("items", array.toString()).apply()
        }

        private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun toJson(m: Media) = JSONObject()
            .put("kind", m.kind.name).put("id", m.id).put("slug", m.slug).put("title", m.title)
            .put("thumb", m.thumbUrl).put("anim", m.animatedUrl ?: "").put("aspect", m.aspect.toDouble())
            .put("files", JSONObject().apply { m.files.forEach { (k, v) -> put(k, v) } })

        private fun fromJson(o: JSONObject): Media? {
            val kind = runCatching { Kind.valueOf(o.getString("kind")) }.getOrNull() ?: return null
            val filesJson = o.optJSONObject("files") ?: return null
            val files = LinkedHashMap<String, String>()
            filesJson.keys().forEach { k -> files[k] = filesJson.getString(k) }
            return Media(
                kind = kind,
                id = o.optString("id"),
                slug = o.optString("slug"),
                title = o.optString("title"),
                thumbUrl = o.optString("thumb"),
                animatedUrl = o.optString("anim").takeIf { it.startsWith("http") },
                files = files,
                aspect = o.optDouble("aspect", 1.0).toFloat(),
            )
        }
    }
}
