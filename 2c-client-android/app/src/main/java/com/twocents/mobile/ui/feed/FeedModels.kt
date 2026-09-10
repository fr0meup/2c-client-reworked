package com.twocents.mobile.ui.feed

import androidx.compose.runtime.Immutable

@Immutable
data class FeedAuthor(
    val balance: Double = 0.0,
    val subscriptionType: Int = 1,
    val age: Int? = null,
    val gender: String? = null,
    val arena: String? = null,
    val role: String? = null,
    val eloRating: Double? = null,
    val alias: String? = null,
)

@Immutable
data class FeedBudgetCategory(
    val id: String,
    val label: String,
    val color: String?,
    val icon: String? = null,
    val allocated: Double,
    val spent: Double,
)

@Immutable
data class FeedPostMeta(
    val platform: String? = null,
    val images: List<String> = emptyList(),
    val mediaType: String? = null,
    val videoUrl: String? = null,
    val poll: List<String> = emptyList(),
    val quotePost: FeedPost? = null,
    val link: String? = null,
    val giphyUrl: String? = null,
    val tweetUrl: String? = null,
    val question: String? = null,
    val resolutionDeadline: String? = null,
    val merchant: String? = null,
    val category: String? = null,
    val date: String? = null,
    val transactionValue: Double? = null,
    val currencyCode: String? = null,
    val categoryIconUrl: String? = null,
    val receiptImageUrl: String? = null,
    val month: String? = null,
    val spendingLimit: Double = 0.0,
    val totalAllocated: Double = 0.0,
    val totalSpent: Double = 0.0,
    val budgetCategories: List<FeedBudgetCategory> = emptyList(),
    val priceHistory: List<Double> = emptyList(),
)

@Immutable
data class FeedPost(
    val uuid: String,
    val createdAt: String,
    val authorUuid: String,
    val upvoteCount: Int,
    val commentCount: Int,
    val viewCount: Int,
    val title: String,
    val text: String,
    val topic: String,
    val author: FeedAuthor,
    val meta: FeedPostMeta,
    val postType: Int,
) {
    val recycleType: String
        get() = when {
            postType == 2 -> "poll"
            postType == 5 -> "likert"
            postType == 7 -> "picks"
            postType == 8 -> "transaction"
            postType == 9 -> "budget"
            postType == 10 || !meta.videoUrl.isNullOrBlank() || meta.mediaType == "video" -> "video"
            meta.images.isNotEmpty() -> "image"
            meta.quotePost != null -> "quote"
            else -> "text"
        }
}

@Immutable
data class FeedOptionResult(val votes: Int, val averageBalance: Double)

@Immutable
data class FeedPicksResult(
    val yesPercent: Int = 50,
    val noPercent: Int = 50,
    val resolved: Boolean = false,
    val correctAnswer: String? = null,
    val yesAverageBalance: Double? = null,
    val noAverageBalance: Double? = null,
)

@Immutable
data class FeedUiState(
    val posts: List<FeedPost> = emptyList(),
    val postVotes: Map<String, Int> = emptyMap(),
    val pollVotes: Map<String, Int> = emptyMap(),
    val likertVotes: Map<String, Int> = emptyMap(),
    val pickVotes: Map<String, String> = emptyMap(),
    val aliases: Map<String, String> = emptyMap(),
    val bookmarkedPosts: Set<String> = emptySet(),
    val pollResults: Map<String, Map<Int, FeedOptionResult>> = emptyMap(),
    val likertResults: Map<String, Map<Int, FeedOptionResult>> = emptyMap(),
    val picksResults: Map<String, FeedPicksResult> = emptyMap(),
    val resultsRevision: Long = 0L,
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
    val advancedSearching: Boolean = false,
    val advancedScanned: Int = 0,
    val advancedMatches: Int = 0,
)

internal data class FeedPage(
    val posts: List<FeedPost>,
    val rawPosts: List<String>,
    val postVotes: Map<String, Int>,
    val pollVotes: Map<String, Int>,
    val likertVotes: Map<String, Int>,
    val pickVotes: Map<String, String>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

internal data class FeedCacheEntry(val storedAt: Long, val state: FeedUiState)

enum class FeedSource {
    Arena,
    Bookmarks,
}
