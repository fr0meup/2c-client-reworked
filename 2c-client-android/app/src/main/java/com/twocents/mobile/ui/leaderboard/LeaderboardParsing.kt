package com.twocents.mobile.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.feed.FeedAuthor
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.feed.FeedPostMeta
import com.twocents.mobile.ui.feed.FeedUserMetaPill
import com.twocents.mobile.ui.feed.AnimatedDropdownPanel
import com.twocents.mobile.ui.feed.HeaderActionButton
import com.twocents.mobile.ui.feed.PROFILE_ICON_RES
import com.twocents.mobile.ui.feed.PROFILE_ICON_SELECTED_RES
import com.twocents.mobile.ui.feed.ChevronIcon
import com.twocents.mobile.ui.common.PullToRefreshContainer
import com.twocents.mobile.ui.common.RefreshProgressBar
import com.twocents.mobile.ui.common.rememberPullToRefreshState
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.common.pressScale
import com.twocents.mobile.ui.common.rememberPressScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val Gold = Color(0xFFC8A44D)
private val NoPadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

internal fun parseBoards(root: JSONObject?): List<LeaderboardBoard> {
    val rows = root?.optJSONArray("leaderboards") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val item = rows.optJSONObject(index) ?: continue
            val name = item.optString("name").takeIf(String::isNotBlank) ?: continue
            add(
                LeaderboardBoard(
                    uuid = item.optString("uuid"),
                    apiName = name,
                    label = boardLabel(name),
                    description = item.optString("description"),
                    pointsFormat = item.optString("points_format", "number"),
                    embedded = parseEntries(item.optJSONArray("leaderboard")),
                ),
            )
        }
    }.sortedBy { boardOrder(it.apiName) }
}

internal fun parseEntries(rows: JSONArray?): List<LeaderboardEntry> = if (rows == null) emptyList() else buildList {
    for (index in 0 until rows.length()) {
        val item = rows.optJSONObject(index) ?: continue
        val uuid = item.optString("uuid").takeIf(String::isNotBlank) ?: continue
        add(
            LeaderboardEntry(
                uuid = uuid,
                balance = item.number("balance") ?: 0.0,
                subscriptionType = item.optInt("subscription_type", 1),
                role = item.nullableString("role"),
                elo = item.number("elo_rating")?.toInt() ?: 0,
                gender = item.nullableString("gender"),
                age = item.number("age")?.toInt(),
                arena = item.nullableString("arena"),
                bio = item.nullableString("bio"),
                points = item.number("points"),
                apiRank = item.number("rank")?.toInt(),
            ),
        )
    }
}

internal fun boardLabel(name: String): String = when (name) {
    "top100" -> "Top 100"
    "highestDebt" -> "Highest Debt"
    "Biggest Gains" -> "Biggest Gains"
    "Biggest Losses" -> "Biggest Losses"
    "league" -> "Your League"
    else -> name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").replaceFirstChar(Char::uppercase)
}

internal fun boardOrder(name: String): Int = when (name) { "top100" -> 0; "league" -> 1; "highestDebt" -> 2; "Biggest Gains" -> 3; "Biggest Losses" -> 4; else -> 100 }
internal fun LeaderboardEntry.pointsFor(board: LeaderboardBoard): Double = if (board.apiName == "Biggest Losses") abs(points ?: 0.0) else points ?: 0.0
internal fun formatExtra(value: Double, board: LeaderboardBoard): String = if (board.pointsFormat == "currency" || board.hasExtra) {
    when { abs(value) >= 1_000_000 -> "$${String.format(Locale.US, "%.1f", value / 1_000_000)}M"; abs(value) >= 1_000 -> "$${String.format(Locale.US, "%.0f", value / 1_000)}K"; else -> "$${NumberFormat.getNumberInstance(Locale.US).format(value.roundToLong())}" }
} else formatInt(value.roundToLong().toInt())
internal fun formatInt(value: Int): String = NumberFormat.getNumberInstance(Locale.US).format(value)
internal fun JSONObject.number(key: String): Double? = when (val value = opt(key)) { is Number -> value.toDouble(); is String -> value.toDoubleOrNull(); else -> null }
internal fun JSONObject.nullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }
internal fun LeaderboardEntry.profile() = ComposeAuthorProfile(uuid, balance, subscriptionType, role, gender, age, arena)
