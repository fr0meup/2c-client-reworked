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

internal data class BackupCredentials(
    val userUuid: String,
    val secretKey: String,
    val createdAtMillis: Long,
)

/** Decodes uuid:secret-key:timestamp, accepting legacy milliseconds and decimal Unix seconds. */
internal fun parseBackupCode(raw: String): BackupCredentials {
    val decoded = try {
        String(Base64.decode(raw.trim(), Base64.DEFAULT), StandardCharsets.UTF_8)
    } catch (_: Exception) {
        throw LoginInputException("That backup code isn't valid. Check that the complete code was pasted.")
    }
    val parts = decoded.split(':', limit = 3)
    val uuid = parts.getOrNull(0).orEmpty().trim()
    val secretKey = parts.getOrNull(1).orEmpty().trim()
    val timestamp = parts.getOrNull(2)?.trim()?.let { value ->
        // Keep legacy integers unchanged. New decimal timestamps are seconds; use decimal
        // arithmetic to avoid floating-point rounding and normalize to milliseconds.
        value.toLongOrNull() ?: if (value.matches(Regex("[0-9]+\\.[0-9]+"))) {
            runCatching {
                value.toBigDecimal().movePointRight(3)
                    .setScale(0, java.math.RoundingMode.DOWN).longValueExact()
            }.getOrNull()
        } else null
    }
    if (!uuid.matches(Regex("[0-9a-fA-F-]{36}")) || secretKey.isBlank() || timestamp == null) {
        throw LoginInputException("That backup code isn't valid. Generate a new one in the official twocents app.")
    }
    return BackupCredentials(uuid, secretKey, timestamp)
}

private fun firstNonBlank(json: JSONObject, vararg keys: String): String =
    keys.firstNotNullOfOrNull { key ->
        json.optString(key, "").takeIf { it.isNotBlank() }
    }.orEmpty()

private fun decodeTokenSubject(token: String): String {
    val parts = token.split(".")
    if (parts.size < 2) throw IllegalArgumentException("JWT payload missing")
    val decoded = Base64.decode(
        parts[1],
        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
    )
    val payload = JSONObject(String(decoded, StandardCharsets.UTF_8))
    return payload.optString("sub").takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("JWT subject missing")
}

internal class LoginInputException(
    val userMessage: String,
) : Exception(userMessage)
