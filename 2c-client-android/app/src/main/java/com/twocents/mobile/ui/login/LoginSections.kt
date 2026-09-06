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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
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
import com.twocents.mobile.ui.common.AppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.StandardCharsets

private const val HOME_URL = "https://www.twocents.money"

@Composable
internal fun IntroSection(uriHandler: androidx.compose.ui.platform.UriHandler) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Welcome to the custom 2¢ client",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = buildAnnotatedString {
                append("This is a locally hosted client for ")
                pushStringAnnotation("url", HOME_URL)
                withStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = Gold,
                        textDecoration = TextDecoration.Underline,
                    ),
                ) {
                    append("twocents.money")
                }
                pop()
                append(". Create your account in the official twocents app, generate a backup code there, then paste that code below to sign in.")
            },
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.clickable { uriHandler.openUri(HOME_URL) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Emerald.copy(alpha = 0.04f),
                            Color.Transparent,
                        ),
                    ),
                )
                .border(
                    BorderStroke(1.dp, Emerald.copy(alpha = 0.15f)),
                    RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Emerald.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = Emerald.copy(alpha = 0.72f),
                        ),
                    ) {
                        append("Your backup code and keys stay private. ")
                    }
                    append("They are stored securely on this device and sent only to twocents' own API to authenticate you. No keys, account data, or other information is sent to any third party.")
                },
                color = Emerald.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }

        Text("Your backup code contains the credentials needed to restore your account on this device.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun StepRow(
    number: Int,
    text: String,
    link: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Gold.copy(alpha = 0.15f),
                            Gold.copy(alpha = 0.05f),
                        ),
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                color = Gold.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = text,
            color = if (link) Gold else Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textDecoration = if (link) TextDecoration.Underline else null,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
internal fun PasteSection(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(value, focused) {
        if (focused) {
            // Wait for both text layout and the IME inset before asking the
            // ancestor scroll container to expose the complete input.
            delay(80)
            bringIntoView.bringIntoView()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Backup code",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Paste",
                color = Gold,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable {
                        clipboard.getText()?.text?.let(onValueChange)
                    }
                    .padding(4.dp),
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.03f))
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    RoundedCornerShape(12.dp),
                )
                .bringIntoViewRequester(bringIntoView)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    onFocusChanged(state.isFocused)
                    if (state.isFocused) scope.launch { delay(180); bringIntoView.bringIntoView() }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = TextStyle(
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            maxLines = 8,
            cursorBrush = SolidColor(Gold),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "Paste your backup code here…",
                            color = Color.White.copy(alpha = 0.15f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            modifier = Modifier.offset(y = (-1).dp),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
internal fun PersistRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            Gold.copy(alpha = 0.4f)
        } else {
            Color.White.copy(alpha = 0.08f)
        },
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
        label = "persist-track",
    )
    val knobColor by animateColorAsState(
        targetValue = if (checked) Gold else Color.White.copy(alpha = 0.5f),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
        label = "persist-knob",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 16.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "persist-knob-offset",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(trackColor)
                .padding(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .offset(x = knobOffset)
                    .clip(CircleShape)
                    .background(knobColor),
            )
        }
        Text(
            text = "Keep me signed in",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
        )
    }
}

@Composable
internal fun LoginButton(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    Button(
        onClick = { AppHaptics.confirm(view); onClick() },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        border = if (enabled) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Color(0xFF0F0E0A),
            disabledContainerColor = Color.White.copy(alpha = 0.04f),
            disabledContentColor = Color.White.copy(alpha = 0.25f),
        ),
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF0F0E0A),
            )
            Text(
                text = "Verifying…",
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        } else {
            Text(
                text = "Login",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 3.dp)
                    .size(18.dp),
            )
        }
    }
}
