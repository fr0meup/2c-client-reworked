package com.twocents.mobile.ui.common

import android.os.Build
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur

// Expo BlurView intensity=50 with its Android reduction factor of 4.
private const val DEFAULT_BLUR_RADIUS = 12.5f
// Expo's dark tint at intensity=50: 255 * .50 * .69 = 87 alpha.
private val DEFAULT_OVERLAY_COLOR = AndroidColor.argb(87, 25, 25, 25)

/**
 * Backdrop blur matching the RN dock: blur the activity behind this layer,
 * then apply the app's dark translucent tint on top.
 */
@Composable
fun FrostedGlassBackground(
    modifier: Modifier = Modifier,
    blurRadius: Float = DEFAULT_BLUR_RADIUS,
    overlayColor: Int = DEFAULT_OVERLAY_COLOR,
) {
    val rootView = LocalView.current.rootView
    val targetRoot = rootView.findViewById<ViewGroup>(android.R.id.content)
        ?: (rootView as? ViewGroup)

    if (targetRoot != null) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                BlurView(context).apply {
                    // BlurView snapshots its target by drawing the root. Doing
                    // that from AndroidView's layout pass makes Compose try to
                    // measure itself recursively, so initialize after attach
                    // and after the current traversal has completed.
                    doOnAttach { attachedView ->
                        attachedView.post {
                            runCatching {
                                val blurView = attachedView as BlurView
                                val facade = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    blurView.setupWith(targetRoot, RenderEffectBlur())
                                } else {
                                    blurView.setupWith(targetRoot)
                                }
                                facade
                                    .setFrameClearDrawable(rootView.background)
                                    .setBlurRadius(blurRadius)
                                    .setOverlayColor(overlayColor)
                                blurView.invalidate()
                            }
                        }
                    }
                }
            },
            update = { blurView ->
                blurView
                    .setBlurRadius(blurRadius)
                    .setOverlayColor(overlayColor)
                blurView.invalidate()
            },
        )
    }
}
