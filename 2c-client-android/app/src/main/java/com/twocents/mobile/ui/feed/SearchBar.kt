package com.twocents.mobile.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.common.pressScale
import com.twocents.mobile.ui.common.rememberPressScale
import com.twocents.mobile.ui.theme.Gold
import com.twocents.mobile.ui.common.AppHaptics

@Composable
internal fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    active: Boolean,
    onAdvancedSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeInteractionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current

    LaunchedEffect(active) {
        if (!active) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(39.dp)
            .clip(RoundedCornerShape(19.5.dp))
            .background(Color(0xFF14120E).copy(alpha = 0.98f))
            .border(1.dp, Gold.copy(alpha = 0.42f), RoundedCornerShape(19.5.dp))
            .clickable(
                enabled = active,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusRequester.requestFocus() }
            .padding(start = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(CircleShape)
                .background(Gold.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Gold.copy(alpha = 0.82f),
                modifier = Modifier.size(17.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .height(39.dp)
                .focusRequester(focusRequester),
            enabled = active,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White.copy(alpha = 0.94f),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
            ),
            cursorBrush = SolidColor(Gold),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Search twocents",
                            color = Color.White.copy(alpha = 0.32f),
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(Gold.copy(alpha = 0.07f))
                .clickable(enabled = active) {
                    AppHaptics.open(view)
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onAdvancedSearch()
                },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.FilterAlt, "Advanced search", tint = Gold.copy(alpha = .72f), modifier = Modifier.size(15.dp)) }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.055f))
                .pressScale(rememberPressScale(closeInteractionSource))
                .clickable(
                    enabled = active,
                    interactionSource = closeInteractionSource,
                    indication = null,
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close search",
                tint = Color.White.copy(alpha = 0.58f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
