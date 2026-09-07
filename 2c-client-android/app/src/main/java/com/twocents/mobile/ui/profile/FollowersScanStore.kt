package com.twocents.mobile.ui.profile

import android.content.Context
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class FollowersScanSnapshot(
    val candidates: List<String> = emptyList(),
    val followers: List<FollowerEntry> = emptyList(),
    val completedAt: Long = 0L,
    val followEvents: Map<String, Long> = emptyMap(),
)

internal data class FollowerEntry(val alias: String?, val profile: ComposeAuthorProfile)

/** Disk format for follower discovery; UUID spelling/case is preserved verbatim. */
internal class FollowersScanStore(context: Context, private val userUuid: String) {
    private val file = File(context.applicationContext.filesDir, "followers-scan/$userUuid.json")

    fun read(): FollowersScanSnapshot = synchronized(Lock) { runCatching {
        val root = JSONObject(file.readText())
        FollowersScanSnapshot(
            candidates = root.optJSONArray("candidates").strings(),
            followers = root.optJSONArray("followers").objects().mapNotNull(::parseEntry),
            completedAt = root.optLong("completedAt"),
            followEvents = root.optJSONObject("followEvents").eventTimes(),
        )
    }.getOrDefault(FollowersScanSnapshot()) }

    fun write(snapshot: FollowersScanSnapshot): Unit = synchronized(Lock) {
        file.parentFile?.mkdirs()
        val root = JSONObject().put("completedAt", snapshot.completedAt)
            .put("followEvents", JSONObject(snapshot.followEvents))
            .put("candidates", JSONArray(snapshot.candidates))
            .put("followers", JSONArray().apply { snapshot.followers.forEach { put(it.json()) } })
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(root.toString())
        if (!temporary.renameTo(file)) { file.writeText(temporary.readText()); temporary.delete() }
    }

    /** All store instances share a lock: push, refresh, aliases and scans may overlap. */
    fun modify(transform: (FollowersScanSnapshot) -> FollowersScanSnapshot): FollowersScanSnapshot = synchronized(Lock) {
        val previous = read()
        val next = transform(previous)
        if (next != previous) write(next)
        next
    }

    fun exportJson(): JSONObject = synchronized(Lock) { if (file.isFile) runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject()) else JSONObject() }
    fun importJson(root: JSONObject) = write(
        FollowersScanSnapshot(root.optJSONArray("candidates").strings(), root.optJSONArray("followers").objects().mapNotNull(::parseEntry), root.optLong("completedAt"), root.optJSONObject("followEvents").eventTimes()),
    )
    fun clear() { synchronized(Lock) { file.delete() } }

    private companion object { val Lock = Any() }

    private fun parseEntry(raw: JSONObject): FollowerEntry? {
        val uuid = raw.optString("uuid").takeIf(String::isNotBlank) ?: return null
        return FollowerEntry(
            raw.optString("alias").takeUnless { it.isBlank() || it == "null" },
            ComposeAuthorProfile(
                uuid, raw.optDouble("balance"), raw.optInt("subscriptionType"),
                raw.optString("role").takeUnless { it.isBlank() || it == "null" },
                raw.optString("gender").takeUnless { it.isBlank() || it == "null" },
                raw.optInt("age").takeIf { raw.has("age") && !raw.isNull("age") },
                raw.optString("arena").takeUnless { it.isBlank() || it == "null" },
            ),
        )
    }

    private fun FollowerEntry.json() = JSONObject().put("uuid", profile.uuid).put("alias", alias ?: JSONObject.NULL)
        .put("balance", profile.balance).put("subscriptionType", profile.subscriptionType)
        .put("role", profile.role ?: JSONObject.NULL).put("gender", profile.gender ?: JSONObject.NULL)
        .put("age", profile.age ?: JSONObject.NULL).put("arena", profile.arena ?: JSONObject.NULL)
}

private fun JSONObject?.eventTimes(): Map<String, Long> = buildMap {
    val source = this@eventTimes ?: return@buildMap
    source.keys().forEach { uuid -> put(uuid, source.optLong(uuid)) }
}

private fun JSONArray?.strings(): List<String> = buildList {
    val source = this@strings ?: return@buildList
    for (index in 0 until source.length()) source.optString(index).takeIf(String::isNotBlank)?.let(::add)
}

private fun JSONArray?.objects(): List<JSONObject> = buildList {
    val source = this@objects ?: return@buildList
    for (index in 0 until source.length()) source.optJSONObject(index)?.let(::add)
}
