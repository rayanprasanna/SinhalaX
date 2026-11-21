package dev.valhalla.sinhalax.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import dev.valhalla.sinhalax.ui.screens.SetupCompleteDurationMs
import dev.valhalla.sinhalax.ui.screens.SetupCompleteScreen
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.valhalla.sinhalax.SinhalaXApp
import dev.valhalla.sinhalax.data.KeyboardSettings
import dev.valhalla.sinhalax.ui.screens.GuideScreen
import dev.valhalla.sinhalax.ui.screens.HomeScreen
import dev.valhalla.sinhalax.ui.screens.LearnedWordsScreen
import dev.valhalla.sinhalax.ui.screens.OnboardingScreen
import dev.valhalla.sinhalax.ui.screens.PrivacyScreen
import dev.valhalla.sinhalax.ui.screens.SettingsScreen
import dev.valhalla.sinhalax.ui.screens.ShortcutsScreen
import dev.valhalla.sinhalax.keyboard.AppRoutes
import dev.valhalla.sinhalax.ui.screens.StudioScreen
import dev.valhalla.sinhalax.ui.screens.ThemesScreen
import kotlin.time.Duration.Companion.milliseconds

/** A screen asked for from the keyboard; [id] changes on every request so the same one can repeat. */
data class OpenRequest(val route: String, val id: Int)

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    THEMES("Themes", Icons.Outlined.Palette),
    STUDIO("Studio", Icons.Outlined.AutoAwesome),
    SETTINGS("Settings", Icons.Outlined.Tune),
    GUIDE("Guide", Icons.AutoMirrored.Outlined.MenuBook),
}

@Composable
fun AppRoot(app: SinhalaXApp, openRequest: OpenRequest? = null) {
    val context = LocalContext.current
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = KeyboardSettings())
    var status by remember { mutableStateOf(ImeStatus.read(context)) }

    // Re-check after returning from system settings…
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = ImeStatus.read(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // …and after the keyboard picker dialog closes (the activity isn't paused for it).
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (focused) status = ImeStatus.read(context) }

    var skippedSetup by rememberSaveable { mutableStateOf(false) }
    // True while the user went through setup in this session; drives the "all set" animation.
    var cameFromSetup by rememberSaveable { mutableStateOf(false) }
    val needsSetup = !status.ready && !skippedSetup

    LaunchedEffect(needsSetup) {
        if (needsSetup) {
            cameFromSetup = true
        } else if (cameFromSetup) {
            delay(SetupCompleteDurationMs.milliseconds)
            cameFromSetup = false
        }
    }

    val stage = when {
        needsSetup -> Stage.SETUP
        cameFromSetup && status.ready -> Stage.FINISHING
        else -> Stage.MAIN
    }

    AnimatedContent(
        targetState = stage,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
        label = "setup",
    ) { current ->
        when (current) {
            Stage.SETUP -> OnboardingScreen(status = status, onSkip = {
                skippedSetup = true
                cameFromSetup = false
            })
            Stage.FINISHING -> {
                // Start loading the 100k-word dictionary now, so the first keystrokes are fast.
                LaunchedEffect(Unit) { app.prediction.ensureLoaded() }
                SetupCompleteScreen()
            }
            Stage.MAIN -> MainScreen(app, settings, status, openRequest)
        }
    }
}

private enum class Stage { SETUP, FINISHING, MAIN }

@Composable
private fun MainScreen(app: SinhalaXApp, settings: KeyboardSettings, status: ImeStatus, openRequest: OpenRequest?) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showLearned by rememberSaveable { mutableStateOf(false) }
    var showShortcuts by rememberSaveable { mutableStateOf(false) }
    var studioTab by rememberSaveable { mutableIntStateOf(0) }
    val subPage = showPrivacy || showLearned || showShortcuts

    // The keyboard's Tools hub can open a screen directly.
    LaunchedEffect(openRequest?.id) {
        val route = openRequest?.route ?: return@LaunchedEffect
        showPrivacy = false
        showLearned = false
        showShortcuts = false
        when (route) {
            AppRoutes.SETTINGS -> tab = Tab.SETTINGS
            AppRoutes.THEMES -> tab = Tab.THEMES
            AppRoutes.STUDIO -> { studioTab = 0; tab = Tab.STUDIO }
            AppRoutes.STUDIO_FM -> { studioTab = 1; tab = Tab.STUDIO }
            AppRoutes.GUIDE -> tab = Tab.GUIDE
            AppRoutes.PRIVACY -> showPrivacy = true
            AppRoutes.SHORTCUTS -> showShortcuts = true
            else -> tab = Tab.HOME
        }
    }

    BackHandler(enabled = subPage) {
        showPrivacy = false
        showLearned = false
        showShortcuts = false
    }
    BackHandler(enabled = !subPage && tab != Tab.HOME) { tab = Tab.HOME }

    Scaffold(
        bottomBar = {
            if (!subPage) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (showShortcuts) {
                ShortcutsScreen(app, settings, onBack = { showShortcuts = false })
            } else if (showPrivacy) {
                PrivacyScreen(onBack = { showPrivacy = false })
            } else if (showLearned) {
                LearnedWordsScreen(app, onBack = { showLearned = false })
            } else {
                when (tab) {
                    Tab.HOME -> HomeScreen(
                        status = status,
                        onOpenTab = { tab = it },
                        onOpenPrivacy = { showPrivacy = true },
                    )
                    Tab.THEMES -> ThemesScreen(app, settings)
                    Tab.STUDIO -> StudioScreen(startTab = studioTab, startRequest = openRequest?.id ?: 0)
                    Tab.SETTINGS -> SettingsScreen(
                        app, settings,
                        onOpenLearnedWords = { showLearned = true },
                        onOpenShortcuts = { showShortcuts = true },
                    )
                    Tab.GUIDE -> GuideScreen()
                }
            }
        }
    }
}
