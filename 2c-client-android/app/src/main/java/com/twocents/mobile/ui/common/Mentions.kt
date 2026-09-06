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
private val ExactUuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
private val MentionProfileCache = ConcurrentHashMap<String, ComposeAuthorProfile>()
private val MentionAliasCache = ConcurrentHashMap<String, String>()

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
            while (original <= labelRange.last) appendOriginal(original++)
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
    val profiles = remember { mutableStateMapOf<String, ComposeAuthorProfile>().apply { putAll(MentionProfileCache) } }
    val directory = remember { mutableStateMapOf<String, String>().apply { putAll(MentionAliasCache); putAll(aliases) } }
    LaunchedEffect(api, auth?.userUuid) {
        if (api == null || auth == null) return@LaunchedEffect
        runCatching { api.call("/v1/aliases/get", JSONObject(), auth) as? JSONObject }.getOrNull()
            ?.optJSONArray("aliases")?.let { rows ->
                for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { row ->
                    val user = row.optJSONObject("user")
                    val uuid = row.optString("for_uuid").ifBlank { user?.optString("uuid").orEmpty() }
                    val alias = row.optString("alias")
                    if (uuid.isNotBlank() && alias.isNotBlank()) {
                        directory[uuid] = alias; MentionAliasCache[uuid] = alias
                        user?.let {
                            val profile = ComposeAuthorProfile(uuid, it.optDouble("balance"), it.optInt("subscription_type", 1), it.optString("role").takeIf(String::isNotBlank), it.optString("gender").takeIf(String::isNotBlank), it.optInt("age").takeIf { _ -> it.has("age") && !it.isNull("age") }, it.optString("arena").takeIf(String::isNotBlank))
                            profiles[uuid] = profile; MentionProfileCache[uuid] = profile
                        }
                    }
                }
            }
    }
    val aliasMatches = directory.entries.asSequence()
        .filter { (uuid, alias) -> alias.contains(query, ignoreCase = true) || uuid.contains(query, ignoreCase = true) }
        .toList()
    val matches = if (ExactUuid.matches(query) && directory[query] == null) {
        listOf(java.util.AbstractMap.SimpleEntry(query, query.take(8))) + aliasMatches.filterNot { it.key == query }
    } else aliasMatches
    LaunchedEffect(contextKey) { onShown() }
    LaunchedEffect(matches.map { it.key }, api, auth) {
        if (api == null || auth == null) return@LaunchedEffect
        matches.filterNot { profiles.containsKey(it.key) }.map { entry -> async {
            runCatching {
                val root = api.call(
                    "/v2/users/get",
                    JSONObject().put("user_uuid", entry.key).put("posts_limit", 0).put("comments_limit", 0)
                        .put("voted_posts_limit", 0).put("pick_votes_limit", 0),
                    auth,
                ) as? JSONObject
                root?.optJSONObject("user")?.let { user ->
                    ComposeAuthorProfile(
                        uuid = entry.key,
                        balance = user.optDouble("balance", 0.0),
                        subscriptionType = user.optInt("subscription_type", 1),
                        role = user.optString("role").takeIf(String::isNotBlank),
                    )
                }
            }.getOrNull()?.let { entry.key to it }
        } }.awaitAll().filterNotNull().forEach { (uuid, profile) ->
            MentionProfileCache[uuid] = profile
            profiles[uuid] = profile
        }
    }
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
                if (query.length >= 36) "No user found" else "Type an alias or full UUID",
                color = Color.White.copy(alpha = .38f), fontSize = 11.5.sp,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            )
        }
        matches.forEach { (uuid, alias) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable {
                    val mentionLabel = profiles[uuid]?.let { NumberFormat.getIntegerInstance(Locale.US).format(it.balance.roundToLong()) } ?: alias
                    onSelect(uuid, mentionLabel)
                }
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val profile = profiles[uuid]
                if (profile != null) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.width(92.dp).height(27.dp).graphicsLayer {
                            scaleX = .85f
                            scaleY = .85f
                            transformOrigin = TransformOrigin(0f, .5f)
                        },
                        contentAlignment = Alignment.CenterStart,
                    ) { ComposeNetworthPill(profile, uuid, compact = true) }
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

internal suspend fun notifyMentions(
    api: RpcApi,
    auth: AuthState,
    text: String,
    postUuid: String,
    commentUuid: String? = null,
    contentType: String,
) = coroutineScope {
    val recipients = extractMentionUuids(text).filterNot { it == auth.userUuid }
    if (recipients.isEmpty()) return@coroutineScope
    suspend fun networth(uuid: String): String {
        val root = api.call(
            "/v2/users/get",
            JSONObject().put("user_uuid", uuid).put("posts_limit", 0).put("comments_limit", 0).put("voted_posts_limit", 0),
            auth,
        ) as? JSONObject
        val amount = root?.optJSONObject("user")?.optDouble("balance", 0.0) ?: 0.0
        return "$${"%,d".format(amount.roundToLong())}"
    }
    val sender = runCatching { networth(auth.userUuid) }.getOrDefault("Someone")
    recipients.map { recipient -> async {
        runCatching {
            val recipientNw = networth(recipient)
            val target = "https://www.twocents.money/post/$postUuid" + (commentUuid?.let { "?comment=$it" } ?: "")
            val dm = api.call("/v1/rooms/startDM", JSONObject().put("recipientUuid", recipient), auth) as? JSONObject
            val roomUuid = dm?.optJSONObject("room")?.optString("uuid").orEmpty()
            if (roomUuid.isNotBlank()) api.sendRoomMessage(roomUuid, "$sender mentioned $recipientNw in a $contentType.\nCheck it out here:\n$target", auth)
        }
    } }.awaitAll()
}
