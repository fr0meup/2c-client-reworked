package com.twocents.mobile.ui.feed

import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.core.json.double
import com.twocents.mobile.core.json.int
import com.twocents.mobile.core.json.nullableDouble
import com.twocents.mobile.core.json.nullableInt
import com.twocents.mobile.core.json.objectValue
import com.twocents.mobile.core.json.objects
import com.twocents.mobile.core.json.string
import com.twocents.mobile.core.json.strings
import com.twocents.mobile.core.media.looksLikeGifUrl
import com.twocents.mobile.core.media.looksLikeVideoUrl
import org.json.JSONObject
import java.util.Locale

internal fun parseFeedPage(root: JSONObject): FeedPage {
    val pagination = root.optJSONObject("pagination")
    val rawPosts = root.optJSONArray("posts").objects()
    return FeedPage(
        posts = rawPosts.mapNotNull(::parseFeedPost),
        rawPosts = rawPosts.map(JSONObject::toString),
        postVotes = root.optJSONArray("votes").objects().associate { it.string("content_uuid").orEmpty() to it.int("vote_type") },
        pollVotes = root.optJSONArray("polls").objects().associate { it.string("post_uuid").orEmpty() to it.int("option") },
        likertVotes = root.optJSONArray("likertVotes").objects().associate { it.string("post_uuid").orEmpty() to it.int("option") },
        pickVotes = root.optJSONArray("pickVotes").objects().mapNotNull { vote ->
            val uuid = vote.string("post_uuid") ?: return@mapNotNull null
            val value = vote.string("vote") ?: return@mapNotNull null
            uuid to value
        }.toMap(),
        nextCursor = pagination?.string("next_cursor"),
        hasMore = pagination?.optBoolean("has_more", false) == true,
    )
}

internal suspend fun loadFeedPost(api: RpcApi, auth: AuthState, uuid: String): FeedPost? {
    val root = api.call("/v1/posts/get", JSONObject().put("post_uuid", uuid), auth) as? JSONObject
        ?: return null
    return root.optJSONObject("post")?.let(::parseFeedPost)
}

internal fun parseFeedPost(root: JSONObject, depth: Int = 0): FeedPost? {
    val uuid = root.string("uuid") ?: return null
    val authorMeta = root.objectValue("author_meta")
    val meta = root.objectValue("post_meta")
    val postType = root.int("post_type")
    val sourceUrl = meta?.string("src")?.normalizedApiMediaUrl()
    val sourceGifUrl = sourceUrl?.takeIf(String::looksLikeGifUrl)?.let(::normalizeMediaUrl)
    val preferredMedia = buildList {
        addAll(meta?.optJSONArray("imageUrls").strings())
        addAll(meta?.optJSONArray("image_urls").strings())
    }.map(String::normalizedApiMediaUrl).filter(String::isNotBlank).distinct()
    val singularMedia = (meta?.string("imageUrl") ?: meta?.string("image_url"))
        ?.normalizedApiMediaUrl()
        ?.takeIf(String::isNotBlank)
    val imageGifUrl = (preferredMedia.firstOrNull(String::looksLikeGifUrl)
        ?: singularMedia?.takeIf(String::looksLikeGifUrl))?.let(::normalizeMediaUrl)
    val videoUrl = meta?.string("videoUrl")
        ?: meta?.string("video_url")
        ?: meta?.string("video")
        ?: sourceUrl?.takeIf { source ->
            postType == 10 || meta.string("media_type") == "video" || source.looksLikeVideoUrl()
        }
    val images = buildList {
        addAll(preferredMedia.filterNot(String::looksLikeGifUrl))
        if (isEmpty()) {
            listOfNotNull(singularMedia, sourceUrl)
                .firstOrNull { it.isNotEmpty() && it != videoUrl && !it.looksLikeVideoUrl() && !it.looksLikeGifUrl() }
                ?.let(::add)
        }
    }.distinct()
    // Quotes are intentionally parsed one level deep to prevent malformed cyclic payloads
    // from recursively constructing an unbounded post tree.
    val quote = if (depth == 0) meta?.objectValue("quote_post")?.let { parseFeedPost(it, depth + 1) } else null
    val categories = meta?.optJSONArray("categories").objects().map { category ->
        FeedBudgetCategory(
            id = category.string("id") ?: category.string("label").orEmpty(),
            label = category.string("label").orEmpty(),
            color = category.string("color"),
            icon = category.string("icon"),
            allocated = category.double("allocated"),
            spent = category.double("spent"),
        )
    }
    return FeedPost(
        uuid = uuid,
        createdAt = root.string("created_at").orEmpty(),
        authorUuid = root.string("author_uuid").orEmpty(),
        upvoteCount = root.int("upvote_count"),
        commentCount = root.int("comment_count"),
        viewCount = root.int("view_count"),
        title = root.string("title").orEmpty(),
        text = root.string("text").orEmpty(),
        topic = root.string("topic").orEmpty(),
        author = FeedAuthor(
            balance = authorMeta?.double("balance") ?: 0.0,
            subscriptionType = authorMeta?.int("subscription_type") ?: 1,
            age = authorMeta?.nullableInt("age"),
            gender = authorMeta?.string("gender"),
            arena = authorMeta?.string("arena"),
            role = authorMeta?.string("role"),
            eloRating = authorMeta?.nullableDouble("elo_rating"),
            alias = authorMeta?.string("alias") ?: authorMeta?.string("display_name"),
        ),
        meta = FeedPostMeta(
            platform = meta?.string("platform"),
            images = images,
            mediaType = meta?.string("media_type") ?: videoUrl?.let { "video" },
            videoUrl = videoUrl,
            poll = meta?.optJSONArray("poll").strings(),
            quotePost = quote,
            link = meta?.string("link"),
            giphyUrl = meta?.string("giphy_url")?.let(::normalizeMediaUrl) ?: sourceGifUrl ?: imageGifUrl,
            tweetUrl = meta?.string("tweet_url") ?: meta?.string("tweetUrl"),
            question = meta?.string("question"),
            resolutionDeadline = meta?.string("resolution_deadline"),
            merchant = meta?.string("merchant"),
            category = meta?.string("category"),
            date = meta?.string("date"),
            transactionValue = meta?.nullableDouble("transactionValue") ?: meta?.nullableDouble("transaction_value"),
            currencyCode = meta?.string("currencyCode") ?: meta?.string("currency_code"),
            categoryIconUrl = meta?.string("categoryIconUrl") ?: meta?.string("category_icon_url"),
            receiptImageUrl = meta?.string("imageUrl") ?: meta?.string("image_url"),
            month = meta?.string("month"),
            spendingLimit = meta?.nullableDouble("spendingLimit") ?: meta?.double("spending_limit") ?: 0.0,
            totalAllocated = meta?.nullableDouble("totalAllocated") ?: meta?.double("total_allocated") ?: 0.0,
            totalSpent = meta?.nullableDouble("totalSpent") ?: meta?.double("total_spent") ?: 0.0,
            budgetCategories = categories,
        ),
        postType = postType,
    )
}

private val MarkdownWrappedMediaUrl = Regex("^\\[[^]]*]\\((https?://[^)]+)\\)$", RegexOption.IGNORE_CASE)

private fun String.normalizedApiMediaUrl(): String =
    (MarkdownWrappedMediaUrl.matchEntire(trim())?.groupValues?.getOrNull(1) ?: trim()).replace("\\&", "&")

internal fun parseOptionResults(root: JSONObject?, averageKeys: List<String>): Map<Int, FeedOptionResult> {
    if (root == null) return emptyMap()
    return buildMap {
        root.keys().forEach { key ->
            val value = root.optJSONObject(key) ?: return@forEach
            val average = averageKeys.firstNotNullOfOrNull { value.nullableDouble(it) } ?: 0.0
            key.toIntOrNull()?.let { put(it, FeedOptionResult(value.int("votes"), average)) }
        }
    }
}

internal fun topicToApi(topic: String): String? = when (topic) {
    "New", "All" -> null
    "Hot" -> "hot"
    "Following" -> "following"
    "Polls" -> "new-polls"
    "Picks" -> "picks"
    "Bugs and feedback" -> "bugs-and-feedback"
    "Business and entrepreneurship" -> "business-entrepreneurship"
    "AI and tech" -> "ai-tech"
    "Ask a millionaire" -> "ask-a-millionaire"
    "Situation monitoring" -> "situation-monitoring"
    "Real estate" -> "real-estate"
    else -> topic.lowercase(Locale.US).replace(Regex("[^a-z0-9_-]"), "-").replace(Regex("-+"), "-")
}
