package com.twocents.mobile.ui.feed

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * User-scoped presentation preference for hiding unverified top-level content.
 * The observable cache lets every visible feed react immediately while shared
 * preferences keeps the choice stable across process restarts.
 */
internal object VerifiedContentFilterStore {
    private val observed = mutableStateMapOf<String, Boolean>()

    private fun preferences(context: Context, userUuid: String) =
        context.applicationContext.getSharedPreferences("twocents-feed-$userUuid", Context.MODE_PRIVATE)

    fun isEnabled(context: Context, userUuid: String): Boolean = observed.getOrPut(userUuid) {
        preferences(context, userUuid).getBoolean("verified-only", false)
    }

    fun setEnabled(context: Context, userUuid: String, enabled: Boolean) {
        observed[userUuid] = enabled
        preferences(context, userUuid).edit().putBoolean("verified-only", enabled).apply()
    }
}
