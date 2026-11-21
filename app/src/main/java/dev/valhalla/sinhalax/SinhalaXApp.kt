package dev.valhalla.sinhalax

import android.app.Application
import dev.valhalla.sinhalax.data.ClipboardRepository
import dev.valhalla.sinhalax.data.PredictionRepository
import dev.valhalla.sinhalax.data.SettingsRepository
import dev.valhalla.sinhalax.data.ShortcutRepository
import dev.valhalla.sinhalax.store.StoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SinhalaXApp : Application() {
    /** Lives as long as the process; used for background loading and saving. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val clipboard: ClipboardRepository by lazy { ClipboardRepository(this) }
    val shortcuts: ShortcutRepository by lazy { ShortcutRepository(this) }
    val prediction: PredictionRepository by lazy { PredictionRepository(this, appScope) }
    val store: StoreRepository by lazy { StoreRepository(this, settings) }

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
