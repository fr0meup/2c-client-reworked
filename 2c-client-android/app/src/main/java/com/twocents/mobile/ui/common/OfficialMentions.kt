package com.twocents.mobile.ui.common

import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

internal val NativeMention = Regex("(?<![\\w/@\\[])@([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?![\\w-])")
internal val LegacyMention = Regex("\\[(@[^]]+)]\\(/user/([0-9a-fA-F-]{32,36})\\)")
/** The upstream parser needs a delimiter after a final UUID, even in a mention-only message. */
internal fun terminateMentionToken(text: String): String =
    if (NativeMention.findAll(text).lastOrNull()?.range?.last == text.lastIndex && text.isNotEmpty()) "$text " else text
internal data class MutualMention(val uuid: String, val alias: String, val user: JSONObject)

/** Cache server candidates per account/query; selected UUIDs survive query changes. */
internal object MutualMentionDirectory {
    private val lock = Mutex()
    private var owner = ""
    private val queries = linkedMapOf<String, Pair<Long, List<MutualMention>>>()
    private val selected = linkedMapOf<String, MutualMention>()
    suspend fun get(api: RpcApi, auth: AuthState, query: String = ""): List<MutualMention> = lock.withLock {
        if (owner != auth.userUuid) { queries.clear(); selected.clear(); owner = auth.userUuid }
        queries[query]?.takeIf { System.currentTimeMillis() - it.first < 60_000 }?.let { return@withLock it.second }
        val root = api.call("/v2/posts/mention", JSONObject().put("query", query), auth) as? JSONObject
            ?: error("Couldn't load mention candidates")
        val rows = root.optJSONArray("candidates") ?: error("Mention response was incomplete")
        val result = (0 until rows.length()).mapNotNull { i ->
            val user = rows.optJSONObject(i) ?: return@mapNotNull null
            val uuid = user.optString("uuid")
            val label = user.optString("label")
            if (uuid.isBlank() || label.isBlank()) null else MutualMention(uuid, label, user)
        }
        result.forEach { selected[it.uuid] = it }
        queries[query] = System.currentTimeMillis() to result
        while (queries.size > 32) queries.remove(queries.keys.first())
        result
    }
    suspend fun resolve(auth: AuthState): Map<String, MutualMention> = lock.withLock {
        if (owner == auth.userUuid) selected.toMap() else emptyMap()
    }
}

internal data class OfficialMentionText(val text: String, val metadata: JSONArray)

/** Requests contain the viewer's alias; UUID tokens are the server's response format.
 * Resolve the editor's stable UUID rather than trusting its displayed net-worth label. */
internal suspend fun officialMentionText(text: String, api: RpcApi, auth: AuthState): OfficialMentionText {
    val wire = LegacyMention.replace(text) { "@${it.groupValues[2]}" }
    val ids = NativeMention.findAll(wire).map { it.groupValues[1] }.toSet()
    if (ids.isEmpty()) return OfficialMentionText(wire, JSONArray())
    var allowed = MutualMentionDirectory.resolve(auth)
    // Saved drafts can outlive the in-memory directory. Revalidate their labels.
    LegacyMention.findAll(text).filter { it.groupValues[2] !in allowed }.forEach {
        MutualMentionDirectory.get(api, auth, it.groupValues[1].removePrefix("@"))
    }
    allowed = MutualMentionDirectory.resolve(auth)
    require(ids.all(allowed::containsKey)) { "Please select this person from the mention suggestions again." }
    val metadata = JSONArray()
    ids.forEach { uuid ->
        val entry = JSONObject().put("uuid", uuid).put("text", "@${allowed.getValue(uuid).alias}")
        metadata.put(entry)
    }
    return OfficialMentionText(mentionRequestText(wire, allowed.mapValues { it.value.alias }), metadata)
}

/** Kept pure so the actual request text can be tested without Android's JSON stubs. */
internal fun mentionRequestText(text: String, aliases: Map<String, String>): String =
    NativeMention.replace(text) { match ->
        val alias = aliases.getValue(match.groupValues[1])
        require(alias.isNotBlank() && alias != "null") { "Give this person a nickname before mentioning them." }
        "@$alias"
    }

/** Normalize at parse time so every existing text/quote/sidebar renderer keeps clickable labels. */
internal fun renderOfficialMentions(text: String, mentions: JSONArray?): String {
    val entries = (0 until (mentions?.length() ?: 0)).mapNotNull { mentions?.optJSONObject(it) }
        .associateBy { it.optString("uuid") }
    return NativeMention.replace(text) { match ->
        val uuid = match.groupValues[1]
        val user = entries[uuid]
        val label = user?.optString("alias")?.takeUnless { it.isBlank() || it == "null" }
            ?: user?.takeUnless { it.isNull("balance") }?.let { NumberFormat.getIntegerInstance(Locale.US).format(it.optDouble("balance")) }
            ?: uuid
        "[@${label.replace("]", "").replace("\n", " ")}](/user/$uuid)"
    }
}
