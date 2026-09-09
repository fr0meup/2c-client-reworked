package com.twocents.mobile.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.twocents.mobile.ui.common.AppToastHost
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.SecureAuthStore
import com.twocents.mobile.ui.login.LoginScreen
import com.twocents.mobile.ui.shell.MainShell
import com.twocents.mobile.ui.startup.StartupScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TwoCentsApp(
    authStore: SecureAuthStore,
    rpcApi: RpcApi,
) {
    val scope = rememberCoroutineScope()
    var auth by remember { mutableStateOf<AuthState?>(null) }
    var checkingStoredAuth by remember { mutableStateOf(true) }
    var startupProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(auth?.userUuid) { com.twocents.mobile.ApiRateLimitNotice.reset() }

    LaunchedEffect(authStore) {
        startupProgress = 0.78f
        val storedAuth = authStore.load()
        startupProgress = 1f
        delay(160L)
        auth = storedAuth
        checkingStoredAuth = false
    }

    Box(Modifier.fillMaxSize()) {
        when {
            checkingStoredAuth -> StartupScreen(progress = startupProgress)
            auth == null -> LoginScreen(
                api = rpcApi,
                authStore = authStore,
                onAuthenticated = { auth = it },
            )
            else -> MainShell(
                auth = auth!!,
                rpcApi = rpcApi,
                onLogout = { scope.launch { authStore.clear(); auth = null } },
            )
        }
        AppToastHost(Modifier.align(Alignment.TopCenter))
        com.twocents.mobile.ui.common.RateLimitBanner()
    }
}
