package com.twocents.mobile.ui.common

/** A Dialog is above shell navigation; close ticker dialogs before a nested route opens. */
internal object TickerOverlayExit {
    private val callbacks = LinkedHashSet<() -> Unit>()
    fun register(callback: () -> Unit) { callbacks += callback }
    fun unregister(callback: () -> Unit) { callbacks -= callback }
    fun dismissAll() { callbacks.toList().forEach { it() } }
}
