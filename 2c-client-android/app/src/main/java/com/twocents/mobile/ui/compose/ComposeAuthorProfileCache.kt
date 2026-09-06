package com.twocents.mobile.ui.compose

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Encrypted, per-user cache for the small profile slice shown in the compose
 * header. The modal can render this immediately while MainShell refreshes it
 * once during app startup.
 */
class ComposeAuthorProfileCache(context: Context) {
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    suspend fun load(userUuid: String): ComposeAuthorProfile? = withContext(Dispatchers.IO) {
        preferences.getString(keyFor(userUuid), null)?.let(::decode)
    }

    suspend fun save(profile: ComposeAuthorProfile) = withContext(Dispatchers.IO) {
        preferences.edit()
            .putString(keyFor(profile.uuid), encode(profile))
            .apply()
    }

    private fun keyFor(userUuid: String): String = "$KEY_PREFIX$userUuid"

    private fun encode(profile: ComposeAuthorProfile): String = JSONObject().apply {
        put("uuid", profile.uuid)
        put("balance", profile.balance)
        put("subscription_type", profile.subscriptionType)
        profile.role?.let { put("role", it) }
        profile.gender?.let { put("gender", it) }
        profile.age?.let { put("age", it) }
        profile.arena?.let { put("arena", it) }
    }.toString()

    private fun decode(raw: String): ComposeAuthorProfile? = runCatching {
        val json = JSONObject(raw)
        val uuid = json.optString("uuid").takeIf { it.isNotBlank() } ?: return null
        ComposeAuthorProfile(
            uuid = uuid,
            balance = json.optDouble("balance", 0.0),
            subscriptionType = json.optInt("subscription_type", 1),
            role = json.optNullableString("role"),
            gender = json.optNullableString("gender"),
            age = if (json.has("age") && !json.isNull("age")) json.optInt("age") else null,
            arena = json.optNullableString("arena"),
        )
    }.getOrNull()

    private companion object {
        const val FILE_NAME = "twocents_compose_profile"
        const val KEY_PREFIX = "profile_"
    }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
