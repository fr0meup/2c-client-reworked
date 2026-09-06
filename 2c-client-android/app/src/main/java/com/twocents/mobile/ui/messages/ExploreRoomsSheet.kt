package com.twocents.mobile.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.notifications.NotificationIcons
import com.twocents.mobile.ui.theme.Gold
import kotlinx.coroutines.launch
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExploreRoomsSheet(
    controller: MessagesController,
    onDismiss: () -> Unit,
    onOpenRoom: (RoomSummary) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rooms by remember { mutableStateOf<List<RoomSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var joining by remember { mutableStateOf<String?>(null) }
    val joined = remember(controller.state.rooms, controller.state.dms) { (controller.state.rooms + controller.state.dms).mapTo(hashSetOf()) { it.uuid } }
    LaunchedEffect(controller) { rooms = controller.exploreRooms(); loading = false }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141410),
        scrimColor = Color.Black.copy(alpha = .75f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = { Box(Modifier.padding(top = 8.dp, bottom = 5.dp).width(36.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f))) },
    ) {
        val sheetView = LocalView.current
        DisposableEffect(sheetView) {
            val window = (sheetView.parent as? DialogWindowProvider)?.window
            val oldNavigationColor = window?.navigationBarColor
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            window?.navigationBarColor = android.graphics.Color.TRANSPARENT
            window?.isNavigationBarContrastEnforced = false
            window?.let { WindowInsetsControllerCompat(it, sheetView).isAppearanceLightNavigationBars = false }
            onDispose { if (oldNavigationColor != null) window?.navigationBarColor = oldNavigationColor }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Explore, null, tint = Gold, modifier = Modifier.size(18.dp))
            Text("Explore rooms", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, "Close", tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(18.dp)) }
        }
        when {
            loading -> Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Gold, strokeWidth = 2.dp) }
            rooms.isEmpty() -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { Text("No public rooms available", color = Color.White.copy(alpha = .4f), fontSize = 14.sp) }
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 610.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rooms, key = { it.uuid }) { room ->
                    val alreadyJoined = room.uuid in joined
                    val eligible = room.requirements.all { it.met }
                    val canJoin = eligible && !room.isPrivate
                    val requirement = room.requirements.firstOrNull { !it.met }?.label
                    val busy = joining == room.uuid
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .03f))
                            .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(18.dp))
                            .alpha(if (canJoin || alreadyJoined) 1f else .38f)
                            .clickable(enabled = joining == null && (canJoin || alreadyJoined)) {
                                if (alreadyJoined) { onDismiss(); onOpenRoom(room) }
                                else scope.launch {
                                    joining = room.uuid
                                    controller.joinRoom(room)?.let { joinedRoom -> onDismiss(); onOpenRoom(joinedRoom) }
                                    joining = null
                                }
                            }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(room.gradients.ifEmpty { listOf(Color(0xFF302418), Color(0xFF14120F)) })))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (room.isPrivate) Icon(Icons.Outlined.Lock, null, tint = Color.White.copy(alpha = .4f), modifier = Modifier.size(13.dp))
                                else Text("#", color = Color.White.copy(alpha = .4f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(room.name, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp).weight(1f))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(NotificationIcons.Users, null, tint = Color.White.copy(alpha = .4f), modifier = Modifier.size(12.dp))
                                Text(room.memberCount.toString(), color = Color.White.copy(alpha = .4f), fontSize = 11.5.sp)
                                Text(requirement?.takeIf(String::isNotBlank) ?: if (room.isPrivate) "Private" else "Open", color = if (requirement != null || room.isPrivate) Color.White.copy(alpha = .45f) else Color(0xFF34D399), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        when {
                            busy -> CircularProgressIndicator(Modifier.size(17.dp), color = Gold, strokeWidth = 2.dp)
                            alreadyJoined -> Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Check, null, tint = Color(0xFF34D399), modifier = Modifier.size(14.dp)); Text("Joined", color = Color(0xFF34D399), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp)) }
                            !canJoin -> Icon(Icons.Outlined.Lock, null, tint = Color.White.copy(alpha = .3f), modifier = Modifier.size(15.dp))
                            else -> Text("›", color = Color.White.copy(alpha = .4f), fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}
