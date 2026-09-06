package com.twocents.mobile.data

import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import org.json.JSONObject

/** API-only vote operations. Optimistic state and rollback stay with each feature controller. */
internal class VoteRepository(private val api: RpcApi, private val auth: AuthState) {
    suspend fun post(postUuid: String, vote: Int): Boolean = call("/v1/posts/vote", JSONObject().put("post_uuid", postUuid).put("vote_type", vote))
    suspend fun comment(postUuid: String, commentUuid: String, vote: Int): Boolean = call(
        "/v1/comments/vote",
        JSONObject().put("comment_uuid", commentUuid).put("post_uuid", postUuid).put("vote_type", vote),
    )
    suspend fun poll(postUuid: String, option: Int): Boolean = call("/v1/polls/vote", JSONObject().put("post_uuid", postUuid).put("option", option))
    suspend fun likert(postUuid: String, option: Int): Boolean = call("/v1/likert/vote", JSONObject().put("postUuid", postUuid).put("post_uuid", postUuid).put("option", option))
    suspend fun pick(postUuid: String, vote: String): Boolean = call("/v1/posts/votePick", JSONObject().put("post_uuid", postUuid).put("vote", vote))

    private suspend fun call(method: String, params: JSONObject): Boolean = runCatching {
        api.call(method, params, auth)
    }.isSuccess
}
