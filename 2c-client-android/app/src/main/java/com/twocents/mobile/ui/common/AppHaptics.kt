package com.twocents.mobile.ui.common

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View

internal enum class HapticIntent { Navigate, Open, Toggle, Confirm }

internal object AppHaptics {
    private const val Preferences = "twocents-accessibility"
    private const val Enabled = "haptics-enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .getBoolean(Enabled, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .edit().putBoolean(Enabled, enabled).apply()
    }

    /** Navigation between screens or peer tabs. */
    fun navigate(view: View) = perform(view, HapticFeedbackConstants.VIRTUAL_KEY)

    /** Revealing a menu, sheet, sidebar, or other secondary surface. */
    fun open(view: View) = perform(view, HapticFeedbackConstants.CONTEXT_CLICK)

    /** A reversible state mutation such as voting or toggling a setting. */
    fun toggle(view: View) = perform(view, HapticFeedbackConstants.CONTEXT_CLICK)

    /** A committed action such as sending or submitting. */
    fun confirm(view: View) = perform(view, HapticFeedbackConstants.CONFIRM)

    fun perform(view: View, intent: HapticIntent?) = when (intent) {
        HapticIntent.Navigate -> navigate(view)
        HapticIntent.Open -> open(view)
        HapticIntent.Toggle -> toggle(view)
        HapticIntent.Confirm -> confirm(view)
        null -> Unit
    }

    private fun perform(view: View, constant: Int) {
        if (isEnabled(view.context)) view.performHapticFeedback(constant)
    }
}
