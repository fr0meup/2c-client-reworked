package com.twocents.mobile.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.twocents.mobile.ui.feed.FeedController
import com.twocents.mobile.ui.feed.PostComment
import kotlinx.coroutines.CancellationException

internal enum class TickerTab { Posts, Replies }
internal enum class TickerSort(val apiValue: String) { Top("top"), Latest("latest") }

/** One paginated ticker stream. It shares the ordinary feed controller for post actions. */
internal class TickerFeed(private val data: TickerData, val posts: FeedController) {
    var tab by mutableStateOf(TickerTab.Posts)
        private set
    var sort by mutableStateOf(TickerSort.Top)
        private set
    var comments by mutableStateOf(emptyList<PostComment>())
        private set
    var commentVotes by mutableStateOf(emptyMap<String, Int>())
        private set
    var postTitles by mutableStateOf(emptyMap<String, String>())
        private set
    var postTopics by mutableStateOf(emptyMap<String, String>())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set
    private var cursor: String? = null
    private var generation = 0

    suspend fun select(symbol: String, nextTab: TickerTab, nextSort: TickerSort) {
        val currentGeneration = ++generation
        tab = nextTab
        sort = nextSort
        cursor = null
        hasMore = false
        error = null
        loading = true
        comments = emptyList()
        commentVotes = emptyMap()
        postTitles = emptyMap()
        postTopics = emptyMap()
        posts.state = posts.state.copy(posts = emptyList(), postVotes = emptyMap())
        request(symbol, null, currentGeneration)
    }

    suspend fun loadMore(symbol: String) {
        if (loading || !hasMore || cursor == null) return
        request(symbol, cursor, generation)
    }

    private suspend fun request(symbol: String, requestCursor: String?, requestGeneration: Int) {
        loading = true
        error = null
        try {
            if (tab == TickerTab.Posts) {
                val page = data.posts(symbol, sort.apiValue, requestCursor)
                if (requestGeneration != generation) return
                posts.state = posts.state.copy(
                    posts = (posts.state.posts + page.posts).distinctBy { it.uuid },
                    postVotes = posts.state.postVotes + page.postVotes,
                    pollVotes = posts.state.pollVotes + page.pollVotes,
                    likertVotes = posts.state.likertVotes + page.likertVotes,
                    pickVotes = posts.state.pickVotes + page.pickVotes,
                    isInitialLoading = false,
                )
                cursor = page.nextCursor
                hasMore = page.hasMore && cursor != null
            } else {
                val page = data.comments(symbol, sort.apiValue, requestCursor)
                if (requestGeneration != generation) return
                comments = (comments + page.comments).distinctBy { it.uuid }
                commentVotes = commentVotes + page.votes
                postTitles = postTitles + page.postTitles
                postTopics = postTopics + page.postTopics
                cursor = page.nextCursor
                hasMore = page.hasMore && cursor != null
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) {
            if (requestGeneration == generation) error = friendlyError(failure, "Couldn't load ticker activity")
        } finally {
            if (requestGeneration == generation) loading = false
        }
    }

    suspend fun toggleCommentVote(comment: PostComment, direction: Int) {
        val previous = commentVotes[comment.uuid] ?: 0
        val next = if (previous == direction) 0 else direction
        val oldComments = comments
        comments = comments.map { if (it.uuid == comment.uuid) it.copy(upvoteCount = it.upvoteCount + next - previous) else it }
        commentVotes = commentVotes + (comment.uuid to next)
        if (!data.voteComment(comment, next)) {
            comments = oldComments
            commentVotes = commentVotes + (comment.uuid to previous)
            AppToast.error("Couldn't save vote")
        }
    }

    suspend fun deleteComment(comment: PostComment) {
        try {
            data.deleteComment(comment)
            comments = comments.map { if (it.uuid == comment.uuid) it.copy(deleted = true, text = "", mediaUrls = emptyList()) else it }
            AppToast.success("Comment deleted")
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { AppToast.error(friendlyError(failure, "Couldn't delete comment")) }
    }
}
