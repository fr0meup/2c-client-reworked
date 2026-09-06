package com.twocents.mobile.ui.messages

import android.content.Context

/** Matches the web client's local "appear offline" mode. */
internal object OfflineModeStore {
    private fun preferences(context: Context, userUuid: String) =
        context.applicationContext.getSharedPreferences("twocents-sidebar-$userUuid", Context.MODE_PRIVATE)

    fun isEnabled(context: Context, userUuid: String): Boolean =
        preferences(context, userUuid).getBoolean("offline-mode", false)

    fun setEnabled(context: Context, userUuid: String, enabled: Boolean) {
        preferences(context, userUuid).edit().putBoolean("offline-mode", enabled).apply()
    }
}
