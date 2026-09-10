package com.twocents.mobile.ui.feed

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * User-scoped presentation preference for hiding unverified top-level content.
 * The observable cache lets every visible feed react immediately while shared
 * preferences keeps the choice stable across process restarts.
 */
internal object VerifiedContentFilterStore {
    // News/service content and picks are deliberately exempt, regardless of
    // the account's subscription flag. Quotes are still filtered only outside.
    fun allows(post: FeedPost): Boolean = post.author.subscriptionType > 0 ||
        post.postType == 7 || post.authorUuid.equals("news", ignoreCase = true) ||
        post.author.role.equals("news", ignoreCase = true)

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
