package com.twocents.mobile.ui.profile

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.data.VoteRepository
import com.twocents.mobile.ui.feed.FeedAuthor
import com.twocents.mobile.ui.feed.FeedPost
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

internal object ProfileAvailability {
    val blockedByMe = mutableStateMapOf<String, Boolean>()
}

internal enum class ProfileTab(val label: String) { Posts("Posts"), Comments("Comments"), Votes("Votes") }
internal data class BalancePoint(val balance: Double, val date: String)
internal data class ProfileUser(
    val uuid: String,
    val createdAt: String,
    val disabled: Int,
    val balance: Double,
    val bio: String?,
    val age: Int?,
    val gender: String?,
    val arena: String?,
    val subscriptionType: Int,
    val role: String?,
    val elo: Int,
)
internal data class ProfileComment(
    val uuid: String,
    val postUuid: String,
    val createdAt: String,
    val text: String,
    val upvotes: Int,
    val author: FeedAuthor,
    val authorUuid: String,
    val postTitle: String?,
    val deleted: Boolean,
    val mediaUrls: List<String>,
)
internal data class ProfileState(
    val loading: Boolean = true,
    val error: String? = null,
    val user: ProfileUser? = null,
    val history: List<BalancePoint> = emptyList(),
    val followers: Int = 0,
    val following: Int = 0,
    val totalUpvotes: Int = 0,
    val posts: List<FeedPost> = emptyList(),
    val comments: List<ProfileComment> = emptyList(),
    val votedPosts: List<FeedPost> = emptyList(),
    val pickPosts: List<FeedPost> = emptyList(),
    val votes: Map<String, Int> = emptyMap(),
    val polls: Map<String, Int> = emptyMap(),
    val likerts: Map<String, Int> = emptyMap(),
    val picks: Map<String, String> = emptyMap(),
    val commentVotes: Map<String, Int> = emptyMap(),
    val followsMe: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePosts: Boolean = false,
    val hasMoreComments: Boolean = false,
    val hasMoreVotedPosts: Boolean = false,
    val hasMorePickVotes: Boolean = false,
    val nextPostCursor: String? = null,
    val nextCommentCursor: String? = null,
    val nextVotedPostCursor: String? = null,
    val nextPickVoteCursor: String? = null,
)

@Stable
internal class ProfileController(
    private val api: RpcApi,
    private val auth: AuthState,
    private val targetUuid: String,
) {
    private val voteRepository = VoteRepository(api, auth)
    var state by mutableStateOf(ProfileState())
        private set

    suspend fun load(force: Boolean = false): Boolean {
        if (!force && !state.loading && state.user != null) return true
        state = state.copy(loading = state.user == null, error = null)
        return runCatching {
            val root = api.call(
                "/v2/users/get",
                JSONObject().put("user_uuid", targetUuid).put("posts_limit", 20).put("comments_limit", 30)
                    .put("voted_posts_limit", 20),
                auth,
            ) as? JSONObject ?: error("Invalid profile response")
            val followsMe = if (targetUuid == auth.userUuid) false else runCatching {
                val result = api.call("/v1/aliases/hasMe", JSONObject().put("authorUUID", targetUuid), auth) as? JSONObject
                result?.optBoolean("hasAlias") == true
            }.getOrDefault(false)
            state = parseProfile(root).copy(loading = false, followsMe = followsMe)
            true
        }.getOrElse {
            if (it is CancellationException) throw it
            val blocked = runCatching {
                val result = api.call("/v1/users/blocked", JSONObject(), auth) as? JSONObject
                listOf("blocked", "blocked_users", "users").asSequence().mapNotNull { key -> result?.optJSONArray(key) }.flatMap { array ->
                    (0 until array.length()).asSequence().mapNotNull { index ->
                        when (val value = array.opt(index)) {
                            is String -> value
                            is JSONObject -> listOf("uuid", "blocked_uuid", "user_uuid").firstNotNullOfOrNull { key -> value.optString(key).takeIf(String::isNotBlank) }
                            else -> null
                        }
                    }
                }.any { uuid -> uuid.equals(targetUuid, true) }
            }.getOrDefault(false)
            ProfileAvailability.blockedByMe[targetUuid] = blocked
            state = state.copy(loading = false, error = it.message ?: "Couldn't load profile")
            false
        }
    }

    fun hasMore(tab: ProfileTab): Boolean = when (tab) {
        ProfileTab.Posts -> state.hasMorePosts
        ProfileTab.Comments -> state.hasMoreComments
        ProfileTab.Votes -> state.hasMoreVotedPosts
    }

    suspend fun loadMore(tab: ProfileTab) {
        if (state.isLoadingMore || !hasMore(tab)) return
        val before = state
        state = state.copy(isLoadingMore = true)
        runCatching {
            val params = JSONObject().put("user_uuid", targetUuid)
                .put("posts_limit", 20).put("comments_limit", 30).put("voted_posts_limit", 20)
            when (tab) {
                ProfileTab.Posts -> before.nextPostCursor?.let { params.put("posts_cursor", it) }
                ProfileTab.Comments -> before.nextCommentCursor?.let { params.put("comments_cursor", it) }
                ProfileTab.Votes -> before.nextVotedPostCursor?.let { params.put("voted_posts_cursor", it) }
            }
            val root = api.call("/v2/users/get", params, auth) as? JSONObject ?: error("Invalid profile page")
            mergeProfilePage(before, parseProfile(root)).copy(isLoadingMore = false)
        }.onSuccess { state = it }.onFailure { error ->
            if (error is CancellationException) throw error
            state = before.copy(isLoadingMore = false, error = error.message)
        }
    }

    suspend fun toggleCommentVote(comment: ProfileComment, direction: Int) {
        val oldVote = state.commentVotes[comment.uuid] ?: 0
        val nextVote = if (oldVote == direction) 0 else direction
        val oldComments = state.comments
        state = state.copy(
            commentVotes = state.commentVotes + (comment.uuid to nextVote),
            comments = state.comments.map { current ->
                if (current.uuid == comment.uuid) current.copy(upvotes = current.upvotes + nextVote - oldVote) else current
            },
        )
        if (!voteRepository.comment(comment.postUuid, comment.uuid, nextVote)) {
            state = state.copy(commentVotes = state.commentVotes + (comment.uuid to oldVote), comments = oldComments)
        }
    }

    suspend fun togglePostVote(post: FeedPost, direction: Int) {
        val oldVote = state.votes[post.uuid] ?: 0
        val nextVote = if (oldVote == direction) 0 else direction
        val oldPosts = state.posts
        val oldVotedPosts = state.votedPosts
        fun update(rows: List<FeedPost>) = rows.map { current ->
            if (current.uuid == post.uuid) current.copy(upvoteCount = current.upvoteCount + nextVote - oldVote) else current
        }
        state = state.copy(
            votes = state.votes + (post.uuid to nextVote),
            posts = update(state.posts),
            votedPosts = update(state.votedPosts),
        )
        if (!voteRepository.post(post.uuid, nextVote)) {
            state = state.copy(votes = state.votes + (post.uuid to oldVote), posts = oldPosts, votedPosts = oldVotedPosts)
        }
    }
}
