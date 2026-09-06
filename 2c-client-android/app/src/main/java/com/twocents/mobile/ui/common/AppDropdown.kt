package com.twocents.mobile.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.window.PopupProperties

/** Shared dropdown host; callers retain their exact dimensions and styling. */
@Composable
internal fun AppDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    shape: Shape,
    containerColor: Color,
    tonalElevation: Dp,
    shadowElevation: Dp,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    border: BorderStroke? = null,
    verticalTrim: Dp = Dp.Unspecified,
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        // DropdownMenu performs intrinsic sizing internally. A custom measuring
        // modifier here would ask its SubcomposeLayout child for intrinsics and
        // crash at runtime, so verticalTrim remains a compatibility hint only.
        modifier = modifier,
        offset = offset,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        properties = properties,
        content = {
            if (verticalTrim != Dp.Unspecified && verticalTrim > Dp.Hairline) {
                Column(
                    Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val trimPx = verticalTrim.roundToPx().coerceAtMost(placeable.height / 3)
                        layout(placeable.width, (placeable.height - trimPx * 2).coerceAtLeast(0)) {
                            placeable.placeRelative(0, -trimPx)
                        }
                    },
                    content = content,
                )
            } else content()
        },
    )
}
