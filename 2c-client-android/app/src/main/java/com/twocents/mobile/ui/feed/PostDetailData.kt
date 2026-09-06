package com.twocents.mobile.ui.feed

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.core.json.int
import com.twocents.mobile.core.json.objects
import com.twocents.mobile.core.json.string
import com.twocents.mobile.data.AliasRepository
import com.twocents.mobile.data.VoteRepository
import com.twocents.mobile.ui.compose.formatComposeTextForApi
import com.twocents.mobile.ui.common.notifyMentions
import com.twocents.mobile.ui.common.AppToast
import com.twocents.mobile.ui.common.friendlyError
import com.twocents.mobile.ui.settings.InteractionPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

@Stable
internal class PostDetailController(
    private val api: RpcApi,
    private val auth: AuthState,
    private val seedPost: FeedPost,
) {
    private val aliasRepository = AliasRepository(api, auth)
    private val voteRepository = VoteRepository(api, auth)
    var state by mutableStateOf(PostDetailUiState(post = seedPost))
        private set
    private val locallyVotedComments = hashSetOf<String>()

    suspend fun load(force: Boolean = false) = coroutineScope {
        if (!force && !state.loadingPost && !state.loadingComments) return@coroutineScope
        state = state.copy(
            loadingPost = state.post == null,
            loadingComments = state.comments.isEmpty(),
            error = null,
        )
        val postRequest = async { runCatching { requestPost(seedPost.uuid) } }
        val commentsRequest = async { runCatching { requestComments(seedPost.uuid) } }
        val aliasesRequest = async { runCatching { requestAliases() } }

        postRequest.await().onSuccess { postResult ->
            state = state.copy(
                post = postResult.post ?: state.post,
                postVote = postResult.postVote,
                pollVote = postResult.pollVote,
                likertVote = postResult.likertVote,
                pickVote = postResult.pickVote,
                loadingPost = false,
            )
        }.onFailure { error ->
            state = state.copy(loadingPost = false, error = error.message ?: "Couldn't load post")
        }
        commentsRequest.await().onSuccess { result ->
            val merged = mergeLocalCommentVotes(result.first, result.second)
            state = state.copy(
                comments = merged.first,
                commentVotes = merged.second,
                commentOrderGeneration = state.commentOrderGeneration + 1,
                loadingComments = false,
            )
        }.onFailure { error ->
            state = state.copy(loadingComments = false, error = error.message ?: state.error)
        }
        aliasesRequest.await().getOrNull()?.let { state = state.copy(aliases = it) }
    }

    suspend fun refresh(): Boolean {
        load(force = true)
        return state.error == null
    }

    suspend fun toggleCommentVote(commentUuid: String, direction: Int) {
        val oldVote = state.commentVotes[commentUuid] ?: 0
        val nextVote = if (oldVote == direction) 0 else direction
        val oldComments = state.comments
        locallyVotedComments += commentUuid
        state = state.copy(
            commentVotes = state.commentVotes + (commentUuid to nextVote),
            comments = state.comments.map { comment ->
                if (comment.uuid == commentUuid) {
                    comment.copy(upvoteCount = comment.upvoteCount + nextVote - oldVote)
                } else comment
            },
        )
        if (!voteRepository.comment(seedPost.uuid, commentUuid, nextVote)) {
            state = state.copy(commentVotes = state.commentVotes + (commentUuid to oldVote), comments = oldComments)
        }
    }

    suspend fun createComment(text: String, parentUuid: String?, imageUri: String?, context: Context): Boolean {
        if (state.submitting) return false
        val limitedText = text.take(1_000)
        state = state.copy(submitting = true)
        return runCatching {
            val imageUrl = imageUri?.let { uploadCommentImage(it, context) }
            val created = api.call(
                "/v1/comments/create",
                JSONObject()
                    .put("post_uuid", seedPost.uuid)
                    .put("text", formatComposeTextForApi(limitedText.trim()).ifBlank { "\u200B" })
                    .put("in_reply_to_uuid", parentUuid.orEmpty())
                    .apply { imageUrl?.let { put("image_url", it) } },
                auth,
            ) as? JSONObject
            val createdCommentUuid = created?.optJSONObject("comment")?.optString("uuid")
            if (!createdCommentUuid.isNullOrBlank() && InteractionPreferences.autoLikeOwnContent(context)) {
                voteRepository.comment(seedPost.uuid, createdCommentUuid, 1)
            }
            val comments = requestComments(seedPost.uuid)
            val post = requestPost(seedPost.uuid)
            val mergedComments = mergeLocalCommentVotes(comments.first, comments.second)
            state = state.copy(
                post = post.post ?: state.post,
                comments = mergedComments.first,
                commentVotes = mergedComments.second,
                commentOrderGeneration = state.commentOrderGeneration + 1,
                submitting = false,
                error = null,
            )
            if (!createdCommentUuid.isNullOrBlank()) {
                notifyMentions(api, auth, limitedText, seedPost.uuid, createdCommentUuid, "comment")
            }
            val responseMessage = created?.optString("message").orEmpty()
            if (responseMessage.contains("moder", true) || responseMessage.contains("flag", true)) {
                AppToast.error("Comment submitted for moderation")
            } else AppToast.success(if (parentUuid.isNullOrBlank()) "Comment posted" else "Reply posted")
            true
        }.getOrElse { error ->
            state = state.copy(submitting = false, error = "Failed to post comment")
            AppToast.error(friendlyError(error, "Couldn't post comment"))
            false
        }
    }

    suspend fun deleteComment(commentUuid: String): Boolean = runCatching {
        api.call(
            "/v1/comments/delete",
            JSONObject().put("comment_uuid", commentUuid).put("post_uuid", seedPost.uuid),
            auth,
        )
        state = state.copy(comments = state.comments.map { if (it.uuid == commentUuid) it.copy(deleted = true, text = "", mediaUrls = emptyList()) else it })
        AppToast.success("Comment deleted")
        true
    }.getOrElse { error ->
        AppToast.error(friendlyError(error, "Couldn't delete comment"))
        false
    }

    private suspend fun requestPost(uuid: String): PostResult {
        val root = api.call("/v1/posts/get", JSONObject().put("post_uuid", uuid), auth) as? JSONObject
            ?: error("Post response was invalid")
        return PostResult(
            post = root.optJSONObject("post")?.let(::parseFeedPost),
            postVote = root.optJSONArray("votes").objects().firstOrNull()?.int("vote_type") ?: 0,
            pollVote = root.optJSONArray("polls").objects().firstOrNull()?.int("option"),
            likertVote = root.optJSONArray("likertVotes").objects().firstOrNull()?.int("option"),
            pickVote = root.optJSONArray("pickVotes").objects().firstOrNull()?.string("vote"),
        )
    }

    private suspend fun requestComments(postUuid: String): Pair<List<PostComment>, Map<String, Int>> {
        val root = api.call("/v1/comments/get", JSONObject().put("post_uuid", postUuid), auth) as? JSONObject
            ?: error("Comments response was invalid")
        val comments = root.optJSONArray("comments").objects().mapNotNull(::parseComment)
        val votes = root.optJSONArray("votes").objects().mapNotNull { vote ->
            val uuid = vote.string("content_uuid") ?: return@mapNotNull null
            uuid to vote.int("vote_type")
        }.toMap()
        return comments to votes
    }

    private suspend fun requestAliases(): Map<String, String> = aliasRepository.load()

    private fun mergeLocalCommentVotes(
        incomingComments: List<PostComment>,
        incomingVotes: Map<String, Int>,
    ): Pair<List<PostComment>, Map<String, Int>> {
        if (locallyVotedComments.isEmpty()) return incomingComments to incomingVotes
        val currentComments = state.comments.associateBy(PostComment::uuid)
        val mergedVotes = incomingVotes.toMutableMap()
        val mergedComments = incomingComments.map { incoming ->
            if (incoming.uuid !in locallyVotedComments) incoming
            else {
                state.commentVotes[incoming.uuid]?.let { mergedVotes[incoming.uuid] = it }
                currentComments[incoming.uuid]?.let { current -> incoming.copy(upvoteCount = current.upvoteCount) } ?: incoming
            }
        }
        return mergedComments to mergedVotes
    }

    private suspend fun uploadCommentImage(rawUri: String, context: Context): String {
        if (rawUri.startsWith("http://") || rawUri.startsWith("https://")) {
            val (contentType, bytes) = api.downloadBinary(rawUri)
            val upload = api.call(
                "/v1/media/uploadImage",
                JSONObject().put("contentType", contentType).put("size", bytes.size),
                auth,
            ) as? JSONObject ?: error("Image upload response was invalid")
            api.putBytes(upload.getString("presignedURL"), bytes, contentType)
            return upload.getString("publicURL")
        }
        val resolver = context.contentResolver
        val uri = Uri.parse(rawUri)
        val contentType = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it > 0 }
            ?: resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }?.takeIf { it > 0 }
            ?: error("Selected image size is unavailable")
        val upload = api.call(
            "/v1/media/uploadImage",
            JSONObject().put("contentType", contentType).put("size", size),
            auth,
        ) as? JSONObject ?: error("Image upload response was invalid")
        api.putBinary(upload.getString("presignedURL"), resolver, uri, contentType, size)
        return upload.getString("publicURL")
    }
}
