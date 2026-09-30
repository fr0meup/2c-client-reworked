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
    data class Pending(
        val comment: PostComment,
        val parent: PostComment?,
        val depth: Int,
        val isLast: Boolean,
        val rootUuid: String,
        val continuations: List<Boolean>,
    )
    val pending = java.util.ArrayDeque<Pending>()
    fun enqueueChildren(parent: PostComment?, depth: Int, rootUuid: String? = null, continuations: List<Boolean> = emptyList()) {
        val children = sorted(byParent[parent?.uuid].orEmpty())
        for (index in children.indices.reversed()) {
            val child = children[index]
            pending.addLast(Pending(child, parent, depth, index == children.lastIndex, rootUuid ?: child.uuid, continuations))
        }
    }
    enqueueChildren(null, 0)
    while (!pending.isEmpty()) {
        val item = pending.removeLast()
        if (!visited.add(item.comment.uuid)) continue
        rows += FlatPostComment(item.comment, item.depth, item.isLast, item.parent, item.rootUuid, item.continuations)
        // Use an explicit stack so very deep threads cannot overflow the call stack.
        enqueueChildren(item.comment, item.depth + 1, item.rootUuid, (item.continuations + !item.isLast).takeLast(4))
    }
    return rows
}

internal fun parseComment(root: JSONObject): PostComment? {
    val uuid = root.string("uuid") ?: return null
    val authorMeta = root.optJSONObject("author_meta")
    val commentMeta = root.optJSONObject("comment_meta")
    val text = com.twocents.mobile.ui.common.renderOfficialMentions(root.string("text").orEmpty(), commentMeta?.optJSONArray("mentions"))
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
        // Zero is the first choice, not an absent vote. Null stays absent.
        pollUserVote = authorMeta?.nullableInt("poll_user_vote")?.takeIf { it in 0..3 },
        pickUserVote = authorMeta?.string("pick_user_vote")?.lowercase(java.util.Locale.ROOT)?.takeIf { it == "yes" || it == "no" },
    )
}

private const val X_STATUS_CORE = "https?://(?:www\\.)?(?:x\\.com|twitter\\.com)/[^\\s/)]+/status/\\d+(?:\\?[^\\s)\\]]*)?"
private val X_STATUS_MARKDOWN = Regex("\\[[^]]*]\\(($X_STATUS_CORE)\\)", RegexOption.IGNORE_CASE)
private val X_STATUS_RAW = Regex(X_STATUS_CORE, RegexOption.IGNORE_CASE)

internal fun commentTweetUrl(text: String): String? =
    X_STATUS_MARKDOWN.find(text)?.groupValues?.getOrNull(1) ?: X_STATUS_RAW.find(text)?.value

internal fun prepareCommentText(text: String): String = normalizeParagraphSpacing(
    com.twocents.mobile.ui.common.withoutPreviewLinks(DirectMediaUrl.replace(text, ""))
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
