package com.twocents.mobile.ui.common

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Applies the app's dialog-window policy without owning dialog content or motion.
 * Every caller supplies its existing navigation color so visual behavior is retained.
 */
@Composable
internal fun EdgeToEdgeDialogWindow(
    navigationBarColor: Int = Color.TRANSPARENT,
    decorFitsSystemWindows: Boolean? = false,
    darkNavigationIcons: Boolean = false,
) {
    val view = LocalView.current
    DisposableEffect(view, navigationBarColor, decorFitsSystemWindows, darkNavigationIcons) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val oldNavigationColor = window?.navigationBarColor
        if (decorFitsSystemWindows != null) {
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, decorFitsSystemWindows) }
        }
        window?.setWindowAnimations(0)
        window?.navigationBarColor = navigationBarColor
        window?.isNavigationBarContrastEnforced = false
        window?.let { WindowInsetsControllerCompat(it, view).isAppearanceLightNavigationBars = darkNavigationIcons }
        onDispose {
            if (oldNavigationColor != null) window?.navigationBarColor = oldNavigationColor
        }
    }
}
