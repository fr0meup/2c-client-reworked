package com.twocents.mobile.ui.feed

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.common.AppHaptics
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class SearchResultSort(val label: String) { Newest("Newest"), Oldest("Oldest"), MostUpvoted("Most upvoted"), LeastUpvoted("Least upvoted"), MostCommented("Most commented"), LeastCommented("Least commented") }

@Immutable
data class AdvancedSearchFilters(
    val query: String = "", val dateFrom: String = "", val dateTo: String = "",
    val authorUuids: String = "", val genders: Set<String> = emptySet(), val verification: Int? = null,
    val minBalance: Double? = null, val maxBalance: Double? = null, val minAge: Int? = null, val maxAge: Int? = null,
    val country: String = "", val city: String = "", val topic: String = "", val minVotes: Int? = null, val maxVotes: Int? = null,
    val minComments: Int? = null, val maxComments: Int? = null, val hasImage: Boolean = false, val hasVideo: Boolean = false,
    val hasPoll: Boolean = false, val hasLikert: Boolean = false, val sort: SearchResultSort = SearchResultSort.Newest,
) {
    val activeCount: Int get() = listOf(query, dateFrom, dateTo, authorUuids, genders.isNotEmpty(), verification != null, minBalance, maxBalance, minAge, maxAge, country, city, topic, minVotes, maxVotes, minComments, maxComments, hasImage, hasVideo, hasPoll, hasLikert).count { it != null && it != false && it != "" }

    fun matches(post: FeedPost): Boolean {
        if (query.isNotBlank() && !"${post.title} ${post.text}".contains(query.trim(), true)) return false
        val instant = runCatching { Instant.parse(post.createdAt) }.getOrNull()
        if (dateFrom.isNotBlank() && instant?.isBefore(runCatching { Instant.parse("${dateFrom}T00:00:00Z") }.getOrNull()) == true) return false
        if (dateTo.isNotBlank() && instant?.isAfter(runCatching { Instant.parse("${dateTo}T23:59:59Z") }.getOrNull()) == true) return false
        val authors = authorUuids.split(',', '\n').map(String::trim).filter(String::isNotBlank)
        if (authors.isNotEmpty() && authors.none { it.equals(post.authorUuid, true) }) return false
        if (genders.isNotEmpty() && post.author.gender?.uppercase() !in genders) return false
        if (verification != null && post.author.subscriptionType != verification) return false
        if (minBalance != null && post.author.balance < minBalance) return false
        if (maxBalance != null && post.author.balance > maxBalance) return false
        if (minAge != null && (post.author.age == null || post.author.age < minAge)) return false
        if (maxAge != null && (post.author.age == null || post.author.age > maxAge)) return false
        val arena = post.author.arena.orEmpty()
        val split = arena.lastIndexOf(',')
        val arenaCity = if (split >= 0) arena.substring(0, split).trim() else arena.trim()
        val arenaCountry = if (split >= 0) arena.substring(split + 1).trim() else ""
        if (country.isNotBlank() && !arenaCountry.equals(country.trim(), true)) return false
        if (city.isNotBlank() && !arenaCity.equals(city.trim(), true)) return false
        if (topic.isNotBlank() && !post.topic.equals(topic.trim(), true)) return false
        if (minVotes != null && post.upvoteCount < minVotes) return false
        if (maxVotes != null && post.upvoteCount > maxVotes) return false
        if (minComments != null && post.commentCount < minComments) return false
        if (maxComments != null && post.commentCount > maxComments) return false
        val contentSelected = hasImage || hasVideo || hasPoll || hasLikert
        if (contentSelected && !((hasImage && post.meta.images.isNotEmpty()) || (hasVideo && (!post.meta.videoUrl.isNullOrBlank() || post.meta.mediaType == "video")) || (hasPoll && post.postType == 2) || (hasLikert && post.postType == 5))) return false
        return true
    }

    fun sort(posts: List<FeedPost>): List<FeedPost> = when (sort) {
        SearchResultSort.Newest -> posts.sortedByDescending(FeedPost::createdAt)
        SearchResultSort.Oldest -> posts.sortedBy(FeedPost::createdAt)
        SearchResultSort.MostUpvoted -> posts.sortedByDescending(FeedPost::upvoteCount)
        SearchResultSort.LeastUpvoted -> posts.sortedBy(FeedPost::upvoteCount)
        SearchResultSort.MostCommented -> posts.sortedByDescending(FeedPost::commentCount)
        SearchResultSort.LeastCommented -> posts.sortedBy(FeedPost::commentCount)
    }
}

internal object AdvancedSearchMemoryIndex {
    @Synchronized fun count(): Int = AdvancedSearchIndex.count()
    @Synchronized fun clear() = AdvancedSearchIndex.clear()
}
/** Expandable search controls only; indexing and worker orchestration remain data concerns. */
@Composable
internal fun AdvancedSearchHeaderPanel(
    value: AdvancedSearchFilters,
    onValueChange: (AdvancedSearchFilters) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    searching: Boolean,
    scanned: Int,
    matches: Int,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var authorOpen by rememberSaveable { mutableStateOf(false) }
    var postOpen by rememberSaveable { mutableStateOf(false) }
    var dateTarget by remember { mutableStateOf<String?>(null) }
    val authorCount = listOf(value.authorUuids, value.genders.isNotEmpty(), value.verification != null, value.minBalance, value.maxBalance, value.minAge, value.maxAge, value.country, value.city).count { it != null && it != false && it != "" }
    val postCount = listOf(value.topic, value.minVotes, value.maxVotes, value.minComments, value.maxComments, value.hasImage, value.hasVideo, value.hasPoll, value.hasLikert).count { it != null && it != false && it != "" }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
            .background(Background)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = .08f),
                    start = androidx.compose.ui.geometry.Offset(18.dp.toPx(), size.height - 1f),
                    end = androidx.compose.ui.geometry.Offset(size.width - 18.dp.toPx(), size.height - 1f),
                    strokeWidth = 1f,
                )
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, end = 9.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFC8A44D).copy(alpha = .11f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.FilterAlt, null, tint = Color(0xFFC8A44D), modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                Text("Advanced search", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (searching) "$scanned scanned · $matches matches · 20 workers" else "${value.activeCount} active · ${AdvancedSearchMemoryIndex.count()} indexed",
                    color = if (searching) Color(0xFFC8A44D).copy(alpha = .75f) else Color.White.copy(alpha = .35f),
                    fontSize = 10.sp,
                )
            }
            Text("Reset", color = Color.White.copy(alpha = .42f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onReset).padding(8.dp))
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Close filters", tint = Color.White.copy(alpha = .58f), modifier = Modifier.size(18.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .06f)))
        Column(
            Modifier.padding(start = 13.dp, top = 11.dp, end = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField("Search text", value.query, { onValueChange(value.copy(query = it)) }, "Search twocents")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = .025f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SearchSection("Date range")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { DateSearchField("From", value.dateFrom) { dateTarget = "from" } }
                    Box(Modifier.weight(1f)) { DateSearchField("To", value.dateTo) { dateTarget = "to" } }
                }
            }
            ExpandableFilterGroup("People", "Net worth, age, location and verification", authorCount, authorOpen, { authorOpen = !authorOpen }) {
                SearchField("Author UUIDs", value.authorUuids, { onValueChange(value.copy(authorUuids = it)) }, "Comma-separated UUIDs")
                ToggleGrid("Gender", listOf("M" to "Male", "F" to "Female"), value.genders) { key -> onValueChange(value.copy(genders = value.genders.toMutableSet().apply { if (!add(key)) remove(key) })) }
                ToggleGrid("Verification", listOf("1" to "Verified", "0" to "Unverified"), value.verification?.let { setOf(it.toString()) }.orEmpty()) { key -> onValueChange(value.copy(verification = key.toInt().takeUnless { it == value.verification })) }
                NumberPair("Min NW", value.minBalance, "Max NW", value.maxBalance) { a, b -> onValueChange(value.copy(minBalance = a, maxBalance = b)) }
                IntPair("Min age", value.minAge, "Max age", value.maxAge) { a, b -> onValueChange(value.copy(minAge = a, maxAge = b)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { SearchField("Country / region", value.country, { onValueChange(value.copy(country = it)) }, "Any") }
                    Box(Modifier.weight(1f)) { SearchField("City", value.city, { onValueChange(value.copy(city = it)) }, "Any") }
                }
            }
            ExpandableFilterGroup("Posts", "Topic, engagement and media type", postCount, postOpen, { postOpen = !postOpen }) {
                AdvancedTopicDropdown(value.topic) { onValueChange(value.copy(topic = it)) }
                IntPair("Min votes", value.minVotes, "Max votes", value.maxVotes) { a, b -> onValueChange(value.copy(minVotes = a, maxVotes = b)) }
                IntPair("Min comments", value.minComments, "Max comments", value.maxComments) { a, b -> onValueChange(value.copy(minComments = a, maxComments = b)) }
                ToggleGrid("Content", listOf("image" to "Image", "video" to "Video", "poll" to "Poll", "likert" to "Likert"), buildSet { if (value.hasImage) add("image"); if (value.hasVideo) add("video"); if (value.hasPoll) add("poll"); if (value.hasLikert) add("likert") }) { key ->
                    onValueChange(when (key) { "image" -> value.copy(hasImage = !value.hasImage); "video" -> value.copy(hasVideo = !value.hasVideo); "poll" -> value.copy(hasPoll = !value.hasPoll); else -> value.copy(hasLikert = !value.hasLikert) })
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(start = 13.dp, top = 8.dp, end = 13.dp, bottom = 10.dp).height(42.dp).clip(RoundedCornerShape(13.dp))
                .background(if (searching) Color(0xFFC8A44D).copy(alpha = .58f) else Color(0xFFC8A44D))
                .clickable(enabled = !searching) { AppHaptics.confirm(view); onApply() },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (searching) CircularProgressIndicator(Modifier.size(14.dp), color = Color(0xFF17130A), strokeWidth = 1.8.dp)
                Text(if (searching) "Searching twocents…" else "Search", color = Color(0xFF17130A), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
    dateTarget?.let { target ->
        AdvancedDatePickerDialog(
            initial = if (target == "from") value.dateFrom else value.dateTo,
            onDismiss = { dateTarget = null },
            onClear = {
                onValueChange(if (target == "from") value.copy(dateFrom = "") else value.copy(dateTo = ""))
                dateTarget = null
            },
            onSelect = { selected ->
                onValueChange(if (target == "from") value.copy(dateFrom = selected.toString()) else value.copy(dateTo = selected.toString()))
                dateTarget = null
            },
        )
    }
}
