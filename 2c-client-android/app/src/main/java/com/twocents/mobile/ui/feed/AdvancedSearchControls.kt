package com.twocents.mobile.ui.feed
import com.twocents.mobile.ui.common.AppDropdown

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

/** Search controls are separated from filtering/index rules; dimensions and interaction code are unchanged. */
@Composable
internal fun DateSearchField(label: String, value: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Color.White.copy(alpha = .42f), fontSize = 10.5.sp)
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(11.dp))
                .background(Color.Black.copy(alpha = .16f))
                .border(.7.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(11.dp))
                .clickable(onClick = onClick).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value.ifBlank { "Any date" }, color = Color.White.copy(alpha = if (value.isBlank()) .25f else .78f), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.CalendarMonth, null, tint = Color(0xFFC8A44D).copy(alpha = .68f), modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
internal fun AdvancedDatePickerDialog(initial: String, onDismiss: () -> Unit, onClear: () -> Unit, onSelect: (LocalDate) -> Unit) {
    val parsed = remember(initial) { runCatching { LocalDate.parse(initial) }.getOrNull() }
    val earliest = remember { LocalDate.of(2024, 11, 1) }
    val latest = remember { LocalDate.now() }
    var month by remember(initial) { mutableStateOf(YearMonth.from((parsed ?: latest).coerceIn(earliest, latest))) }
    var monthMenu by remember { mutableStateOf(false) }
    var yearMenu by remember { mutableStateOf(false) }
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM") }
    val rowCount = (firstOffset + month.lengthOfMonth() + 6) / 7
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(320.dp).clip(RoundedCornerShape(19.dp)).background(Color(0xFF141410))
                    .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(19.dp))
                    .clickable(onClick = {}).padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = month > YearMonth.from(earliest)) { month = month.minusMonths(1) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ChevronLeft, "Previous month", tint = Color.White.copy(alpha = .62f), modifier = Modifier.size(19.dp))
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Text(month.format(monthFormatter), color = Color.White.copy(alpha = .88f), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { monthMenu = true }.padding(horizontal = 7.dp, vertical = 5.dp))
                            AppDropdown(expanded = monthMenu, onDismissRequest = { monthMenu = false }, modifier = Modifier.width(142.dp).heightIn(max = 300.dp), containerColor = Color(0xFF141410), shape = RoundedCornerShape(13.dp), tonalElevation = 0.dp, shadowElevation = 20.dp) {
                                (1..12).forEach { number ->
                                    val candidate = YearMonth.of(month.year, number)
                                    val enabled = candidate >= YearMonth.from(earliest) && candidate <= YearMonth.from(latest)
                                    DropdownMenuItem(text = { Text(candidate.format(monthFormatter), color = if (candidate == month) Color(0xFFC8A44D) else Color.White.copy(alpha = if (enabled) .7f else .22f), fontSize = 12.5.sp, maxLines = 1) }, enabled = enabled, onClick = { month = candidate; monthMenu = false }, modifier = Modifier.height(36.dp))
                                }
                            }
                        }
                        Box {
                            Text(month.year.toString(), color = Color(0xFFC8A44D).copy(alpha = .82f), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { yearMenu = true }.padding(horizontal = 7.dp, vertical = 5.dp))
                            AppDropdown(expanded = yearMenu, onDismissRequest = { yearMenu = false }, modifier = Modifier.width(96.dp), containerColor = Color(0xFF141410), shape = RoundedCornerShape(13.dp), tonalElevation = 0.dp, shadowElevation = 20.dp) {
                                (earliest.year..latest.year).reversed().forEach { year -> DropdownMenuItem(text = { Text(year.toString(), color = if (year == month.year) Color(0xFFC8A44D) else Color.White.copy(alpha = .7f), fontSize = 12.5.sp) }, onClick = { month = YearMonth.of(year, month.monthValue).coerceIn(YearMonth.from(earliest), YearMonth.from(latest)); yearMenu = false }, modifier = Modifier.height(36.dp)) }
                            }
                        }
                    }
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = month < YearMonth.from(latest)) { month = month.plusMonths(1) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ChevronRight, "Next month", tint = Color.White.copy(alpha = .62f), modifier = Modifier.size(19.dp))
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, color = Color.White.copy(alpha = .3f), fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f)) }
                }
                repeat(rowCount) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val day = row * 7 + column - firstOffset + 1
                            val valid = day in 1..month.lengthOfMonth()
                            val date = if (valid) month.atDay(day) else null
                            val selected = date == parsed
                            val enabled = date != null && date >= earliest && date <= latest
                            Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                                if (date != null) Box(
                                    Modifier.size(32.dp).clip(CircleShape)
                                        .background(if (selected) Color(0xFFC8A44D) else Color.Transparent)
                                        .clickable(enabled = enabled) { onSelect(date) }, contentAlignment = Alignment.Center,
                                ) {
                                    Text(day.toString(), color = if (selected) Color(0xFF17130A) else Color.White.copy(alpha = if (enabled) .7f else .2f), fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("Clear", color = Color.White.copy(alpha = .44f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(9.dp)).clickable(onClick = onClear).padding(horizontal = 12.dp, vertical = 8.dp))
                    Text("Cancel", color = Color(0xFFC8A44D), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(9.dp)).clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
internal fun AdvancedTopicDropdown(value: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val view = LocalView.current
    val topics = remember { TOPIC_GROUPS.filterNot { it.category == "Feeds" }.flatMap { it.items }.distinct() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Topic", color = Color.White.copy(alpha = .38f), fontSize = 10.5.sp)
        Box {
            Row(Modifier.fillMaxWidth().onSizeChanged { anchorWidthPx = it.width }.height(38.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = .035f)).border(.7.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(11.dp)).clickable { AppHaptics.open(view); open = true }.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value.ifBlank { "Any topic" }, color = Color.White.copy(alpha = if (value.isBlank()) .25f else .78f), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, null, tint = Color.White.copy(alpha = .38f), modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = if (open) 180f else 0f })
            }
            AppDropdown(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.width(with(density) { anchorWidthPx.toDp() }).heightIn(max = 280.dp), containerColor = Color(0xFF141410), shape = RoundedCornerShape(13.dp), tonalElevation = 0.dp, shadowElevation = 22.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .1f))) {
                listOf("") .plus(topics).forEach { topic ->
                    val selected = topic.equals(value, true)
                    DropdownMenuItem(text = { Text(topic.ifBlank { "Any topic" }, color = if (selected) Color(0xFFC8A44D) else Color.White.copy(alpha = .7f), fontSize = 12.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1) }, trailingIcon = { if (selected) Icon(Icons.Rounded.Check, null, tint = Color(0xFFC8A44D), modifier = Modifier.size(14.dp)) }, onClick = { open = false; onSelect(topic) }, modifier = Modifier.padding(horizontal = 4.dp).height(36.dp).clip(RoundedCornerShape(9.dp)).background(if (selected) Color(0xFFC8A44D).copy(alpha = .13f) else Color.Transparent), contentPadding = PaddingValues(horizontal = 10.dp))
                }
            }
        }
    }
}

@Composable
internal fun ToggleGrid(label: String, options: List<Pair<String, String>>, selected: Set<String>, toggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color.White.copy(alpha = .42f), fontSize = 10.5.sp)
        options.chunked(2).forEach { rowOptions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                rowOptions.forEach { (key, text) ->
                    val active = key in selected
                    Row(
                        Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (active) Color(0xFFC8A44D).copy(alpha = .12f) else Color.Black.copy(alpha = .15f))
                            .border(.7.dp, if (active) Color(0xFFC8A44D).copy(alpha = .34f) else Color.White.copy(alpha = .075f), RoundedCornerShape(10.dp))
                            .clickable { toggle(key) }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(text, color = if (active) Color(0xFFC8A44D) else Color.White.copy(alpha = .54f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        if (active) Icon(Icons.Rounded.Check, null, tint = Color(0xFFC8A44D), modifier = Modifier.padding(start = 5.dp).size(13.dp))
                    }
                }
                if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun ExpandableFilterGroup(
    title: String,
    subtitle: String,
    active: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val view = LocalView.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = .025f))) {
        Row(Modifier.fillMaxWidth().clickable { AppHaptics.open(view); onToggle() }.padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White.copy(alpha = .82f), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.White.copy(alpha = .3f), fontSize = 9.5.sp)
            }
            if (active > 0) Text("$active", color = Color(0xFFC8A44D), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 4.dp))
            Icon(Icons.Outlined.ExpandMore, null, tint = Color.White.copy(alpha = .38f), modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f })
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
internal fun AdvancedSearchSheet(initial: AdvancedSearchFilters?, onDismiss: () -> Unit, onApply: (AdvancedSearchFilters) -> Unit) {
    var value by remember { mutableStateOf(initial ?: AdvancedSearchFilters()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window; val old = window?.navigationBarColor
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }; window?.navigationBarColor = AndroidColor.TRANSPARENT; window?.isNavigationBarContrastEnforced = false
            onDispose { if (old != null) window?.navigationBarColor = old }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f)).clickable(onClick = onDismiss)) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.9f).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color(0xFF0F0E0A)).border(.7.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).clickable(onClick = {})) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).width(36.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)))
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFC8A44D).copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.FilterAlt, null, tint = Color(0xFFC8A44D), modifier = Modifier.size(17.dp)) }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) { Text("Advanced search", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text("${value.activeCount} active · ${AdvancedSearchMemoryIndex.count()} posts indexed", color = Color.White.copy(alpha = .38f), fontSize = 10.5.sp) }
                    Icon(Icons.Outlined.Close, "Close", tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(7.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SearchField("Search text", value.query, { value = value.copy(query = it) }, "Search twocents")
                    SearchSection("Date range")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { SearchField("From", value.dateFrom, { value = value.copy(dateFrom = it) }, "YYYY-MM-DD") }; Box(Modifier.weight(1f)) { SearchField("To", value.dateTo, { value = value.copy(dateTo = it) }, "YYYY-MM-DD") } }
                    SearchSection("Author")
                    SearchField("Author UUIDs", value.authorUuids, { value = value.copy(authorUuids = it) }, "Comma-separated UUIDs")
                    ChipRow(listOf("M" to "Male", "F" to "Female"), value.genders) { key -> value = value.copy(genders = value.genders.toMutableSet().apply { if (!add(key)) remove(key) }) }
                    ChipRow(listOf("1" to "Verified", "0" to "Unverified"), value.verification?.let { setOf(it.toString()) }.orEmpty()) { key -> value = value.copy(verification = key.toInt().takeUnless { it == value.verification }) }
                    NumberPair("Min NW", value.minBalance, "Max NW", value.maxBalance) { a, b -> value = value.copy(minBalance = a, maxBalance = b) }
                    IntPair("Min age", value.minAge, "Max age", value.maxAge) { a, b -> value = value.copy(minAge = a, maxAge = b) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { SearchField("Country / region", value.country, { value = value.copy(country = it) }, "Any") }; Box(Modifier.weight(1f)) { SearchField("City", value.city, { value = value.copy(city = it) }, "Any") } }
                    SearchSection("Post")
                    SearchField("Topic", value.topic, { value = value.copy(topic = it) }, "Any topic")
                    IntPair("Min votes", value.minVotes, "Max votes", value.maxVotes) { a, b -> value = value.copy(minVotes = a, maxVotes = b) }
                    IntPair("Min comments", value.minComments, "Max comments", value.maxComments) { a, b -> value = value.copy(minComments = a, maxComments = b) }
                    ChipRow(listOf("image" to "Image", "video" to "Video", "poll" to "Poll", "likert" to "Likert"), buildSet { if (value.hasImage) add("image"); if (value.hasVideo) add("video"); if (value.hasPoll) add("poll"); if (value.hasLikert) add("likert") }) { key -> value = when(key) { "image" -> value.copy(hasImage=!value.hasImage); "video" -> value.copy(hasVideo=!value.hasVideo); "poll" -> value.copy(hasPoll=!value.hasPoll); else -> value.copy(hasLikert=!value.hasLikert) } }
                    SearchSection("Sort")
                    ChipRow(SearchResultSort.entries.map { it.name to it.label }, setOf(value.sort.name)) { key -> value = value.copy(sort = SearchResultSort.valueOf(key)) }
                }
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Reset", color = Color.White.copy(alpha = .55f), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(13.dp)).clickable { value = AdvancedSearchFilters() }.padding(horizontal = 18.dp, vertical = 13.dp))
                    Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFC8A44D)).clickable { onApply(value); onDismiss() }, contentAlignment = Alignment.Center) { Text("Apply filters", color = Color(0xFF17130A), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}

@Composable internal fun SearchSection(text: String) = Text(text.uppercase(), color = Color(0xFFC8A44D).copy(alpha = .68f), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
@Composable internal fun SearchField(label: String, value: String, onChange: (String)->Unit, placeholder: String) = Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, color=Color.White.copy(alpha=.38f), fontSize=10.5.sp); BasicTextField(value,onChange, singleLine=true, textStyle=TextStyle(color=Color.White,fontSize=12.5.sp),cursorBrush=SolidColor(Color(0xFFC8A44D)),modifier=Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha=.035f)).border(.7.dp,Color.White.copy(alpha=.08f),RoundedCornerShape(11.dp)).padding(horizontal=11.dp),decorationBox={ inner -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.CenterStart){if(value.isBlank()) Text(placeholder,color=Color.White.copy(alpha=.2f),fontSize=12.sp);inner()}}) }
@Composable private fun ChipRow(options: List<Pair<String,String>>, selected: Set<String>, toggle:(String)->Unit) = Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){options.forEach{(key,label)->Text(label,color=if(key in selected)Color(0xFFC8A44D) else Color.White.copy(alpha=.5f),fontSize=10.5.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clip(CircleShape).background(if(key in selected)Color(0xFFC8A44D).copy(alpha=.11f)else Color.White.copy(alpha=.03f)).border(.7.dp,if(key in selected)Color(0xFFC8A44D).copy(alpha=.3f)else Color.White.copy(alpha=.07f),CircleShape).clickable{toggle(key)}.padding(horizontal=9.dp,vertical=7.dp))}}
@Composable internal fun NumberPair(aLabel:String,a:Double?,bLabel:String,b:Double?,change:(Double?,Double?)->Unit)=Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){SearchField(aLabel,a?.toString().orEmpty(),{change(it.toDoubleOrNull(),b)},"Any")};Box(Modifier.weight(1f)){SearchField(bLabel,b?.toString().orEmpty(),{change(a,it.toDoubleOrNull())},"Any")}}
@Composable internal fun IntPair(aLabel:String,a:Int?,bLabel:String,b:Int?,change:(Int?,Int?)->Unit)=Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){SearchField(aLabel,a?.toString().orEmpty(),{change(it.toIntOrNull(),b)},"Any")};Box(Modifier.weight(1f)){SearchField(bLabel,b?.toString().orEmpty(),{change(a,it.toIntOrNull())},"Any")}}
