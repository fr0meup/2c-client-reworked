package com.twocents.mobile.ui.feed

import com.twocents.mobile.core.json.double
import com.twocents.mobile.core.json.int
import com.twocents.mobile.core.json.nullableDouble
import com.twocents.mobile.core.json.nullableInt
import com.twocents.mobile.core.json.string
import com.twocents.mobile.core.media.DirectMediaUrl
import com.twocents.mobile.core.media.looksLikeGifUrl
import com.twocents.mobile.core.text.normalizeParagraphSpacing
import org.json.JSONObject

internal fun flattenPostComments(comments: List<PostComment>, sort: CommentSort): List<FlatPostComment> {
    val known = comments.mapTo(HashSet()) { it.uuid }
    val byParent = comments.groupBy { it.parentUuid?.takeIf(known::contains) }
    val rows = mutableListOf<FlatPostComment>()
    val visited = HashSet<String>()
    fun sorted(items: List<PostComment>) = when (sort) {
        CommentSort.Newest -> items.sortedByDescending { it.createdAt }
        CommentSort.Oldest -> items.sortedBy { it.createdAt }
        CommentSort.Networth -> items.sortedWith(compareByDescending<PostComment> { it.author.balance }.thenByDescending { it.upvoteCount })
        CommentSort.Top -> items.sortedWith(compareByDescending<PostComment> { it.upvoteCount }.thenBy { it.createdAt })
    }
    fun visit(parent: String?, depth: Int, ancestorContinuations: List<Boolean>) {
        val children = sorted(byParent[parent].orEmpty())
        children.forEachIndexed { index, comment ->
            if (!visited.add(comment.uuid)) return@forEachIndexed
            val isLast = index == children.lastIndex
            rows += FlatPostComment(comment, depth, isLast, ancestorContinuations)
            visit(comment.uuid, depth + 1, ancestorContinuations + !isLast)
        }
    }
    visit(null, 0, emptyList())
    return rows
}

internal fun parseComment(root: JSONObject): PostComment? {
    val uuid = root.string("uuid") ?: return null
    val authorMeta = root.optJSONObject("author_meta")
    val commentMeta = root.optJSONObject("comment_meta")
    val text = root.string("text").orEmpty()
    val media = buildList {
        listOf("giphy_url", "image_url", "imageUrl", "src").forEach { key ->
            commentMeta?.string(key)?.takeIf { it.isNotBlank() }?.let { add(normalizeMediaUrl(it)) }
        }
        DirectMediaUrl.findAll(text).map { normalizeMediaUrl(it.value.trimEnd('.', ',', ')')) }.forEach(::add)
    }.distinct()
    return PostComment(
        uuid = uuid,
        createdAt = root.string("created_at").orEmpty(),
        postUuid = root.string("post_uuid").orEmpty(),
        parentUuid = root.string("reply_parent_uuid")?.takeIf { it.isNotBlank() },
        authorUuid = root.string("author_uuid").orEmpty(),
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
        text = text,
        upvoteCount = root.int("upvote_count"),
        deleted = !root.isNull("deleted_at"),
        mediaUrls = media,
    )
}

private const val X_STATUS_CORE = "https?://(?:www\\.)?(?:x\\.com|twitter\\.com)/[^\\s/)]+/status/\\d+(?:\\?[^\\s)\\]]*)?"
private val X_STATUS_MARKDOWN = Regex("\\[[^]]*]\\(($X_STATUS_CORE)\\)", RegexOption.IGNORE_CASE)
private val X_STATUS_RAW = Regex(X_STATUS_CORE, RegexOption.IGNORE_CASE)

internal fun commentTweetUrl(text: String): String? =
    X_STATUS_MARKDOWN.find(text)?.groupValues?.getOrNull(1) ?: X_STATUS_RAW.find(text)?.value

internal fun prepareCommentText(text: String): String = normalizeParagraphSpacing(
    DirectMediaUrl.replace(text, "")
        .replace(X_STATUS_MARKDOWN, "")
        .replace(X_STATUS_RAW, ""),
)

internal fun PostComment.visibleText(): String = prepareCommentText(text)

/** Supplies a meaningful reply preview for media-only comments. */
internal fun PostComment.replyPreviewText(): String = visibleText().ifBlank {
    when {
        mediaUrls.firstOrNull()?.looksLikeGifUrl() == true -> "GIF"
        mediaUrls.isNotEmpty() -> "Image"
        commentTweetUrl(text) != null -> "X post"
        else -> "Comment"
    }
}
