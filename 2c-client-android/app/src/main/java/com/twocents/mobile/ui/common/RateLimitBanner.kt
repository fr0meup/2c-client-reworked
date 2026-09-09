package com.twocents.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.twocents.mobile.ApiRateLimitNotice

/** A retained transport warning, not a timed toast: survives refresh and navigation. */
@Composable
internal fun RateLimitBanner() {
    val active by ApiRateLimitNotice.active.collectAsState()
    if (!active) return
    Popup(alignment = Alignment.TopCenter, properties = PopupProperties(focusable = false)) {
        Text(
            "Rate limited by twocents\nWait before retrying. Some requests are temporarily blocked.",
            color = Color(0xFFFB7185), fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.statusBarsPadding()
                .padding(top = AppToast.topOffsetDp.dp, start = 12.dp, end = 12.dp)
                .widthIn(max = 330.dp)
                .background(Color(0xFF35171E), RoundedCornerShape(12.dp))
                .border(.8.dp, Color(0xFFFB7185).copy(alpha = .3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}
