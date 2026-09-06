package com.twocents.mobile.ui.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.mutableStateOf

/** Preferences that affect actions authored by the signed-in user. */
internal object InteractionPreferences {
    private const val Store = "twocents-interaction-preferences"
    private const val AutoLikeOwnContent = "auto-like-own-content"
    private const val AutoPlayVideos = "auto-play-videos"
    private const val WifiOnlyMedia = "wifi-only-media"
    private val observedAutoPlay = mutableStateOf<Boolean?>(null)
    private val observedWifiOnly = mutableStateOf<Boolean?>(null)

    fun autoLikeOwnContent(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(Store, Context.MODE_PRIVATE)
            .getBoolean(AutoLikeOwnContent, true)

    fun setAutoLikeOwnContent(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(Store, Context.MODE_PRIVATE)
            .edit().putBoolean(AutoLikeOwnContent, enabled).apply()
    }

    fun autoPlayVideos(context: Context): Boolean = observedAutoPlay.value ?: context.applicationContext
        .getSharedPreferences(Store, Context.MODE_PRIVATE).getBoolean(AutoPlayVideos, false)
        .also { observedAutoPlay.value = it }

    fun setAutoPlayVideos(context: Context, enabled: Boolean) {
        observedAutoPlay.value = enabled
        context.applicationContext.getSharedPreferences(Store, Context.MODE_PRIVATE)
            .edit().putBoolean(AutoPlayVideos, enabled).apply()
    }

    fun wifiOnlyMedia(context: Context): Boolean = observedWifiOnly.value ?: context.applicationContext
        .getSharedPreferences(Store, Context.MODE_PRIVATE).getBoolean(WifiOnlyMedia, false)
        .also { observedWifiOnly.value = it }

    fun setWifiOnlyMedia(context: Context, enabled: Boolean) {
        observedWifiOnly.value = enabled
        context.applicationContext.getSharedPreferences(Store, Context.MODE_PRIVATE)
            .edit().putBoolean(WifiOnlyMedia, enabled).apply()
    }

    fun automaticMediaAllowed(context: Context): Boolean {
        if (!wifiOnlyMedia(context)) return true
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
