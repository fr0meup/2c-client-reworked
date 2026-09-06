package com.twocents.mobile.ui.compose

import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import org.json.JSONObject

/** The small, compose-only slice of the authenticated profile used by the RN header. */
data class ComposeAuthorProfile(
    val uuid: String,
    val balance: Double = 0.0,
    val subscriptionType: Int = 1,
    val role: String? = null,
    val gender: String? = null,
    val age: Int? = null,
    val arena: String? = null,
)

suspend fun loadComposeAuthorProfile(
    api: RpcApi,
    auth: AuthState,
): ComposeAuthorProfile? {
    val result = api.call(
        method = "/v2/users/get",
        params = JSONObject()
            .put("user_uuid", auth.userUuid)
            .put("posts_limit", 20)
            .put("comments_limit", 20)
            .put("voted_posts_limit", 20)
            .put("pick_votes_limit", 20),
        auth = auth,
    ) as? JSONObject ?: return null

    val user = result.optJSONObject("user")
    if (user == null) return null
    return ComposeAuthorProfile(
        uuid = user.optString("uuid", auth.userUuid),
        balance = user.optDouble("balance", 0.0),
        subscriptionType = user.optInt("subscription_type", 1),
        role = user.optNullableString("role"),
        gender = user.optNullableString("gender"),
        age = if (user.has("age") && !user.isNull("age")) user.optInt("age") else null,
        arena = user.optNullableString("arena"),
    )
}

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
