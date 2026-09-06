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
)

internal data class FollowerEntry(val alias: String?, val profile: ComposeAuthorProfile)

/** Disk format for follower discovery; UUID spelling/case is preserved verbatim. */
internal class FollowersScanStore(context: Context, private val userUuid: String) {
    private val file = File(context.applicationContext.filesDir, "followers-scan/$userUuid.json")

    @Synchronized
    fun read(): FollowersScanSnapshot = runCatching {
        val root = JSONObject(file.readText())
        FollowersScanSnapshot(
            candidates = root.optJSONArray("candidates").strings(),
            followers = root.optJSONArray("followers").objects().mapNotNull(::parseEntry),
            completedAt = root.optLong("completedAt"),
        )
    }.getOrDefault(FollowersScanSnapshot())

    @Synchronized
    fun write(snapshot: FollowersScanSnapshot) {
        file.parentFile?.mkdirs()
        val root = JSONObject().put("completedAt", snapshot.completedAt)
            .put("candidates", JSONArray(snapshot.candidates))
            .put("followers", JSONArray().apply { snapshot.followers.forEach { put(it.json()) } })
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(root.toString())
        if (!temporary.renameTo(file)) { file.writeText(temporary.readText()); temporary.delete() }
    }

    fun exportJson(): JSONObject = if (file.isFile) runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject()) else JSONObject()
    fun importJson(root: JSONObject) = write(
        FollowersScanSnapshot(root.optJSONArray("candidates").strings(), root.optJSONArray("followers").objects().mapNotNull(::parseEntry), root.optLong("completedAt")),
    )
    fun clear() { file.delete() }

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

private fun JSONArray?.strings(): List<String> = buildList {
    val source = this@strings ?: return@buildList
    for (index in 0 until source.length()) source.optString(index).takeIf(String::isNotBlank)?.let(::add)
}

private fun JSONArray?.objects(): List<JSONObject> = buildList {
    val source = this@objects ?: return@buildList
    for (index in 0 until source.length()) source.optJSONObject(index)?.let(::add)
}
