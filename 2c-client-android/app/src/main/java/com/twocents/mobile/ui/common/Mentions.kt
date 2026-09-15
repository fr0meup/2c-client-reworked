package com.twocents.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import kotlin.math.roundToLong
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val MentionLink = Regex("\\[[^]]*]\\(/user/([0-9a-fA-F-]{32,36})\\)")

internal data class MentionContext(val start: Int, val query: String)

internal fun mentionContext(text: String, cursor: Int = text.length): MentionContext? {
    val safeCursor = cursor.coerceIn(0, text.length)
    val prefix = text.substring(0, safeCursor)
    val at = prefix.lastIndexOf('@')
    if (at < 0 || (at > 0 && !prefix[at - 1].isWhitespace())) return null
    val query = prefix.substring(at + 1)
    if (query.any(Char::isWhitespace)) return null
    return MentionContext(at, query)
}

internal fun mentionMarkup(alias: String, uuid: String) = "[@${alias.trim()}](/user/$uuid) "

internal fun extractMentionUuids(text: String): Set<String> =
    MentionLink.findAll(text).map { it.groupValues[1] }.toSet()

/** Keeps UUID metadata in the submitted text while exposing only the readable @label in editors. */
internal object MentionVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = transformMentionMarkup(text)
}

internal fun transformMentionMarkup(source: AnnotatedString): TransformedText {
    val raw = source.text
    val matches = MentionLink.findAll(raw).toList()
    if (matches.isEmpty()) return TransformedText(source, OffsetMapping.Identity)
    val originalToVisual = IntArray(raw.length + 1)
    val visualToOriginal = mutableListOf<Int>()
    val visual = buildAnnotatedString {
        var original = 0
        fun appendOriginal(index: Int) {
            originalToVisual[index] = length
            visualToOriginal += index
            append(source.subSequence(index, index + 1))
        }
        matches.forEach { match ->
            while (original < match.range.first) appendOriginal(original++)
            val labelRange = match.groups[0]!!.range.let { whole ->
                val labelStart = whole.first + 1
                labelStart until (labelStart + raw.substring(labelStart).substringBefore(']').length)
            }
            while (original < labelRange.first) originalToVisual[original++] = length
            val labelStart = length
            while (original <= labelRange.last) appendOriginal(original++)
            addStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFC8A44D), fontWeight = FontWeight.SemiBold,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.None), labelStart, length)
            while (original <= match.range.last) originalToVisual[original++] = length
        }
        while (original < raw.length) appendOriginal(original++)
    }
    originalToVisual[raw.length] = visual.length
    visualToOriginal += raw.length
    return TransformedText(
        visual,
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = originalToVisual[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int) = visualToOriginal[offset.coerceIn(0, visualToOriginal.lastIndex)]
        },
    )
}

@Composable
internal fun MentionSuggestions(
    context: MentionContext?,
    aliases: Map<String, String>,
    onSelect: (uuid: String, alias: String) -> Unit,
    onShown: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    placeAbove: Boolean = false,
    gap: Dp = 6.dp,
    api: RpcApi? = null,
    auth: AuthState? = null,
) {
    val query = context?.query ?: return
    val contextKey = "${context.start}:$query"
    val profiles = remember(auth?.userUuid) { mutableStateMapOf<String, ComposeAuthorProfile>() }
    val directory = remember(auth?.userUuid) { mutableStateMapOf<String, String>() }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    LaunchedEffect(api, auth?.userUuid, query) {
        if (api == null || auth == null) return@LaunchedEffect
        loading = true
        loadError = false
        directory.clear()
        try {
            kotlinx.coroutines.delay(200)
            MutualMentionDirectory.get(api, auth, query).forEach { entry ->
                directory[entry.uuid] = entry.alias
                profiles[entry.uuid] = ComposeAuthorProfile(entry.uuid, entry.user.optDouble("balance", 0.0), entry.user.optInt("subscription_type", 0))
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel
        } catch (_: Exception) { loadError = true
        } finally { loading = false }
    }
    val matches = directory.entries.toList()
    LaunchedEffect(contextKey) { onShown() }
    val density = LocalDensity.current
    val offsetPx = with(density) { IntOffset(offset.x.roundToPx(), offset.y.roundToPx()) }
    val gapPx = with(density) { gap.roundToPx() }
    val positionProvider = remember(offsetPx, placeAbove, gapPx) {
        object : PopupPositionProvider {
          override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            val desiredX = anchorBounds.left + offsetPx.x
            val cursorY = anchorBounds.top + offsetPx.y
            val desiredY = if (placeAbove) cursorY - popupContentSize.height - gapPx else cursorY + gapPx
            return IntOffset(
                desiredX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                desiredY.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
            )
          }
        }
    }
    // A local popup position provider keeps the menu attached to the active line.
    // Material DropdownMenu deliberately repositions tall menus against the window,
    // which made the compose picker jump to the sheet header.
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true, dismissOnBackPress = true),
    ) {
        Column(
            modifier.widthIn(min = 286.dp, max = 328.dp).heightIn(max = 174.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color(0xFF141410))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp))
                .padding(4.dp).verticalScroll(rememberScrollState()),
        ) {
        if (matches.isEmpty()) {
            Text(
                if (loading) "Loading mutual follows…" else if (loadError) "Couldn't load mutual follows. Try again." else "No matching mutual follows",
                color = Color.White.copy(alpha = .38f), fontSize = 11.5.sp,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            )
        }
        matches.forEach { (uuid, alias) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable {
                    onSelect(uuid, alias)
                }
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                // This follows the rendered pill width, so long net-worth values
                // keep the nickname aligned just beyond the pill's trailing edge.
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val profile = profiles[uuid]
                if (profile != null) {
                    ComposeNetworthPill(profile, uuid, compact = true)
                } else {
                    androidx.compose.foundation.layout.Box(
                        Modifier.width(92.dp).height(25.dp).clip(RoundedCornerShape(13.dp))
                            .background(Color.White.copy(alpha = .055f)),
                    )
                }
                Text(alias, color = Color(0xFFC8A44D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        }
    }
}
