package com.twocents.mobile.ui.feed

import android.content.Context

internal class MutedUsersStore(context: Context, private val authUuid: String) {
    private val preferences = context.applicationContext.getSharedPreferences("muted_users", Context.MODE_PRIVATE)
    private val key = "muted:$authUuid"

    fun all(): Set<String> = preferences.getStringSet(key, emptySet()).orEmpty().toSet()

    fun isMuted(uuid: String): Boolean = uuid in all()

    fun setMuted(uuid: String, muted: Boolean): Boolean {
        val next = all().toMutableSet().apply { if (muted) add(uuid) else remove(uuid) }
        return preferences.edit().putStringSet(key, next).commit()
    }
}
