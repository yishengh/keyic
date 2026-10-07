package com.yishenghuang.keyic

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.yishenghuang.keyic.core.model.ThemeMode
import com.yishenghuang.keyic.ui.KeyicRoot
import com.yishenghuang.keyic.ui.locale.AppLocaleController
import com.yishenghuang.keyic.ui.splash.BrandLaunchSplash
import com.yishenghuang.keyic.ui.theme.KeyicTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        val contentReady = AtomicBoolean(false)
        splashScreen.setKeepOnScreenCondition { !contentReady.get() }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as KeyicApp
            val settings by app.container.settingsRepository.settings
                .collectAsStateWithLifecycle(
                    initialValue = com.yishenghuang.keyic.core.model.AppSettings(),
                )
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val activity = this@MainActivity
            val localizedContext = remember(settings.appLanguage) {
                AppLocaleController.wrap(activity, settings.appLanguage)
            }
            val localizedConfig = remember(settings.appLanguage) {
                AppLocaleController.configuration(activity, settings.appLanguage)
            }
            SideEffect {
                contentReady.set(true)
                if (settings.allowScreenshots) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE,
                    )
                }
            }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig,
                LocalActivityResultRegistryOwner provides activity,
                LocalOnBackPressedDispatcherOwner provides activity,
                LocalSavedStateRegistryOwner provides activity,
                LocalViewModelStoreOwner provides (activity as ViewModelStoreOwner),
                LocalLifecycleOwner provides activity,
            ) {
                KeyicTheme(
                    darkTheme = darkTheme,
                    dynamicColor = settings.dynamicColor,
                    amoledBlack = settings.amoledBlack,
                ) {
                    IdleAutoLock(autoLockSeconds = settings.autoLockSeconds)
                    var showLaunchSplash by remember { mutableStateOf(true) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        KeyicRoot(container = app.container)
                        if (showLaunchSplash) {
                            BrandLaunchSplash(
                                onFinished = { showLaunchSplash = false },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        (application as KeyicApp).onVaultInteraction()
    }
}

@Composable
private fun IdleAutoLock(autoLockSeconds: Int) {
    val app = LocalContext.current.applicationContext as KeyicApp
    val session = app.container.vaultSession
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val unlocked by session.isUnlocked.collectAsStateWithLifecycle(initialValue = false)

    DisposableEffect(lifecycleOwner, autoLockSeconds) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Check before touching — otherwise background idle never trips.
                    if (session.shouldAutoLock(android.os.SystemClock.elapsedRealtime(), autoLockSeconds)) {
                        scope.launch { session.lock() }
                    } else {
                        session.touch()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Foreground idle: poll while resumed & unlocked.
    LaunchedEffect(autoLockSeconds, lifecycleState, unlocked) {
        if (!unlocked || autoLockSeconds <= 0) return@LaunchedEffect
        if (lifecycleState != Lifecycle.State.RESUMED) return@LaunchedEffect
        while (isActive) {
            delay(1_000L)
            if (session.shouldAutoLock(android.os.SystemClock.elapsedRealtime(), autoLockSeconds)) {
                session.lock()
                break
            }
        }
    }
}
