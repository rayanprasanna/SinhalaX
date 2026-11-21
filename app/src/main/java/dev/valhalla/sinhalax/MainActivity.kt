package dev.valhalla.sinhalax

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.valhalla.sinhalax.ui.AppRoot
import dev.valhalla.sinhalax.ui.OpenRequest
import dev.valhalla.sinhalax.ui.theme.SinhalaXTheme

class MainActivity : ComponentActivity() {

    /** A screen the keyboard asked for (Tools hub → Settings, Shortcuts, Themes…), or null. */
    private val openRequest = mutableStateOf<OpenRequest?>(null)
    private var requestCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readOpenRequest(intent)
        val app = application as SinhalaXApp
        setContent {
            SinhalaXTheme {
                AppRoot(app, openRequest.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readOpenRequest(intent)
    }

    private fun readOpenRequest(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_OPEN) ?: return
        openRequest.value = OpenRequest(route, ++requestCount)
    }

    companion object {
        /** Extra with an AppRoutes value: which screen to open. */
        const val EXTRA_OPEN = "dev.valhalla.sinhalax.OPEN"
    }
}
