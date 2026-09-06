package com.twocents.mobile.ui.profile
import com.twocents.mobile.ui.common.AppDropdown

import com.twocents.mobile.ui.common.EdgeToEdgeDialogWindow

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import kotlinx.coroutines.launch
import org.json.JSONObject

private val EditGold = Color(0xFFC8A44D)
private val EditSurface = Color(0xFF0F0E0A)

@Composable
internal fun EditProfileSheet(auth: AuthState, api: RpcApi, seed: ComposeAuthorProfile?, onDismiss: () -> Unit, onSaved: (ComposeAuthorProfile) -> Unit) {
    var bio by remember { mutableStateOf("") }
    var age by remember { mutableStateOf(seed?.age?.toString().orEmpty()) }
    var gender by remember { mutableStateOf(seed?.gender.orEmpty()) }
    var arena by remember { mutableStateOf(seed?.arena.orEmpty()) }
    var cityCatalog by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var original by remember { mutableStateOf(listOf("", age, gender, arena)) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dismissDistancePx = with(density) { 1200.dp.toPx() }
    val dismissThresholdPx = with(density) { 90.dp.toPx() }
    val dragOffset = remember { Animatable(dismissDistancePx) }
    var closing by remember { mutableStateOf(false) }
    val dirty by remember { derivedStateOf { listOf(bio, age, gender, arena) != original } }

    fun performClose() {
        if (closing) return
        closing = true
        scope.launch {
            dragOffset.animateTo(dismissDistancePx, tween(220))
            onDismiss()
        }
    }
    fun requestClose() { if (dirty) confirmClose = true else performClose() }
    fun settleDrag() { scope.launch { dragOffset.animateTo(0f, tween(180)) } }
    LaunchedEffect(Unit) { dragOffset.animateTo(0f, tween(220)) }

    LaunchedEffect(auth.userUuid) {
        runCatching { (api.call("/v2/users/get", JSONObject().put("user_uuid", auth.userUuid), auth) as? JSONObject)?.optJSONObject("user") }
            .getOrNull()?.let { user ->
                bio = user.optString("bio")
                age = if (user.has("age") && !user.isNull("age")) user.optInt("age").toString() else ""
                gender = user.optString("gender")
                arena = user.optString("arena")
            }
        original = listOf(bio, age, gender, arena)
        cityCatalog = runCatching {
            val root = api.call("/v1/info/cities", JSONObject(), auth) as? JSONObject
            val cities = root?.optJSONObject("cities") ?: return@runCatching emptyMap()
            buildMap {
                cities.keys().asSequence().toList().sorted().forEach { region ->
                    val rows = cities.optJSONArray(region) ?: return@forEach
                    put(region, buildList {
                        for (index in 0 until rows.length()) rows.optString(index).takeIf(String::isNotBlank)?.let(::add)
                    })
                }
            }
        }.getOrDefault(emptyMap())
        loading = false
    }

    val dragModifier = Modifier.pointerInput(dirty) {
        detectVerticalDragGestures(
            onDragStart = { confirmClose = false },
            onVerticalDrag = { change, amount ->
                if (amount > 0f || dragOffset.value > 0f) {
                    change.consume()
                    scope.launch { dragOffset.snapTo((dragOffset.value + amount).coerceAtLeast(0f)) }
                }
            },
            onDragEnd = {
                if (dragOffset.value > dismissThresholdPx) {
                    if (dirty) { confirmClose = true; settleDrag() } else performClose()
                } else settleDrag()
            },
            onDragCancel = ::settleDrag,
        )
    }

    Dialog(onDismissRequest = ::requestClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val imeVisible = WindowInsets.ime.getBottom(density) > 0
        val sheetFraction by animateFloatAsState(if (imeVisible) .94f else .84f, tween(180), label = "edit-sheet-height")
        val saveBottomPadding = if (imeVisible) 8.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
        EdgeToEdgeDialogWindow(navigationBarColor = AndroidColor.TRANSPARENT)
        val openFraction = (1f - dragOffset.value / dismissDistancePx).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f * openFraction)).clickable(onClick = ::requestClose)) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(sheetFraction).graphicsLayer { translationY = dragOffset.value }
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(EditSurface)
                    .border(.7.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .imePadding().clickable(onClick = {}),
            ) {
                Box(Modifier.fillMaxWidth().then(dragModifier).padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)))
                }
                Row(Modifier.fillMaxWidth().then(dragModifier).padding(start = 16.dp, end = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Edit profile", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(if (dirty) "Unsaved changes" else "Make your profile yours", color = if (dirty) EditGold.copy(alpha = .75f) else Color.White.copy(alpha = .38f), fontSize = 11.sp)
                    }
                    EditDismissControl(confirmClose, onExpand = { if (dirty) confirmClose = true else performClose() }, onConfirm = ::performClose)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))
                if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = EditGold, strokeWidth = 2.dp) }
                else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EditSection(Icons.Outlined.PersonOutline, "About you") {
                            EditField("Bio", bio, { bio = it.take(320); confirmClose = false }, "Tell people a little about yourself", false, 58.dp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                                Box(Modifier.width(105.dp)) {
                                    EditField("Age", age, {
                                        val digits = it.filter(Char::isDigit).take(3)
                                        age = digits.toIntOrNull()?.coerceAtMost(120)?.toString() ?: digits
                                        confirmClose = false
                                    }, "Age", centered = true)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Gender", color = Color.White.copy(alpha = .42f), fontSize = 10.5.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        listOf("M" to "Male", "F" to "Female").forEach { (key, label) ->
                                            val selected = gender.equals(key, true)
                                            Box(modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(11.dp)).background(if (selected) EditGold.copy(alpha = .12f) else Color.White.copy(alpha = .025f))
                                                    .border(.7.dp, if (selected) EditGold.copy(alpha = .35f) else Color.White.copy(alpha = .075f), RoundedCornerShape(11.dp))
                                                    .clickable { gender = key; confirmClose = false }, contentAlignment = Alignment.Center) {
                                                Text(label, color = if (selected) EditGold else Color.White.copy(alpha = .55f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        EditSection(Icons.Outlined.LocationOn, "Location") {
                            CityField(
                                value = arena,
                                catalog = cityCatalog,
                                onChange = { arena = it.take(100); confirmClose = false },
                            )
                            Text("This is shown in your user meta pill.", color = Color.White.copy(alpha = .28f), fontSize = 10.sp)
                        }
                        error?.let { Text(it, color = Color(0xFFFB7185), fontSize = 11.5.sp, modifier = Modifier.padding(horizontal = 5.dp)) }
                    }
                    Column(Modifier.fillMaxWidth().background(EditSurface).padding(start = 14.dp, top = 9.dp, end = 14.dp, bottom = saveBottomPadding)) {
                        Box(
                            Modifier.fillMaxWidth().height(41.dp).clip(RoundedCornerShape(14.dp)).background(EditGold.copy(alpha = if (!dirty || saving) .34f else .92f))
                                .clickable(enabled = dirty && !saving) {
                                    scope.launch {
                                        saving = true; error = null
                                        val parsedAge = age.toIntOrNull()?.takeIf { it in 13..120 }
                                        if (age.isNotBlank() && parsedAge == null) { error = "Enter an age between 13 and 120"; saving = false; return@launch }
                                        val params = JSONObject().put("bio", bio.trim()).put("arena", arena.trim()).put("gender", gender).put("balance", seed?.balance ?: 0.0)
                                        if (parsedAge != null) params.put("age", parsedAge)
                                        runCatching { api.call("/v1/users/update", params, auth) }
                                            .onSuccess {
                                                original = listOf(bio, age, gender, arena)
                                                onSaved((seed ?: ComposeAuthorProfile(auth.userUuid)).copy(age = parsedAge, gender = gender.ifBlank { null }, arena = arena.trim().ifBlank { null }))
                                                performClose()
                                            }.onFailure { error = it.message ?: "Couldn't update profile" }
                                        saving = false
                                    }
                                }, contentAlignment = Alignment.Center,
                        ) {
                            if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF17130A), strokeWidth = 2.dp)
                            else Text(if (dirty) "Save changes" else "No changes", color = Color(0xFF17130A), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun EditSection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = .026f)).border(.7.dp, Color.White.copy(alpha = .075f), RoundedCornerShape(17.dp)).padding(13.dp),
    verticalArrangement = Arrangement.spacedBy(11.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, tint = EditGold.copy(alpha = .76f), modifier = Modifier.size(17.dp)); Text(title, color = Color.White.copy(alpha = .8f), fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
    content()
}

@Composable private fun EditField(label: String, value: String, onChange: (String) -> Unit, placeholder: String = "", singleLine: Boolean = true, minHeight: Dp = 38.dp, centered: Boolean = false) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, color = Color.White.copy(alpha = .42f), fontSize = 10.5.sp)
    BasicTextField(value, onChange, singleLine = singleLine, textStyle = TextStyle(color = Color.White.copy(alpha = .9f), fontSize = 13.sp, lineHeight = 18.sp, textAlign = if (centered) TextAlign.Center else TextAlign.Start), cursorBrush = SolidColor(EditGold),
        modifier = Modifier.fillMaxWidth()
            .then(if (singleLine) Modifier.height(minHeight) else Modifier.heightIn(min = minHeight, max = 104.dp))
            .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .16f))
            .border(.7.dp, Color.White.copy(alpha = .085f), RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp, vertical = if (singleLine) 7.dp else 11.dp),
        decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = if (centered) Alignment.Center else if (singleLine) Alignment.CenterStart else Alignment.TopStart) { if (value.isBlank() && placeholder.isNotBlank()) Text(placeholder, color = Color.White.copy(alpha = .2f), fontSize = 12.5.sp, textAlign = if (centered) TextAlign.Center else TextAlign.Start); inner() } })
}

@Composable
private fun CityField(value: String, catalog: Map<String, List<String>>, onChange: (String) -> Unit) {
    val initialParts = remember(value, catalog) {
        val comma = value.lastIndexOf(',')
        val city = if (comma >= 0) value.substring(0, comma).trim() else value.trim()
        val region = if (comma >= 0) value.substring(comma + 1).trim() else ""
        Triple(city, region, countryForRegion(region))
    }
    var country by remember { mutableStateOf(initialParts.third) }
    var region by remember { mutableStateOf(initialParts.second.takeIf(catalog::containsKey)) }
    var city by remember { mutableStateOf(initialParts.first.takeIf { candidate -> region?.let { candidate in catalog[it].orEmpty() } == true }) }
    var customMode by remember { mutableStateOf(value.isNotBlank() && (region == null || city == null)) }
    val countries = remember(catalog) { countryRegions(catalog) }
    val regions = country?.let(countries::get).orEmpty()
    val cities = region?.let(catalog::get).orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (customMode) {
            EditField("Custom location", value, onChange, "e.g. Tokyo, Japan", centered = true)
            Text(
                "Choose from default locations",
                color = EditGold.copy(alpha = .78f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    customMode = false; country = null; region = null; city = null; onChange("")
                }.padding(horizontal = 2.dp, vertical = 4.dp),
            )
        } else {
            SelectionDropdown(
                label = "Region",
                value = country ?: "Choose region",
                options = countries.keys.toList(),
                display = { it },
                onSelect = { selected ->
                    country = selected
                    val countryRegions = countries[selected].orEmpty()
                    region = countryRegions.singleOrNull()
                    city = null
                    onChange("")
                },
            )
            if (country == "United States") {
                SelectionDropdown(
                    label = "State",
                    value = region?.let(::stateDisplayName) ?: "Choose state",
                    options = regions,
                    display = ::stateDisplayName,
                    onSelect = { selected -> region = selected; city = null; onChange("") },
                )
            }
            SelectionDropdown(
                label = "City",
                value = city ?: "Choose city",
                options = cities,
                display = { it },
                enabled = region != null,
                onSelect = { selected -> city = selected; onChange("$selected, $region") },
            )
            Row(
                Modifier.clickable { customMode = true; country = null; region = null; city = null; onChange("") }.padding(horizontal = 2.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Outlined.Add, null, tint = EditGold.copy(alpha = .75f), modifier = Modifier.size(14.dp))
                Text("Use a custom location", color = EditGold.copy(alpha = .78f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SelectionDropdown(
    label: String,
    value: String,
    options: List<String>,
    display: (String) -> String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Color.White.copy(alpha = .42f), fontSize = 10.5.sp)
        BoxWithConstraints {
            Row(
                Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = .16f))
                    .border(.7.dp, Color.White.copy(alpha = if (enabled) .085f else .045f), RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled && options.isNotEmpty()) { expanded = true }
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(value, color = Color.White.copy(alpha = if (enabled) .78f else .25f), fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White.copy(alpha = if (enabled) .38f else .16f), modifier = Modifier.size(17.dp))
            }
            AppDropdown(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(0.dp, 3.dp),
                containerColor = Color(0xFF141410),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 0.dp,
                shadowElevation = 24.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .1f)),
                modifier = Modifier.heightIn(max = 280.dp).width(maxWidth),
            ) {
                options.forEach { option ->
                    val selected = display(option) == value
                    DropdownMenuItem(
                        text = { Text(display(option), color = if (selected) EditGold else Color.White.copy(alpha = .7f), fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                        onClick = { expanded = false; onSelect(option) },
                        trailingIcon = { if (selected) Icon(Icons.Rounded.Check, null, tint = EditGold, modifier = Modifier.size(14.dp)) },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.padding(horizontal = 4.dp).height(35.dp).clip(RoundedCornerShape(9.dp)).background(if (selected) EditGold.copy(alpha = .14f) else Color.Transparent),
                    )
                }
            }
        }
    }
}

private fun countryForRegion(region: String): String? = when (region) {
    in UsStateNames -> "United States"
    "CAN" -> "Canada"
    "UK" -> "United Kingdom"
    "AUS" -> "Australia"
    "EU" -> "Europe"
    else -> null
}

private fun countryRegions(catalog: Map<String, List<String>>): LinkedHashMap<String, List<String>> = linkedMapOf<String, List<String>>().apply {
    UsStateNames.keys.filter(catalog::containsKey).takeIf(List<String>::isNotEmpty)?.let { put("United States", it) }
    listOf("CAN" to "Canada", "UK" to "United Kingdom", "AUS" to "Australia", "EU" to "Europe").forEach { (region, country) ->
        if (catalog.containsKey(region)) put(country, listOf(region))
    }
}

private fun stateDisplayName(code: String): String = UsStateNames[code]?.let { "$it ($code)" } ?: code

private val UsStateNames = linkedMapOf(
    "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas", "CA" to "California",
    "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware", "FL" to "Florida", "GA" to "Georgia",
    "HI" to "Hawaii", "ID" to "Idaho", "IL" to "Illinois", "IN" to "Indiana", "IA" to "Iowa",
    "KS" to "Kansas", "KY" to "Kentucky", "LA" to "Louisiana", "ME" to "Maine", "MD" to "Maryland",
    "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota", "MS" to "Mississippi", "MO" to "Missouri",
    "MT" to "Montana", "NE" to "Nebraska", "NV" to "Nevada", "NH" to "New Hampshire", "NJ" to "New Jersey",
    "NM" to "New Mexico", "NY" to "New York", "NC" to "North Carolina", "ND" to "North Dakota", "OH" to "Ohio",
    "OK" to "Oklahoma", "OR" to "Oregon", "PA" to "Pennsylvania", "RI" to "Rhode Island", "SC" to "South Carolina",
    "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas", "UT" to "Utah", "VT" to "Vermont",
    "VA" to "Virginia", "WA" to "Washington", "WV" to "West Virginia", "WI" to "Wisconsin", "WY" to "Wyoming",
    "DC" to "District of Columbia", "PR" to "Puerto Rico",
)

@Composable private fun EditDismissControl(expanded: Boolean, onExpand: () -> Unit, onConfirm: () -> Unit) {
    val width by animateDpAsState(if (expanded) 136.dp else 44.dp, tween(180), label = "edit-close-width")
    val alpha by animateFloatAsState(if (expanded) 1f else 0f, tween(130), label = "edit-close-label")
    Box(Modifier.width(width).height(40.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = if (expanded) onConfirm else onExpand), contentAlignment = Alignment.CenterEnd) {
        if (expanded) Box(Modifier.fillMaxWidth().height(34.dp).clip(CircleShape).background(Color(0xFF211E18)).border(1.dp, Color.White.copy(alpha = .1f), CircleShape))
        if (expanded) Text("Are you sure?", color = Color.White.copy(alpha = .7f), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterStart).padding(start = 11.dp, end = 37.dp).graphicsLayer { this.alpha = alpha })
        Box(Modifier.align(Alignment.CenterEnd).size(40.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Close", tint = Color.White.copy(alpha = .7f), modifier = Modifier.offset(x = (-1).dp).size(19.dp)) }
    }
}
