package com.twocents.mobile.ui.messages

import android.content.Context
import org.json.JSONObject

/** Preserves media-only DM previews when listUserDMs omits message metadata. */
internal object RoomMediaPreviewStore {
    private const val Store = "twocents-room-media-previews"

    fun put(context: Context, roomUuid: String, label: String, createdAt: String) {
        context.applicationContext.getSharedPreferences(Store, Context.MODE_PRIVATE).edit()
            .putString(roomUuid, JSONObject().put("label", label).put("createdAt", createdAt).toString())
            .apply()
    }

    fun get(context: Context, roomUuid: String): Pair<String, String>? {
        val raw = context.applicationContext.getSharedPreferences(Store, Context.MODE_PRIVATE)
            .getString(roomUuid, null) ?: return null
        return runCatching {
            JSONObject(raw).let { it.optString("label") to it.optString("createdAt") }
        }.getOrNull()?.takeIf { it.first.isNotBlank() }
    }
}
