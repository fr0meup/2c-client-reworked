package com.twocents.mobile.data

import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.core.json.objects
import com.twocents.mobile.core.json.string
import org.json.JSONObject

/** Owns alias transport and normalization; screens remain responsible for display state. */
internal class AliasRepository(private val api: RpcApi, private val auth: AuthState) {
    suspend fun load(): Map<String, String> {
        val root = api.call("/v1/aliases/get", JSONObject(), auth) as? JSONObject
        return root?.optJSONArray("aliases").objects().mapNotNull { row ->
            val uuid = row.string("for_uuid") ?: return@mapNotNull null
            val alias = row.string("alias") ?: return@mapNotNull null
            uuid to alias
        }.toMap()
    }

    suspend fun toggle(current: Map<String, String>, userUuid: String, requestedAlias: String?): Pair<Boolean, Map<String, String>> {
        val following = userUuid in current
        val alias = requestedAlias?.trim()?.takeIf(String::isNotBlank) ?: "anon"
        val succeeded = runCatching {
            api.call(
                if (following) "/v1/aliases/unset" else "/v1/aliases/set",
                JSONObject().put("for_uuid", userUuid).apply { if (!following) put("alias", alias) },
                auth,
            )
        }.isSuccess
        val updated = if (!succeeded) current else if (following) current - userUuid else current + (userUuid to alias)
        return succeeded to updated
    }
}
