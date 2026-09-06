package com.twocents.mobile.ui.settings

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.imageLoader
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.feed.MutedUsersStore
import com.twocents.mobile.ui.feed.AdvancedSearchMemoryIndex
import com.twocents.mobile.ui.feed.AdvancedSearchIndex
import com.twocents.mobile.ui.feed.VerifiedContentFilterStore
import com.twocents.mobile.ui.profile.FollowersScanStore
import com.twocents.mobile.ui.profile.FollowersScanner
import com.twocents.mobile.ui.feed.FeedAuthor
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.feed.FeedPostMeta
import com.twocents.mobile.ui.feed.FeedUserMetaPill
import com.twocents.mobile.ui.feed.UserMetaPill
import com.twocents.mobile.ui.common.toUserDisplay
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.messages.OfflineModeStore
import com.twocents.mobile.ui.compose.GifLibrary
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.AppToast
import com.twocents.mobile.ui.common.AppBackgroundTasks
import com.twocents.mobile.ui.common.friendlyError
import com.twocents.mobile.notifications.NotificationHistoryStore
import com.twocents.mobile.notifications.NotificationCategory
import com.twocents.mobile.notifications.NotificationPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileOutputStream
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import android.util.JsonWriter

private val SettingsGold = Color(0xFFC8A44D)
private data class HiddenUser(val uuid: String, val profile: ComposeAuthorProfile)

@Composable
internal fun SettingsScreen(auth: AuthState, api: RpcApi, onBack: () -> Unit, onOpenFeedback: () -> Unit, onOfflineChanged: (Boolean) -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appVersion = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    val muteStore = remember(auth.userUuid) { MutedUsersStore(context, auth.userUuid) }
    var offline by remember { mutableStateOf(OfflineModeStore.isEnabled(context, auth.userUuid)) }
    var haptics by remember { mutableStateOf(AppHaptics.isEnabled(context)) }
    var verifiedOnly by remember(auth.userUuid) { mutableStateOf(VerifiedContentFilterStore.isEnabled(context, auth.userUuid)) }
    var autoLikeOwnContent by remember { mutableStateOf(InteractionPreferences.autoLikeOwnContent(context)) }
    var autoPlayVideos by remember { mutableStateOf(InteractionPreferences.autoPlayVideos(context)) }
    var wifiOnlyMedia by remember { mutableStateOf(InteractionPreferences.wifiOnlyMedia(context)) }
    var pushCategories by remember { mutableStateOf(NotificationPreferences.enabledSet(context, push = true)) }
    var inAppCategories by remember { mutableStateOf(NotificationPreferences.enabledSet(context, push = false)) }
    var searchIndexCount by remember { mutableIntStateOf(AdvancedSearchMemoryIndex.count()) }
    var muted by remember { mutableStateOf<List<HiddenUser>>(emptyList()) }
    var blocked by remember { mutableStateOf<List<HiddenUser>>(emptyList()) }
    var privacyExpanded by remember { mutableStateOf(false) }
    var contactExpanded by remember { mutableStateOf(false) }
    var connectionsExpanded by remember { mutableStateOf(false) }
    var storageExpanded by remember { mutableStateOf(false) }
    var pushExpanded by remember { mutableStateOf(false) }
    var inAppExpanded by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf<String?>(null) }
    val storageOperation by LocalDataOperation.active.collectAsState()
    val view = androidx.compose.ui.platform.LocalView.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!LocalDataOperation.tryStart("Exporting data")) {
            AppToast.error("${LocalDataOperation.active.value ?: "A storage operation"} is already in progress")
            return@rememberLauncherForActivityResult
        }
        val offlineForExport = offline
        val verifiedForExport = verifiedOnly
        val autoLikeForExport = autoLikeOwnContent
        val autoPlayForExport = autoPlayVideos
        val wifiOnlyForExport = wifiOnlyMedia
        val hapticsForExport = haptics
        val mutedForExport = muteStore.all()
        AppBackgroundTasks.launch {
            // This process-lived IO scope survives leaving Settings. The UI remains
            // interactive and the pinned toast is replaced only by a terminal result.
            val toastId = AppToast.progress("Exporting local data…")
            try {
                runCatching {
                    exportLocalData(
                    context = context,
                    destination = uri,
                    userUuid = auth.userUuid,
                    state = LocalDataExportState(
                        offlineForExport, verifiedForExport, autoLikeForExport, autoPlayForExport,
                        wifiOnlyForExport, hapticsForExport, mutedForExport,
                    ),
                    )
                }
                    .onSuccess { result ->
                    AppToast.success(
                        if (result.skippedDraftMedia == 0) "Backup exported"
                        else "Backup exported · ${result.skippedDraftMedia} inaccessible draft attachment${if (result.skippedDraftMedia == 1) "" else "s"} skipped",
                        toastId,
                    )
                    }
                    .onFailure { AppToast.error(friendlyError(it, "Export failed"), toastId) }
            } finally {
                LocalDataOperation.finish()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!LocalDataOperation.tryStart("Importing data")) {
            AppToast.error("${LocalDataOperation.active.value ?: "A storage operation"} is already in progress")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val toastId = AppToast.progress("Importing local data…")
            try {
                runCatching {
                    val imported = withContext(Dispatchers.IO) { importLocalData(context, uri) }
                muteStore.all().forEach { muteStore.setMuted(it, false) }
                imported.muted.forEach { muteStore.setMuted(it, true) }
                val importedOffline = imported.offline
                OfflineModeStore.setEnabled(context, auth.userUuid, importedOffline); offline = importedOffline; onOfflineChanged(importedOffline)
                val importedVerified = imported.verifiedOnly
                VerifiedContentFilterStore.setEnabled(context, auth.userUuid, importedVerified); verifiedOnly = importedVerified
                val importedAutoLike = imported.autoLikeOwnContent
                InteractionPreferences.setAutoLikeOwnContent(context, importedAutoLike); autoLikeOwnContent = importedAutoLike
                val importedAutoPlay = imported.autoPlayVideos
                InteractionPreferences.setAutoPlayVideos(context, importedAutoPlay); autoPlayVideos = importedAutoPlay
                val importedWifiOnly = imported.wifiOnlyMedia
                InteractionPreferences.setWifiOnlyMedia(context, importedWifiOnly); wifiOnlyMedia = importedWifiOnly
                val importedHaptics = imported.haptics
                AppHaptics.setEnabled(context, importedHaptics); haptics = importedHaptics
                imported.notificationPreferences?.takeIf(String::isNotBlank)?.let {
                    NotificationPreferences.importJson(context, JSONObject(it))
                    pushCategories = NotificationPreferences.enabledSet(context, push = true)
                    inAppCategories = NotificationPreferences.enabledSet(context, push = false)
                }
                searchIndexCount = AdvancedSearchMemoryIndex.count()
                imported.activityStats?.takeIf { it.isNotBlank() }?.let {
                    NotificationHistoryStore(context, auth.userUuid).importJson(JSONObject(it))
                }
                imported.followersScan?.takeIf { it.isNotBlank() && it != "{}" }?.let {
                    FollowersScanStore(context, auth.userUuid).importJson(JSONObject(it))
                    FollowersScanner.reloadStored(context, auth.userUuid)
                }
                imported.gifLibrary?.let { GifLibrary.importJson(context, it) }
                withContext(Dispatchers.IO) {
                    val draftRoot = java.io.File(context.filesDir, "compose-drafts")
                    java.io.File(draftRoot, "media").deleteRecursively()
                    val drafts = imported.drafts?.takeIf { it.isNotBlank() && it != "null" }
                    if (drafts == null) {
                        java.io.File(draftRoot, "drafts.json").delete()
                        java.io.File(draftRoot, "restored-media").deleteRecursively()
                    } else {
                        java.io.File(draftRoot, "drafts.json").apply { parentFile?.mkdirs(); writeText(drafts) }
                    }
                }
                }.onSuccess { AppToast.success("Backup imported", toastId) }
                    .onFailure { AppToast.error(friendlyError(it, "Import failed"), toastId) }
            } finally {
                LocalDataOperation.finish()
            }
        }
    }

    LaunchedEffect(auth.userUuid) {
        val root = runCatching { api.call("/v1/users/blocked", JSONObject(), auth) as? JSONObject }.getOrNull()
        fun users(array: JSONArray?): List<HiddenUser> = buildList {
            if (array != null) for (i in 0 until array.length()) {
                val raw = array.opt(i)
                when (raw) {
                    is String -> add(HiddenUser(raw, ComposeAuthorProfile(uuid = raw, subscriptionType = 0)))
                    is JSONObject -> raw.toHiddenUser()?.let(::add)
                }
            }
        }
        blocked = users(root?.optJSONArray("blocked")).ifEmpty { users(root?.optJSONArray("blocked_users")) }.ifEmpty { users(root?.optJSONArray("users")) }
        muted = coroutineScope {
            muteStore.all().sorted().map { uuid ->
                async {
                    val user = runCatching {
                        (api.call("/v2/users/get", JSONObject().put("user_uuid", uuid), auth) as? JSONObject)?.optJSONObject("user")
                    }.getOrNull()
                    user?.toHiddenUser(uuid) ?: HiddenUser(uuid, ComposeAuthorProfile(uuid = uuid, subscriptionType = 0))
                }
            }.awaitAll()
        }
    }

    Column(Modifier.fillMaxSize().background(Background).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(57.dp).background(Background)) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(48.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)).border(1.dp, Color.White.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(19.dp))
                }
            }
            Text("Settings", color = Color.White, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .06f)))
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, top = 14.dp, end = 12.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionLabel("Content & interaction") }
            item {
                SettingsGroup {
                    ToggleSetting(
                        Icons.Outlined.VerifiedUser,
                        "Show verified accounts only",
                        "Only show posts from verified accounts in your feeds.",
                        verifiedOnly,
                    ) {
                        verifiedOnly = it
                        VerifiedContentFilterStore.setEnabled(context, auth.userUuid, it)
                    }
                    SettingsDivider()
                    ToggleSetting(Icons.Outlined.ThumbUp, "Auto-like my posts and comments", "Automatically upvote new content after it is published", autoLikeOwnContent) {
                        autoLikeOwnContent = it
                        InteractionPreferences.setAutoLikeOwnContent(context, it)
                    }
                    SettingsDivider()
                    ToggleSetting(Icons.Outlined.PlayCircle, "Autoplay videos", "Play videos automatically when they enter the viewport", autoPlayVideos) {
                        autoPlayVideos = it
                        InteractionPreferences.setAutoPlayVideos(context, it)
                    }
                    SettingsDivider()
                    ToggleSetting(Icons.Outlined.Wifi, "Wi-Fi-only automatic media", "Limit media preloading and video autoplay to Wi-Fi", wifiOnlyMedia) {
                        wifiOnlyMedia = it
                        InteractionPreferences.setWifiOnlyMedia(context, it)
                    }
                    SettingsDivider()
                    ToggleSetting(Icons.Outlined.TouchApp, "Haptic feedback", "Subtle feedback for navigation and important actions", haptics) {
                        haptics = it
                        AppHaptics.setEnabled(context, it)
                    }
                }
            }
            item { SectionLabel("Privacy & presence") }
            item {
                SettingsGroup {
                    ToggleSetting(Icons.Outlined.VisibilityOff, "Appear offline", "You can still send and refresh chats, but live incoming messages are unavailable", offline) {
                        offline = it; OfflineModeStore.setEnabled(context, auth.userUuid, it); onOfflineChanged(it)
                        AppToast.success(if (it) "You now appear offline" else "You now appear online")
                    }
                    AnimatedVisibility(offline, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
                                .clip(RoundedCornerShape(11.dp)).background(SettingsGold.copy(alpha = .07f))
                                .border(.5.dp, SettingsGold.copy(alpha = .17f), RoundedCornerShape(11.dp))
                                .padding(horizontal = 11.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(Icons.Outlined.VisibilityOff, null, tint = SettingsGold.copy(alpha = .72f), modifier = Modifier.size(14.dp))
                            Text(
                                "Live messages cannot be received while you appear offline. You can still send messages and refresh chats manually.",
                                color = Color.White.copy(alpha = .55f), fontSize = 10.5.sp, lineHeight = 14.sp,
                            )
                        }
                    }
                    SettingsDivider()
                    ExpandableSettingRow(Icons.Outlined.PeopleAlt, "Hidden users", "${muted.size} muted · ${blocked.size} blocked", privacyExpanded) { privacyExpanded = !privacyExpanded }
                    AnimatedVisibility(privacyExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (muted.isEmpty() && blocked.isEmpty()) Text("No hidden users", color = Color.White.copy(alpha = .35f), fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                            muted.forEach { user -> HiddenUserRow(user, "Muted", auth.userUuid) {
                                muteStore.setMuted(user.uuid, false); muted = muted.filterNot { it.uuid == user.uuid }
                                AppToast.success("User unmuted")
                            } }
                            blocked.forEach { user -> HiddenUserRow(user, "Blocked", auth.userUuid) {
                                scope.launch {
                                    if (runCatching { api.call("/v1/users/unblock", JSONObject().put("blocked_uuid", user.uuid), auth) }.isSuccess) {
                                        blocked = blocked.filterNot { it.uuid == user.uuid }
                                        AppToast.success("User unblocked")
                                    } else AppToast.error("Couldn't unblock user")
                                }
                            } }
                        }
                    }
                }
            }
            item { SectionLabel("Notifications") }
            item {
                SettingsGroup {
                    SettingRow(Icons.Outlined.Notifications, "Notification settings", "Sound, badges and Android permissions") { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
                    SettingsDivider()
                    ExpandableSettingRow(Icons.Outlined.NotificationsActive, "Push notifications", "Choose which alerts Android displays", pushExpanded) { pushExpanded = !pushExpanded }
                    AnimatedVisibility(pushExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            NotificationCategory.entries.forEach { category ->
                                SettingsDivider(22.dp)
                                ToggleSetting(Icons.Outlined.NotificationsNone, category.label, "", category in pushCategories) { enabled ->
                                    NotificationPreferences.setEnabled(context, category, push = true, enabled = enabled)
                                    pushCategories = NotificationPreferences.enabledSet(context, push = true)
                                }
                            }
                        }
                    }
                    SettingsDivider()
                    ExpandableSettingRow(Icons.Outlined.Inbox, "In-app notifications", "Choose what appears in the notifications tab", inAppExpanded) { inAppExpanded = !inAppExpanded }
                    AnimatedVisibility(inAppExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            NotificationCategory.entries.forEach { category ->
                                SettingsDivider(22.dp)
                                ToggleSetting(Icons.Outlined.CircleNotifications, category.label, "", category in inAppCategories) { enabled ->
                                    NotificationPreferences.setEnabled(context, category, push = false, enabled = enabled)
                                    inAppCategories = NotificationPreferences.enabledSet(context, push = false)
                                }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Storage & backups") }
            item {
                SettingsGroup {
                    ExpandableSettingRow(Icons.Outlined.Storage, "Storage & local data", "Cache, search index and backups", storageExpanded) {
                        searchIndexCount = AdvancedSearchMemoryIndex.count()
                        storageExpanded = !storageExpanded
                    }
                    AnimatedVisibility(storageExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            SettingsDivider(22.dp)
                            SettingRow(Icons.Outlined.DeleteSweep, if (confirmClear == "media") "Are you sure?" else "Clear media cache", if (confirmClear == "media") "Tap again to remove downloaded media" else "Remove downloaded thumbnails and images", enabled = storageOperation == null) {
                                if (confirmClear != "media") { AppHaptics.open(view); confirmClear = "media" } else scope.launch {
                                    confirmClear = null
                                    if (!LocalDataOperation.tryStart("Clearing media cache")) return@launch
                                    val toastId = AppToast.progress("Clearing media cache…")
                                    try {
                                        runCatching { context.imageLoader.memoryCache?.clear(); withContext(Dispatchers.IO) { context.imageLoader.diskCache?.clear() } }
                                            .onSuccess { AppToast.success("Media cache cleared", toastId) }
                                            .onFailure { AppToast.error(friendlyError(it, "Couldn't clear media cache"), toastId) }
                                    } finally { LocalDataOperation.finish() }
                                }
                            }
                            SettingRow(Icons.Outlined.ManageSearch, if (confirmClear == "index") "Are you sure?" else "Clear search index", if (confirmClear == "index") "Tap again to remove $searchIndexCount indexed posts" else "$searchIndexCount locally indexed posts", enabled = storageOperation == null) {
                                if (confirmClear != "index") { AppHaptics.open(view); confirmClear = "index" } else scope.launch {
                                    confirmClear = null
                                    if (!LocalDataOperation.tryStart("Clearing search index")) return@launch
                                    val toastId = AppToast.progress("Clearing search index…")
                                    try {
                                        runCatching { withContext(Dispatchers.IO) { AdvancedSearchMemoryIndex.clear() } }
                                            .onSuccess { searchIndexCount = AdvancedSearchMemoryIndex.count(); AppToast.success("Search index cleared", toastId) }
                                            .onFailure { AppToast.error(friendlyError(it, "Couldn't clear search index"), toastId) }
                                    } finally { LocalDataOperation.finish() }
                                }
                            }
                            SettingRow(Icons.Outlined.Download, "Export data", storageOperation ?: "Back up local app data, drafts and search index", enabled = storageOperation == null) { exportLauncher.launch("twocents-backup.2cbackup") }
                            SettingRow(
                                Icons.Outlined.Upload,
                                if (confirmClear == "import") "Are you sure?" else "Import data",
                                if (confirmClear == "import") "This replaces your current local data and cannot be undone" else "Restore a twocents backup",
                                enabled = storageOperation == null,
                            ) {
                                if (confirmClear != "import") {
                                    AppHaptics.open(view)
                                    confirmClear = "import"
                                } else {
                                    confirmClear = null
                                    importLauncher.launch(arrayOf("application/octet-stream", "application/zip", "application/json", "text/json", "text/plain"))
                                }
                            }
                            SettingRow(Icons.Outlined.DeleteForever, if (confirmClear == "local") "Are you sure?" else "Clear local data", if (confirmClear == "local") "Tap again to permanently clear local app data" else "Export a backup first—saved GIFs, drafts and other local-only data can be lost", enabled = storageOperation == null) {
                                if (confirmClear != "local") { AppHaptics.open(view); confirmClear = "local" } else scope.launch {
                                    confirmClear = null
                                    if (!LocalDataOperation.tryStart("Clearing local data")) return@launch
                                    val toastId = AppToast.progress("Clearing local data…")
                                    try {
                                        runCatching {
                                            muteStore.all().forEach { muteStore.setMuted(it, false) }; muted = emptyList(); AdvancedSearchIndex.clear()
                                            NotificationHistoryStore(context, auth.userUuid).clear()
                                            FollowersScanner.clearStored(context, auth.userUuid)
                                            GifLibrary.clear(context)
                                            InteractionPreferences.setAutoLikeOwnContent(context, true)
                                            InteractionPreferences.setAutoPlayVideos(context, false)
                                            InteractionPreferences.setWifiOnlyMedia(context, false)
                                            withContext(Dispatchers.IO) { java.io.File(context.filesDir, "compose-drafts").deleteRecursively() }
                                            context.imageLoader.memoryCache?.clear(); withContext(Dispatchers.IO) { context.imageLoader.diskCache?.clear() }
                                        }.onSuccess { searchIndexCount = AdvancedSearchMemoryIndex.count(); autoLikeOwnContent = true; autoPlayVideos = false; wifiOnlyMedia = false; AppToast.success("Local data cleared", toastId) }
                                            .onFailure { AppToast.error(friendlyError(it, "Couldn't clear local data"), toastId) }
                                    } finally { LocalDataOperation.finish() }
                                }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Account & support") }
            item {
                SettingsGroup {
                    SettingRow(Icons.Outlined.ContentCopy, "Copy my UUID", "Copy your account identifier") {
                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("2c user UUID", auth.userUuid))
                        AppToast.success("UUID copied")
                    }
                    SettingsDivider()
                    SettingRow(Icons.Outlined.VpnKey, "Get backup code", "Copy a fresh account recovery code") {
                        scope.launch {
                            val toastId = AppToast.progress("Requesting backup code…")
                            runCatching {
                                val root = api.call("/v1/users/backupcode", JSONObject(), auth) as? JSONObject
                                root?.optString("backupCode")?.takeIf(String::isNotBlank)
                                    ?: error("The server didn't return a backup code")
                            }.onSuccess { backupCode ->
                                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("2c backup code", backupCode))
                                AppToast.success("Backup code copied. Store it somewhere safe.", toastId)
                            }.onFailure { error ->
                                AppToast.error(friendlyError(error, "Couldn't get a backup code"), toastId)
                            }
                        }
                    }
                    SettingsDivider()
                    ExpandableSettingRow(Icons.Outlined.Link, "Connections", "Connect and manage external accounts", connectionsExpanded) { connectionsExpanded = !connectionsExpanded }
                    AnimatedVisibility(connectionsExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, bottom = 10.dp)) {
                            Text("Account connections are managed in the official twocents web app.", color = Color.White.copy(alpha = .42f), fontSize = 11.5.sp, lineHeight = 16.sp)
                            Row(
                                Modifier.padding(top = 9.dp).clip(RoundedCornerShape(11.dp)).background(SettingsGold.copy(alpha = .12f))
                                    .border(.7.dp, SettingsGold.copy(alpha = .25f), RoundedCornerShape(11.dp))
                                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.twocents.money/user/connections"))) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Text("Open connections", color = SettingsGold, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Outlined.OpenInNew, null, tint = SettingsGold, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    SettingsDivider()
                    ExpandableSettingRow(Icons.Outlined.Mail, "Contact", "Feedback and community links", contactExpanded) { contactExpanded = !contactExpanded }
                    AnimatedVisibility(contactExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(Modifier.padding(start = 18.dp, bottom = 6.dp)) {
                            ContactActionRow("twocents feedback", onOpenFeedback)
                            ContactRow("X / Twitter", "https://x.com/twocents")
                            ContactRow("Discord", "https://discord.gg/w6NnSua4aH")
                            ContactRow("GitHub", "https://github.com/fr0meup/2c-client-reworked")
                        }
                    }
                    SettingsDivider()
                    SettingRow(Icons.Outlined.AutoAwesome, "Credits", "People and tools behind twocents") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.twocents.com/blog/credits"))) }
                    SettingsDivider()
                    SettingRow(
                        Icons.Outlined.Logout,
                        if (confirmLogout) "Are you sure?" else "Log out",
                        if (confirmLogout) "Tap again to remove the saved session" else "Remove the saved session from this device",
                    ) {
                        if (confirmLogout) {
                            AppHaptics.confirm(view)
                            onLogout()
                        } else {
                            AppHaptics.open(view)
                            confirmLogout = true
                        }
                    }
                }
            }
            item {
                Text(
                    "2c $appVersion",
                    color = Color.White.copy(alpha = .2f),
                    fontSize = 9.5.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 2.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable private fun SectionLabel(value: String) = Text(value.uppercase(), color = SettingsGold.copy(alpha = .7f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 1.dp))

@Composable private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = .028f)).border(.6.dp, Color.White.copy(alpha = .075f), RoundedCornerShape(17.dp)), content = content)
@Composable private fun SettingsDivider(start: androidx.compose.ui.unit.Dp = 54.dp) = Box(Modifier.fillMaxWidth().padding(start = start, end = 12.dp).height(.6.dp).background(Color.White.copy(alpha = .055f)))

@Composable private fun SettingRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().graphicsLayer { alpha = if (enabled) 1f else .38f }
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Icon(icon, null, tint = SettingsGold.copy(alpha = .8f), modifier = Modifier.size(19.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, color = Color.White.copy(alpha = .88f), fontSize = 13.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold); if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = .38f), fontSize = 10.5.sp, lineHeight = 13.sp) }
    Icon(Icons.Outlined.ChevronRight, null, tint = Color.White.copy(alpha = .25f), modifier = Modifier.size(17.dp))
}

@Composable private fun ToggleSetting(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) = Row(
    Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Icon(icon, null, tint = SettingsGold.copy(alpha = .8f), modifier = Modifier.size(19.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, color = Color.White.copy(alpha = .88f), fontSize = 13.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold); if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = .38f), fontSize = 10.5.sp, lineHeight = 13.sp) }
    Box(Modifier.width(38.dp).height(22.dp).clip(CircleShape).background(if (checked) SettingsGold.copy(alpha = .8f) else Color.White.copy(alpha = .1f))) {
        Box(Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart).padding(2.dp).size(18.dp).clip(CircleShape).background(if (checked) Color(0xFF17130A) else Color.White.copy(alpha = .6f)))
    }
}

@Composable private fun ExpandableSettingRow(icon: ImageVector, title: String, subtitle: String, expanded: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "settings-chevron")
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = SettingsGold.copy(alpha = .8f), modifier = Modifier.size(19.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, color = Color.White.copy(alpha = .88f), fontSize = 13.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Color.White.copy(alpha = .38f), fontSize = 10.5.sp, lineHeight = 13.sp) }
        Icon(Icons.Outlined.ExpandMore, null, tint = Color.White.copy(alpha = .28f), modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = rotation })
    }
}

@Composable private fun ContactRow(label: String, url: String) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.OpenInNew, null, tint = Color.White.copy(alpha = .35f), modifier = Modifier.size(14.dp))
        Text(label, color = Color.White.copy(alpha = .64f), fontSize = 12.5.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable private fun ContactActionRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Mail, null, tint = Color.White.copy(alpha = .35f), modifier = Modifier.size(14.dp))
        Text(label, color = Color.White.copy(alpha = .64f), fontSize = 12.5.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable private fun HiddenUserRow(user: HiddenUser, kind: String, authUuid: String, onRemove: () -> Unit) = Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .025f)).padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
    ComposeNetworthPill(user.profile, authUuid, compact = true)
    UserMetaPill(user.profile.toUserDisplay(), null, Modifier.weight(1f).padding(start = 5.dp), compact = true)
    Text(kind, color = Color.White.copy(alpha = .3f), fontSize = 9.5.sp, modifier = Modifier.padding(horizontal = 6.dp))
    Text(if (kind == "Muted") "Unmute" else "Unblock", color = SettingsGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onRemove).padding(6.dp))
}

private fun JSONObject.toHiddenUser(fallbackUuid: String? = null): HiddenUser? {
    val uuid = listOf("uuid", "blocked_uuid", "user_uuid", "target_uuid")
        .firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() && !it.equals("null", true) } }
        ?: fallbackUuid
        ?: return null
    return HiddenUser(
        uuid = uuid,
        profile = ComposeAuthorProfile(
            uuid = uuid,
            balance = optDouble("balance", 0.0),
            subscriptionType = optInt("subscription_type", 0),
            role = optString("role").takeIf { it.isNotBlank() && !it.equals("null", true) },
            gender = optString("gender").takeIf { it.isNotBlank() && !it.equals("null", true) },
            age = optInt("age").takeIf { has("age") && !isNull("age") },
            arena = optString("arena").takeIf { it.isNotBlank() && !it.equals("null", true) },
        ),
    )
}
