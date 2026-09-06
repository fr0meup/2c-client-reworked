package com.twocents.mobile.notifications

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject

internal enum class NotificationCategory(val label: String) {
    Replies("Replies"),
    Messages("Direct messages"),
    Votes("Votes"),
    Follows("Follows"),
    Updates("Platform updates"),
}

/** Local category controls for Android notifications and the in-app feed. */
internal object NotificationPreferences {
    private const val Store = "twocents-notification-preferences"
    private val observed = mutableStateMapOf<String, Boolean>()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(Store, Context.MODE_PRIVATE)
    private fun key(category: NotificationCategory, push: Boolean) = "${if (push) "push" else "in-app"}-${category.name}"

    fun enabled(context: Context, category: NotificationCategory, push: Boolean): Boolean =
        observed.getOrPut(key(category, push)) { prefs(context).getBoolean(key(category, push), true) }

    fun cachedEnabled(category: NotificationCategory, push: Boolean): Boolean =
        observed[key(category, push)] ?: true

    fun setEnabled(context: Context, category: NotificationCategory, push: Boolean, enabled: Boolean) {
        observed[key(category, push)] = enabled
        prefs(context).edit().putBoolean(key(category, push), enabled).apply()
    }

    fun enabledSet(context: Context, push: Boolean): Set<NotificationCategory> =
        NotificationCategory.entries.filterTo(linkedSetOf()) { enabled(context, it, push) }

    fun exportJson(context: Context) = JSONObject().apply {
        put("push", JSONArray(enabledSet(context, true).map(NotificationCategory::name)))
        put("in_app", JSONArray(enabledSet(context, false).map(NotificationCategory::name)))
    }

    fun importJson(context: Context, root: JSONObject) {
        fun read(key: String): Set<String>? = root.optJSONArray(key)?.let { array ->
            buildSet { for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
        }
        val push = read("push")
        val inApp = read("in_app")
        NotificationCategory.entries.forEach { category ->
            push?.let { setEnabled(context, category, true, category.name in it) }
            inApp?.let { setEnabled(context, category, false, category.name in it) }
        }
    }
}

internal fun notificationCategory(type: String, roomUuid: String? = null, roomType: String? = null): NotificationCategory {
    val normalized = type.lowercase()
    return when {
        normalized.contains("reply") -> NotificationCategory.Replies
        roomUuid != null || normalized.contains("message") || roomType.orEmpty().contains("dm", true) -> NotificationCategory.Messages
        normalized.contains("vote") -> NotificationCategory.Votes
        normalized.contains("follow") || normalized.contains("alias") -> NotificationCategory.Follows
        else -> NotificationCategory.Updates
    }
}
