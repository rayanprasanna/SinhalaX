package dev.valhalla.sinhalax

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.valhalla.sinhalax.ui.AppRoot
import dev.valhalla.sinhalax.ui.theme.SinhalaXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SinhalaXApp
        setContent {
            SinhalaXTheme {
                AppRoot(app)
            }
        }
    }
}
