package dev.valhalla.sinhalax.store

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.valhalla.sinhalax.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Sinhala ⇄ English translation, online (Google's on-device ML Kit translator has no Sinhala).
 * Only text typed into the keyboard's translate box is sent, and only while that box is open.
 *
 * Two providers; keys come from local.properties, never from source control:
 *  - **Microsoft Translator** (AZURE_TRANSLATOR_KEY + AZURE_TRANSLATOR_REGION): free tier of
 *    2 million characters a month. Used when its key is set.
 *  - **Google Cloud Translation** (GOOGLE_TRANSLATE_API_KEY): 500,000 free characters a month.
 *    Restrict the key to "Android apps" with this package name and your signing SHA-1s; the
 *    X-Android-* headers below make that restriction work.
 */
object TranslateApi {

    enum class Provider(val attribution: String) {
        MICROSOFT("Translated by Microsoft"),
        GOOGLE("Translated by Google"),
    }

    private const val GOOGLE_ENDPOINT = "https://translation.googleapis.com/language/translate/v2"
    private const val MICROSOFT_ENDPOINT = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0"

    private val googleKey: String get() = BuildConfig.GOOGLE_TRANSLATE_API_KEY
    private val microsoftKey: String get() = BuildConfig.AZURE_TRANSLATOR_KEY
    private val microsoftRegion: String get() = BuildConfig.AZURE_TRANSLATOR_REGION

    /** Microsoft when its key is set (bigger free tier), otherwise Google, otherwise none. */
    val provider: Provider?
        get() = when {
            microsoftKey.isNotBlank() -> Provider.MICROSOFT
            googleKey.isNotBlank() -> Provider.GOOGLE
            else -> null
        }

    val isConfigured: Boolean get() = provider != null

    /** Language codes Cloud Translation uses. */
    const val SINHALA = "si"
    const val ENGLISH = "en"

    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 64
    }

    @Volatile private var certSha1: String? = null

    /** Translates [text] from [source] to [target]. Throws IOException when offline or refused. */
    suspend fun translate(context: Context, text: String, source: String, target: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val cacheKey = "$source>$target:$trimmed"
        synchronized(cache) { cache[cacheKey] }?.let { return it }
        val result = when (provider) {
            Provider.MICROSOFT -> microsoft(trimmed, source, target)
            Provider.GOOGLE -> google(context, trimmed, source, target)
            null -> throw IOException("Translation key missing")
        }
        synchronized(cache) { cache[cacheKey] = result }
        return result
    }

    private suspend fun microsoft(text: String, source: String, target: String): String = withContext(Dispatchers.IO) {
        val c = URL("$MICROSOFT_ENDPOINT&from=$source&to=$target").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Ocp-Apim-Subscription-Key", microsoftKey)
            if (microsoftRegion.isNotBlank()) c.setRequestProperty("Ocp-Apim-Subscription-Region", microsoftRegion)
            val body = JSONArray().put(JSONObject().put("Text", text)).toString()
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) throw IOException("Translation failed ($code)")
            val json = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            json.getJSONObject(0).getJSONArray("translations").getJSONObject(0).getString("text")
        } finally {
            c.disconnect()
        }
    }

    private suspend fun google(context: Context, trimmed: String, source: String, target: String): String =
        withContext(Dispatchers.IO) {
            val c = URL("$GOOGLE_ENDPOINT?key=${enc(googleKey)}").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 10_000
                c.readTimeout = 15_000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                // Needed when the key is restricted to this Android app.
                c.setRequestProperty("X-Android-Package", context.packageName)
                signingSha1(context)?.let { c.setRequestProperty("X-Android-Cert", it) }
                val body = "q=${enc(trimmed)}&source=$source&target=$target&format=text"
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                if (code !in 200..299) throw IOException("Translation failed ($code)")
                val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                json.getJSONObject("data").getJSONArray("translations").getJSONObject(0).getString("translatedText")
            } finally {
                c.disconnect()
            }
        }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    /** SHA-1 of the app's signing certificate, as Google's key restriction expects (hex, no colons). */
    @Suppress("DEPRECATION")
    private fun signingSha1(context: Context): String? {
        certSha1?.let { return it }
        val value = runCatching {
            val pm = context.packageManager
            val signature = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            } ?: return@runCatching null
            MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
                .joinToString("") { "%02X".format(it) }
        }.getOrNull()
        certSha1 = value
        return value
    }
}
