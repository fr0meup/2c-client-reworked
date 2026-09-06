package com.twocents.mobile.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.twocents.mobile.ui.theme.NoFontPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

internal enum class AppToastType { Success, Error, Progress }

internal data class AppToastMessage(
    val id: Long,
    val type: AppToastType,
    val message: String,
)

/** Process-wide UI feedback bus so controllers and platform helpers share one presentation. */
internal object AppToast {
    private val ids = AtomicLong()
    val messages = MutableSharedFlow<AppToastMessage>(extraBufferCapacity = 32)
    val dismissals = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    var topOffsetDp by mutableFloatStateOf(58f)

    fun placeBelowVisibleHeader(offsetDp: Float) {
        topOffsetDp = offsetDp.coerceAtLeast(6f)
    }

    fun success(message: String, replaceId: Long? = null) = emit(AppToastType.Success, message, replaceId)
    fun error(message: String, replaceId: Long? = null) = emit(AppToastType.Error, message, replaceId)
    fun progress(message: String, replaceId: Long? = null): Long = emit(AppToastType.Progress, message, replaceId)
    fun dismiss(id: Long) { dismissals.tryEmit(id) }

    private fun emit(type: AppToastType, message: String, replaceId: Long? = null): Long {
        val clean = message.trim().ifBlank { if (type == AppToastType.Error) "Something went wrong" else "Done" }
        val id = replaceId ?: ids.incrementAndGet()
        messages.tryEmit(AppToastMessage(id, type, clean))
        return id
    }
}

/** Process-lived work for operations that must survive navigating away from their screen. */
internal object AppBackgroundTasks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)
}

/** Converts transport-oriented failures into text that is useful to a person. */
internal fun friendlyError(error: Throwable, fallback: String = "Something went wrong"): String {
    val raw = error.message.orEmpty()
    return when {
        error is IOException || raw.contains("network", true) || raw.contains("timeout", true) || raw.contains("unable to resolve host", true) ->
            "Couldn't connect. Check your internet connection and try again."
        raw.contains("unauthorized", true) || raw.contains("401") -> "Your session expired. Sign in again."
        raw.isBlank() -> fallback
        else -> raw.replaceFirstChar { it.uppercase() }
    }
}

private data class VisibleToast(val message: AppToastMessage, val visible: Boolean = true)

@Composable
internal fun AppToastHost(modifier: Modifier = Modifier) {
    // This must survive the recomposition triggered by adding the toast itself.
    // Recreating the list here made every message erase itself before its first frame.
    val toasts = remember { mutableStateListOf<VisibleToast>() }
    val hostScope = rememberCoroutineScope()
    val topOffset by animateDpAsState(AppToast.topOffsetDp.dp, label = "toast-header-offset")

    LaunchedEffect(Unit) {
        AppToast.messages.collect { incoming ->
            val existing = toasts.indexOfFirst { it.message.id == incoming.id }
            if (existing >= 0) toasts[existing] = VisibleToast(incoming) else toasts += VisibleToast(incoming)
            if (toasts.size > 3) toasts.removeAt(0)
            if (incoming.type != AppToastType.Progress) hostScope.launch {
                delay(3_500)
                val index = toasts.indexOfFirst { it.message.id == incoming.id }
                if (index >= 0 && toasts[index].message == incoming) toasts[index] = toasts[index].copy(visible = false)
                delay(220)
                toasts.removeAll { it.message == incoming }
            }
        }
    }
    LaunchedEffect(Unit) {
        AppToast.dismissals.collect { id -> toasts.removeAll { it.message.id == id } }
    }

    Column(
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, top = topOffset, end = 12.dp).zIndex(10_000f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        toasts.forEach { shown ->
            androidx.compose.runtime.key(shown.message.id) {
            AnimatedVisibility(
                visible = shown.visible,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                val error = shown.message.type == AppToastType.Error
                val progress = shown.message.type == AppToastType.Progress
                val accent = if (error) Color(0xFFFB7185) else Color(0xFF6EE7B7)
                val surface = if (error) Color(0xFF35171E) else Color(0xFF102C24)
                Row(
                    Modifier.widthIn(max = 310.dp).clip(RoundedCornerShape(12.dp))
                        .background(surface.copy(alpha = .96f)).border(.8.dp, accent.copy(alpha = .25f), RoundedCornerShape(12.dp))
                        .padding(start = 10.dp, top = 7.dp, end = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (progress) CircularProgressIndicator(Modifier.size(15.dp), color = accent, strokeWidth = 1.6.dp)
                    else Icon(if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle, null, tint = accent, modifier = Modifier.size(15.dp))
                    Text(
                        shown.message.message,
                        color = accent,
                        fontSize = 11.5.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = NoFontPadding,
                        modifier = Modifier.widthIn(max = 238.dp),
                    )
                    if (!progress) Box(
                        Modifier.size(20.dp).clip(CircleShape).clickable {
                            val index = toasts.indexOfFirst { it.message.id == shown.message.id }
                            if (index >= 0) toasts[index] = toasts[index].copy(visible = false)
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Close, "Dismiss", tint = Color.White.copy(alpha = .42f), modifier = Modifier.size(12.dp)) }
                }
            }
            }
        }
    }
}
