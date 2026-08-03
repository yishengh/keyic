package com.yishenghuang.keyic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yishenghuang.keyic.core.model.AppSettings
import com.yishenghuang.keyic.data.AppContainer
import com.yishenghuang.keyic.ui.lock.LockScreen
import com.yishenghuang.keyic.ui.lock.LockViewModel
import com.yishenghuang.keyic.ui.lock.LockViewModelFactory
import com.yishenghuang.keyic.ui.lock.SetupScreen
import com.yishenghuang.keyic.ui.nav.Route
import com.yishenghuang.keyic.ui.onboarding.OnboardingPager
import com.yishenghuang.keyic.ui.util.SecureClipboard
import com.yishenghuang.keyic.ui.vault.EntryDetailScreen
import com.yishenghuang.keyic.ui.vault.EntryEditScreen
import com.yishenghuang.keyic.ui.vault.HomeShell
import com.yishenghuang.keyic.ui.vault.VaultViewModel
import com.yishenghuang.keyic.ui.vault.VaultViewModelFactory
import kotlinx.coroutines.launch

@Composable
fun KeyicRoot(container: AppContainer) {
    val configured by container.session.isVaultConfigured
        .collectAsStateWithLifecycle(initialValue = false)
    val unlocked by container.session.isUnlocked
        .collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = remember { SecureClipboard(context, scope) }
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    LaunchedEffect(unlocked) {
        if (unlocked) container.purgeRecycleBinIfNeeded()
    }

    AnimatedContent(
        targetState = when {
            !configured -> AuthState.Setup
            !unlocked -> AuthState.Lock
            !settings.onboardingDone -> AuthState.Onboarding
            else -> AuthState.Main
        },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = Modifier.fillMaxSize(),
        label = "auth",
    ) { state ->
        when (state) {
            AuthState.Setup -> {
                val vm: LockViewModel = viewModel(
                    factory = LockViewModelFactory(
                        context.applicationContext as android.app.Application,
                        container,
                    ),
                )
                SetupScreen(viewModel = vm)
            }
            AuthState.Lock -> {
                val vm: LockViewModel = viewModel(
                    factory = LockViewModelFactory(
                        context.applicationContext as android.app.Application,
                        container,
                    ),
                )
                LockScreen(viewModel = vm)
            }
            AuthState.Onboarding -> {
                OnboardingPager(
                    onFinished = {
                        scope.launch {
                            container.settingsRepository.update { it.copy(onboardingDone = true) }
                        }
                    },
                )
            }
            AuthState.Main -> {
                MainNav(
                    container = container,
                    clipboard = clipboard,
                    clipboardClearSeconds = settings.clipboardClearSeconds,
                )
            }
        }
    }
}

private enum class AuthState { Setup, Lock, Onboarding, Main }

@Composable
private fun MainNav(
    container: AppContainer,
    clipboard: SecureClipboard,
    clipboardClearSeconds: Int,
) {
    val navController = rememberNavController()
    val vaultVm: VaultViewModel = viewModel(factory = VaultViewModelFactory(container))
    val snackbarHostState = remember { SnackbarHostState() }
    val message by vaultVm.message.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        val msg = message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = msg.text,
            actionLabel = msg.actionLabel,
            duration = if (msg.actionLabel != null) {
                SnackbarDuration.Long
            } else {
                SnackbarDuration.Short
            },
        )
        if (result == SnackbarResult.ActionPerformed) {
            msg.action?.invoke()
        }
        vaultVm.consumeMessage()
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
    ) { _ ->
        NavHost(
            navController = navController,
            startDestination = Route.Home.path,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Route.Home.path) {
                HomeShell(
                    vaultViewModel = vaultVm,
                    onOpenEntry = { id -> navController.navigate(Route.EntryDetail.create(id)) },
                    onEditEntry = { id -> navController.navigate(Route.EntryEdit.create(id)) },
                    onAddEntry = { navController.navigate(Route.EntryEdit.create(null)) },
                    onOpenGenerator = {},
                    clipboard = clipboard,
                    clipboardClearSeconds = clipboardClearSeconds,
                    container = container,
                )
            }
            composable(
                route = "entry/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                EntryDetailScreen(
                    entryId = id,
                    viewModel = vaultVm,
                    clipboard = clipboard,
                    clipboardClearSeconds = clipboardClearSeconds,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Route.EntryEdit.create(id)) },
                )
            }
            composable(
                route = "edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val raw = entry.arguments?.getString("id")
                val id = raw?.takeIf { it != "new" }
                EntryEditScreen(
                    entryId = id,
                    viewModel = vaultVm,
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }
        }
    }
}
