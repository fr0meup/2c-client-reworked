package com.twocents.mobile.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Staged with the profile form; opening the editor never creates a self-follow. */
@Composable
internal fun SelfFollowEditor(
    following: Boolean,
    nickname: String,
    available: Boolean,
    saving: Boolean,
    onFollowingChanged: (Boolean) -> Unit,
    onNicknameChanged: (String) -> Unit,
) {
    val gold = Color(0xFFC8A44D)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Follow yourself", modifier = Modifier.weight(1f), color = Color.White.copy(alpha = .8f),
            fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        Switch(checked = following, onCheckedChange = onFollowingChanged, enabled = available && !saving,
            colors = SwitchDefaults.colors(checkedThumbColor = gold, checkedTrackColor = gold.copy(alpha = .25f)))
    }
    if (!available) {
        Text("Couldn't load your self-follow status. Reopen Edit profile to try again.",
            color = Color(0xFFFB7185), fontSize = 11.sp, lineHeight = 15.sp)
    } else if (following) {
        EditField("Your nickname", nickname, { if (!saving) onNicknameChanged(it.take(30)) }, "Enter a nickname")
    }
    Text("Optional and off by default. Follow yourself to give yourself a nickname; you can edit it or unfollow here.",
        color = Color.White.copy(alpha = .45f), fontSize = 11.sp, lineHeight = 15.sp)
    Text("Nickname changes may take some time to appear throughout the app. Refresh or reopen the affected page if you still see the old name.",
        color = gold.copy(alpha = .8f), fontSize = 11.sp, lineHeight = 15.sp)
}
