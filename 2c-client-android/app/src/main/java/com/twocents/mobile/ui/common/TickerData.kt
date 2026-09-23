package com.twocents.mobile.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.feed.FeedPage
import com.twocents.mobile.ui.feed.PostComment
import com.twocents.mobile.ui.feed.parseComment
import com.twocents.mobile.ui.feed.parseFeedPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import com.twocents.mobile.data.VoteRepository

internal val LocalTickerData = staticCompositionLocalOf<TickerData?> { null }

internal data class TickerDetails(
    val name: String,
    val exchange: String,
    val currency: String,
    val marketCap: Double,
    val logoUrl: String?,
    val homepageUrl: String?,
    val description: String,
)

internal data class TickerPrice(
    val price: Double?,
    val change: Double?,
    val changePercent: Double?,
    val volume: Double?,
)

internal data class TickerPoint(val time: Long, val close: Double, val volume: Double? = null)
internal data class TickerCommentsPage(
    val comments: List<PostComment>,
    val votes: Map<String, Int>,
    val postTitles: Map<String, String>,
    val postTopics: Map<String, String>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

internal enum class TickerPeriod(val label: String) { Day("1D"), Week("1W"), Month("1M"), Year("1Y"), All("ALL") }

/** Uses the backend's aggregation cadence; 1D deliberately requests five days of bars. */
internal data class TickerChartSpec(val multiplier: Int, val timespan: String, val from: String, val to: String)

internal fun tickerChartSpec(period: TickerPeriod, now: Long = System.currentTimeMillis()): TickerChartSpec {
    val utcToday = LocalDate.ofEpochDay(now / 86_400_000L)
    return when (period) {
        TickerPeriod.Day -> TickerChartSpec(5, "minute", (now - 5L * 86_400_000).toString(), now.toString())
        TickerPeriod.Week -> TickerChartSpec(30, "minute", (now - 7L * 86_400_000).toString(), now.toString())
        TickerPeriod.Month -> TickerChartSpec(1, "hour", (now - 31L * 86_400_000).toString(), now.toString())
        TickerPeriod.Year -> TickerChartSpec(1, "day", utcToday.minusYears(1).toString(), utcToday.toString())
        TickerPeriod.All -> TickerChartSpec(1, "week", "1970-01-01", utcToday.toString())
    }
}

internal fun tickerChartParams(period: TickerPeriod): JSONObject = tickerChartSpec(period).let { spec ->
    JSONObject().put("multiplier", spec.multiplier).put("timespan", spec.timespan)
        .put("from", spec.from).put("to", spec.to).put("sort", "asc").put("limit", 50_000)
}

internal fun latestTradingDay(points: List<TickerPoint>): List<TickerPoint> {
    val lastDay = points.lastOrNull()?.time?.div(86_400_000L) ?: return points
    return points.filter { it.time / 86_400_000L == lastDay }
}

internal class TickerData(private val api: RpcApi, private val auth: AuthState) {
    val authUuid: String get() = auth.userUuid
    private val detailsCache = object : LinkedHashMap<String, Pair<Long, TickerDetails>>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, TickerDetails>>?) = size > 32
    }
    private val priceCache = object : LinkedHashMap<String, Pair<Long, TickerPrice>>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, TickerPrice>>?) = size > 32
    }
    private val chartCache = object : LinkedHashMap<Pair<String, TickerPeriod>, Pair<Long, List<TickerPoint>>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, TickerPeriod>, Pair<Long, List<TickerPoint>>>?) = size > 16
    }
    fun feedController(context: android.content.Context) =
        com.twocents.mobile.ui.feed.FeedController(api, auth, context = context)
    suspend fun voteComment(comment: PostComment, vote: Int): Boolean =
        VoteRepository(api, auth).comment(comment.postUuid, comment.uuid, vote)
    suspend fun deleteComment(comment: PostComment) {
        api.call("/v1/comments/delete", JSONObject().put("comment_uuid", comment.uuid)
            .put("post_uuid", comment.postUuid), auth)
    }

    suspend fun details(symbol: String): TickerDetails {
        detailsCache[symbol]?.takeIf { System.currentTimeMillis() - it.first < 60 * 60_000L }?.let { return it.second }
        val root = api.call("/v1/info/ticker/details", JSONObject().put("ticker", symbol), auth) as? JSONObject
        val data = root?.optJSONObject("details") ?: error("Company details unavailable")
        return TickerDetails(data.optString("name", symbol), data.optString("exchange"),
            data.optString("currency", "USD"), data.optDouble("marketCap", 0.0),
            data.optString("logoUrl").takeIf(String::isHttpUrl),
            data.optString("homepageUrl").takeIf(String::isHttpUrl), data.optString("description"))
            .also { detailsCache[symbol] = System.currentTimeMillis() to it }
    }

    suspend fun price(symbol: String): TickerPrice {
        priceCache[symbol]?.takeIf { System.currentTimeMillis() - it.first < 20_000L }?.let { return it.second }
        val root = api.call("/v1/info/stocks/price", JSONObject().put("ticker", symbol), auth) as? JSONObject
        val snapshot = root?.optJSONObject("snapshot")
        val day = snapshot?.optJSONObject("day")
        val previous = snapshot?.optJSONObject("prevDay")
        val close = day?.numberOrNull("c") ?: snapshot?.optJSONObject("min")?.numberOrNull("c")
        val change = snapshot?.numberOrNull("todaysChange") ?: close?.let { it - (previous?.numberOrNull("c") ?: it) }
        return TickerPrice(close, change, snapshot?.numberOrNull("todaysChangePerc"), day?.numberOrNull("v"))
            .also { priceCache[symbol] = System.currentTimeMillis() to it }
    }

    suspend fun chart(symbol: String, period: TickerPeriod): List<TickerPoint> {
        val cacheKey = symbol to period
        chartCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.first < 60_000L }?.let { return it.second }
        val root = api.call("/v1/massive/equities/chart", JSONObject()
            .put("ticker", symbol).put("chart", tickerChartParams(period)), auth) as? JSONObject
        return withContext(Dispatchers.Default) {
            val rows = root?.optJSONArray("aggregates") ?: return@withContext emptyList()
            val parsed = buildList(rows.length()) {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val close = row.numberOrNull("c") ?: continue
                    if (close.isFinite()) add(TickerPoint(row.optLong("t"), close, row.numberOrNull("v")))
                }
            }
            // The 1D endpoint includes five calendar days to survive market closures;
            // draw the latest trading session, not a misleading five-day line.
            if (period == TickerPeriod.Day) latestTradingDay(parsed) else parsed
        }.also { if (it.isNotEmpty()) chartCache[cacheKey] = System.currentTimeMillis() to it }
    }

    suspend fun posts(symbol: String, sort: String, cursor: String?): FeedPage {
        val params = JSONObject().put("ticker", symbol).put("sort", sort)
        if (cursor != null) params.put("cursor", cursor)
        val root = api.call("/v2/posts/ticker/list", params, auth) as? JSONObject ?: error("Posts unavailable")
        return parseFeedPage(root)
    }

    suspend fun comments(symbol: String, sort: String, cursor: String?): TickerCommentsPage {
        val params = JSONObject().put("ticker", symbol).put("sort", sort)
        if (cursor != null) params.put("cursor", cursor)
        val root = api.call("/v1/comments/ticker/list", params, auth) as? JSONObject ?: error("Replies unavailable")
        val rows = root.optJSONArray("comments")
        val votes = root.optJSONArray("votes")
        val page = root.optJSONObject("pagination")
        return TickerCommentsPage(
            comments = buildList { for (i in 0 until (rows?.length() ?: 0)) rows?.optJSONObject(i)?.let(::parseComment)?.let(::add) },
            votes = buildMap { for (i in 0 until (votes?.length() ?: 0)) votes?.optJSONObject(i)?.let {
                put(it.optString("content_uuid"), it.optInt("vote_type"))
            } },
            postTitles = root.optJSONObject("postTitles").stringValues(),
            postTopics = root.optJSONObject("postTopics").stringValues(),
            nextCursor = page?.takeUnless { it.isNull("next_cursor") }?.optString("next_cursor")?.takeIf(String::isNotBlank),
            hasMore = page?.optBoolean("has_more") == true,
        )
    }
}

private fun JSONObject.numberOrNull(key: String): Double? =
    opt(key)?.let { (it as? Number)?.toDouble() }?.takeIf(Double::isFinite)

private fun String.isHttpUrl(): Boolean = startsWith("https://", true) || startsWith("http://", true)

private fun JSONObject?.stringValues(): Map<String, String> = buildMap {
    if (this@stringValues == null) return@buildMap
    val keys = this@stringValues.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        put(key, this@stringValues.optString(key))
    }
}
