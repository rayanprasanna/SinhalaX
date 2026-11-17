package dev.valhalla.sinhalax

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the last crash on the phone (never uploaded) so the Home screen can show it.
 * When the keyboard process crashes, Android switches to the default keyboard, so this is
 * the quickest way to see what went wrong without a cable and logcat.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(appContext.filesDir, FILE).writeText(
                    "Time: $time\nThread: ${thread.name}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}, " +
                        "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n\n$trace",
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
