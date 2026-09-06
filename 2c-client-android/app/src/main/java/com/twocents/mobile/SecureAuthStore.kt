package com.twocents.mobile

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecureAuthStore(context: Context) {
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

    suspend fun load(): AuthState? = withContext(Dispatchers.IO) {
        val token = preferences.getString(KEY_TOKEN, null)
        val userUuid = preferences.getString(KEY_USER_UUID, null)
        val secretKey = preferences.getString(KEY_SECRET_KEY, null)
        if (token.isNullOrBlank() || userUuid.isNullOrBlank() || secretKey.isNullOrBlank()) {
            null
        } else {
            AuthState(token, userUuid, secretKey)
        }
    }

    suspend fun save(auth: AuthState, persist: Boolean) = withContext(Dispatchers.IO) {
        preferences.edit()
            .clear()
            .apply {
                if (persist) {
                    putString(KEY_TOKEN, auth.token)
                    putString(KEY_USER_UUID, auth.userUuid)
                    putString(KEY_SECRET_KEY, auth.secretKey)
                }
            }
            .apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "twocents_encrypted_auth"
        const val KEY_TOKEN = "token"
        const val KEY_USER_UUID = "user_uuid"
        const val KEY_SECRET_KEY = "secret_key"
    }
}
