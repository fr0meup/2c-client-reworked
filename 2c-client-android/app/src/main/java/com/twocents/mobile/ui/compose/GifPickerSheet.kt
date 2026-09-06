package com.twocents.mobile.ui.compose

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import com.twocents.mobile.ui.feed.cachedMediaRatio
import com.twocents.mobile.ui.feed.cacheMediaRatio
import org.json.JSONArray
import org.json.JSONObject

private const val GifPreferences = "twocents-gifs"
private val GifGold = Color(0xFFC8A44D)

internal object GifLibrary {
    private fun prefs(context: Context) = context.getSharedPreferences(GifPreferences, Context.MODE_PRIVATE)
    private fun read(context: Context, key: String): List<String> {
        val preferences = prefs(context)
        val json = preferences.getString("${key}_ordered", null)
        if (!json.isNullOrBlank()) return runCatching { JSONArray(json).let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } } }.getOrDefault(emptyList())
        return preferences.getStringSet(key, emptySet()).orEmpty().toList()
    }
    private fun write(context: Context, key: String, values: List<String>) = prefs(context).edit().putString("${key}_ordered", JSONArray(values).toString()).apply()
    fun saved(context: Context) = read(context, "saved")
    fun favorites(context: Context) = read(context, "favorites")
    fun addMany(context: Context, raw: String): Pair<Int, Int> {
        val candidates = raw.split(Regex("[\\s,;]+"), limit = 300).map(String::trim).filter { it.startsWith("http://") || it.startsWith("https://") }.distinct()
        val old = saved(context); val existing = old.toHashSet(); val added = candidates.filter(existing::add)
        write(context, "saved", (added.asReversed() + old).distinct().take(200))
        return added.size to (candidates.size - added.size)
    }
    fun toggleFavorite(context: Context, url: String) {
        val current = favorites(context)
        write(context, "favorites", if (url in current) current - url else listOf(url) + current)
        if (url !in saved(context)) write(context, "saved", listOf(url) + saved(context))
    }
    fun toggleSaved(context: Context, url: String): Boolean {
        val current = saved(context)
        val adding = url !in current
        write(context, "saved", if (adding) (listOf(url) + current).distinct().take(200) else current - url)
        if (!adding) write(context, "favorites", favorites(context) - url)
        return adding
    }
    fun remove(context: Context, url: String) { write(context, "saved", saved(context) - url); write(context, "favorites", favorites(context) - url) }
    fun exportJson(context: Context) = JSONObject().put("saved", JSONArray(saved(context))).put("favorites", JSONArray(favorites(context)))
    fun importJson(context: Context, value: JSONObject) {
        fun strings(key: String): List<String> = value.optJSONArray(key)?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
        write(context, "saved", strings("saved").distinct().take(200))
        write(context, "favorites", strings("favorites").distinct().take(200))
    }
    fun clear(context: Context) = prefs(context).edit().clear().apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifPickerSheet(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    var gifs by remember { mutableStateOf(GifLibrary.saved(context)) }
    var favorites by remember { mutableStateOf(GifLibrary.favorites(context).toSet()) }
    var value by remember { mutableStateOf("") }
    var bulkMode by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun reload() { gifs = GifLibrary.saved(context); favorites = GifLibrary.favorites(context).toSet() }
    fun add(raw: String) {
        val (added, skipped) = GifLibrary.addMany(context, raw)
        value = ""
        reload()
        feedback = if (added == 0) "No new GIFs found" else "Added $added${if (skipped > 0) " · $skipped duplicate" else ""}"
    }
    LaunchedEffect(gifs) {
        gifs.take(40).forEach { url -> context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).size(360, 280).memoryCacheKey(url).diskCacheKey(url).build()) }
    }
    LaunchedEffect(feedback) { if (feedback != null) { kotlinx.coroutines.delay(2200); feedback = null } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141410),
        contentColor = Color.White,
        dragHandle = { Box(Modifier.padding(top = 8.dp, bottom = 5.dp).size(width = 42.dp, height = 4.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f))) },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.72f).navigationBarsPadding().padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("GIFs", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (bulkMode) "Single add" else "Bulk add", color = GifGold.copy(alpha = .68f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { bulkMode = !bulkMode; value = "" }.padding(horizontal = 8.dp, vertical = 6.dp))
                Icon(Icons.Outlined.Close, "Close", tint = Color.White.copy(alpha = .55f), modifier = Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(7.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp).height(if (bulkMode) 88.dp else 40.dp).clip(RoundedCornerShape(11.dp))
                    .background(Color.White.copy(alpha = .04f)).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(11.dp)).padding(start = 12.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = value, onValueChange = { value = it }, singleLine = !bulkMode,
                    textStyle = TextStyle(Color.White, 13.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    cursorBrush = SolidColor(GifGold), modifier = Modifier.weight(1f).fillMaxHeight().padding(top = if (bulkMode) 9.dp else 0.dp),
                    decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = if (bulkMode) Alignment.TopStart else Alignment.CenterStart) { if (value.isBlank()) Text(if (bulkMode) "Paste many GIF URLs — one per line or separated by spaces" else "Paste a GIF URL to save", color = Color.White.copy(alpha = .32f), fontSize = 12.5.sp); inner() } },
                )
                val candidateCount = value.split(Regex("[\\s,;]+")).count { it.startsWith("http") }
                Text(if (bulkMode && candidateCount > 0) "Add $candidateCount" else "Add", color = if (candidateCount > 0) GifGold else Color.White.copy(alpha = .2f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = value.trim().startsWith("http")) {
                        add(value)
                    }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            feedback?.let { Text(it, color = GifGold.copy(alpha = .72f), fontSize = 10.5.sp, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp)) }
            if (gifs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("GIF", color = GifGold.copy(alpha = .6f), fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("Paste a GIF URL to build your picker", color = Color.White.copy(alpha = .36f), fontSize = 12.5.sp)
                    }
                }
            } else {
                val favoriteItems = gifs.filter { it in favorites }
                val savedItems = gifs.filterNot { it in favorites }
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalItemSpacing = 7.dp,
                    contentPadding = PaddingValues(bottom = 18.dp),
                ) {
                    if (favoriteItems.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine, key = "favorites-header") { GifSectionLabel("Favorites", true) }
                        items(favoriteItems, key = { "favorite-$it" }) { url -> GifPickerItem(url, true, context, onSelect, ::reload) }
                    }
                    if (savedItems.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine, key = "saved-header") { GifSectionLabel("Saved", false) }
                        items(savedItems, key = { "saved-$it" }) { url -> GifPickerItem(url, false, context, onSelect, ::reload) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GifSectionLabel(label: String, gold: Boolean) {
    Text(label.uppercase(), color = if (gold) GifGold.copy(alpha = .62f) else Color.White.copy(alpha = .3f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp, modifier = Modifier.padding(start = 3.dp, top = 7.dp, bottom = 1.dp))
}

@Composable
private fun GifPickerItem(url: String, favorite: Boolean, context: Context, onSelect: (String) -> Unit, reload: () -> Unit) {
    var ratio by remember(url) { mutableFloatStateOf(cachedMediaRatio(url) ?: 1.25f) }
    Box(Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(.48f, 2.5f)).clip(RoundedCornerShape(11.dp)).background(Color.Black).border(.7.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(11.dp)).clickable { onSelect(url) }) {
        AsyncImage(ImageRequest.Builder(context).data(url).memoryCacheKey(url).diskCacheKey(url).build(), "GIF", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, onSuccess = { result -> val image = result.result.image; if (image.width > 0 && image.height > 0) { ratio = image.width.toFloat() / image.height; cacheMediaRatio(url, ratio) } })
        Row(Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = .72f))) {
            Icon(if (favorite) Icons.Rounded.Star else Icons.Outlined.StarBorder, "Favorite", tint = if (favorite) GifGold else Color.White.copy(alpha = .75f), modifier = Modifier.size(28.dp).clickable { GifLibrary.toggleFavorite(context, url); reload() }.padding(6.dp))
            Icon(Icons.Outlined.DeleteOutline, "Remove", tint = Color.White.copy(alpha = .72f), modifier = Modifier.size(28.dp).clickable { GifLibrary.remove(context, url); reload() }.padding(6.dp))
        }
    }
}
