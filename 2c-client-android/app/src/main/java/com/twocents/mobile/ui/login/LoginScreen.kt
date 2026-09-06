package com.twocents.mobile.ui.login

import android.util.Base64
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.AuthState
import com.twocents.mobile.LoginCredentials
import com.twocents.mobile.RpcApi
import com.twocents.mobile.SecureAuthStore
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.theme.Emerald
import com.twocents.mobile.ui.theme.Gold
import com.twocents.mobile.ui.theme.Rose
import com.twocents.mobile.ui.common.TwoCentsLogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.StandardCharsets

private const val HOME_URL = "https://www.twocents.money"

@Composable
fun LoginScreen(
    api: RpcApi,
    authStore: SecureAuthStore,
    onAuthenticated: (AuthState) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var pasteValue by rememberSaveable { mutableStateOf("") }
    var persist by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var backupCodeFocused by rememberSaveable { mutableStateOf(false) }
    val formScroll = rememberScrollState()

    LaunchedEffect(backupCodeFocused, pasteValue) {
        if (backupCodeFocused) {
            // The IME changes maxValue asynchronously. Following it across a few
            // frames keeps the growing field visible without a permanent spacer.
            repeat(3) {
                kotlinx.coroutines.delay(if (it == 0) 90 else 55)
                formScroll.animateScrollTo(formScroll.maxValue)
            }
        }
    }

    fun submit() {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                val backup = parseBackupCode(pasteValue)
                val provisionalAuth = AuthState(
                    token = "",
                    userUuid = backup.userUuid,
                    secretKey = backup.secretKey,
                )
                val jwt = api.call(
                    method = "/v1/users/update/jwt",
                    params = JSONObject()
                        .put("ttlSeconds", 0)
                        .put("secret_key", backup.secretKey),
                    auth = provisionalAuth,
                ) as? JSONObject ?: throw LoginInputException("Couldn't create a login token from that backup code.")
                val token = jwt.optString("token").takeIf(String::isNotBlank)
                    ?: throw LoginInputException("The server didn't accept that backup code. Generate a new one and try again.")
                val auth = AuthState(
                    token = token,
                    userUuid = backup.userUuid,
                    secretKey = backup.secretKey,
                )

                withContext(Dispatchers.IO) {
                    // Keep these requests parallel, matching the web client's Promise.all.
                    listOf(
                        async {
                            api.call(
                                method = "/v2/auth/login",
                                params = JSONObject()
                                    .put("version", "web-v0.1.3")
                                    .put("secret_key", backup.secretKey),
                                auth = auth,
                            )
                        },
                        async {
                            api.call(
                                method = "/v1/users/blocked",
                                params = JSONObject().put("secret_key", backup.secretKey),
                                auth = auth,
                            )
                        },
                    ).awaitAll()
                }

                authStore.save(auth, persist)
                onAuthenticated(auth)
            } catch (cause: Throwable) {
                error = when (cause) {
                    is LoginInputException -> cause.userMessage
                    else -> "Couldn't sign in with that backup code. Check your connection or generate a new code and try again."
                }
            } finally {
                loading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(formScroll)
                .imePadding()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TwoCentsLogo(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            )

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.04f),
                                Color.White.copy(alpha = 0.01f),
                            ),
                        ),
                    )
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                IntroSection(uriHandler)
                PasteSection(
                    value = pasteValue,
                    onValueChange = {
                        pasteValue = it
                        error = null
                    },
                    onFocusChanged = { backupCodeFocused = it },
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = Rose,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
                PersistRow(
                    checked = persist,
                    onCheckedChange = { persist = it },
                )
                LoginButton(
                    enabled = pasteValue.trim().length > 10 && !loading,
                    loading = loading,
                    onClick = ::submit,
                )
            }

            Text(
                text = "Not affiliated with twocents.money. This is an independent community project.",
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable { uriHandler.openUri(HOME_URL) },
            )
        }
    }
}
