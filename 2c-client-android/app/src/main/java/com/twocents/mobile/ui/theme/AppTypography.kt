package com.twocents.mobile.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle

/** Removes Android's legacy font padding without imposing size or weight. */
internal val NoFontPadding = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
