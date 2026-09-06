package com.twocents.mobile.ui.feed

import androidx.compose.runtime.Immutable

@Immutable
internal data class PostComment(
    val uuid: String,
    val createdAt: String,
    val postUuid: String,
    val parentUuid: String?,
    val authorUuid: String,
    val author: FeedAuthor,
    val text: String,
    val upvoteCount: Int,
    val deleted: Boolean,
    val mediaUrls: List<String>,
)

internal enum class CommentSort(val label: String) {
    Top("Top"),
    Newest("Newest"),
    Oldest("Oldest"),
    Networth("Net worth"),
}

@Immutable
internal data class FlatPostComment(
    val comment: PostComment,
    val depth: Int,
    val isLast: Boolean,
    val ancestorContinuations: List<Boolean>,
)

@Immutable
internal data class PostDetailUiState(
    val post: FeedPost? = null,
    val postVote: Int = 0,
    val pollVote: Int? = null,
    val likertVote: Int? = null,
    val pickVote: String? = null,
    val comments: List<PostComment> = emptyList(),
    val commentVotes: Map<String, Int> = emptyMap(),
    val aliases: Map<String, String> = emptyMap(),
    val commentOrderGeneration: Int = 0,
    val loadingPost: Boolean = true,
    val loadingComments: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
)

internal data class PostResult(
    val post: FeedPost?,
    val postVote: Int,
    val pollVote: Int?,
    val likertVote: Int?,
    val pickVote: String?,
)
