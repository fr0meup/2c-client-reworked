package com.twocents.mobile.ui.common

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput

/** Shared drag recognizer; sheets retain their own thresholds and animation policy. */
internal fun Modifier.appBottomSheetDragHandle(
    key: Any? = Unit,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier = pointerInput(key) {
    detectVerticalDragGestures(
        onDragStart = { onDragStart() },
        onVerticalDrag = { change, amount ->
            change.consumePositionChange()
            onDrag(amount)
        },
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
    )
}
