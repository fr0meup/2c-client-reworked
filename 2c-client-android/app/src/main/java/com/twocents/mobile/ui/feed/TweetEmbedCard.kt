package com.twocents.mobile.ui.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.twocents.mobile.ui.common.LinkifiedText
import com.twocents.mobile.kotlin.R
import java.util.concurrent.TimeUnit

private val TweetIdPattern = Regex("/status/(\\d+)")
private val TweetShape = RoundedCornerShape(15.dp)
private val NoTweetPadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

private enum class TweetMediaKind { Image, Gif, Video }
private data class TweetMedia(val url: String, val preview: String?, val kind: TweetMediaKind)
private data class NativeTweet(
    val url: String,
    val name: String,
    val handle: String,
    val avatar: String?,
    val verified: Boolean,
    val text: String,
    val createdTimestamp: Long,
    val media: List<TweetMedia>,
    val quote: NativeTweet?,
)

private object TweetRepository {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val cache = object : LinkedHashMap<String, NativeTweet>(48, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NativeTweet>?) = size > 48
    }

    suspend fun get(id: String): NativeTweet? = synchronized(cache) { cache[id] } ?: withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url("https://api.fxtwitter.com/status/$id").header("Accept", "application/json").build())
                .execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    JSONObject(response.body?.string().orEmpty()).optJSONObject("tweet")?.let(::parseTweet)
                }
        }.getOrNull()?.also { synchronized(cache) { cache[id] = it } }
    }

    private fun parseTweet(tweet: JSONObject, depth: Int = 0): NativeTweet {
        val author = tweet.optJSONObject("author") ?: JSONObject()
        val media = buildList {
            val mediaRoot = tweet.optJSONObject("media")
            mediaRoot?.optJSONArray("photos").objects().forEach { item ->
                val url = item.optString("url").takeIf(String::isNotBlank) ?: return@forEach
                add(TweetMedia(url, null, if (item.optString("type").equals("gif", true)) TweetMediaKind.Gif else TweetMediaKind.Image))
            }
            mediaRoot?.optJSONArray("videos").objects().forEach { item ->
                val type = item.optString("type")
                val url = item.optString("url").takeIf(String::isNotBlank)
                    ?: item.optString("transcode_url").takeIf(String::isNotBlank)
                    ?: return@forEach
                add(TweetMedia(url, item.optString("thumbnail_url").takeIf(String::isNotBlank), if (type.equals("gif", true)) TweetMediaKind.Gif else TweetMediaKind.Video))
            }
        }
        return NativeTweet(
            url = tweet.optString("url"),
            name = author.optString("name").ifBlank { author.optString("screen_name") },
            handle = author.optString("screen_name"),
            avatar = author.optString("avatar_url").takeIf(String::isNotBlank),
            verified = author.optJSONObject("verification")?.optBoolean("verified") == true || author.optBoolean("verified"),
            text = tweet.optString("text"),
            createdTimestamp = tweet.optLong("created_timestamp"),
            media = media,
            quote = if (depth < 2) tweet.optJSONObject("quote")?.let { parseTweet(it, depth + 1) } else null,
        )
    }
}

@Composable
internal fun TweetEmbedCard(tweetUrl: String) {
    val context = LocalContext.current
    val tweetId = remember(tweetUrl) { TweetIdPattern.find(tweetUrl)?.groupValues?.getOrNull(1) } ?: return
    var loading by remember(tweetId) { mutableStateOf(true) }
    var tweet by remember(tweetId) { mutableStateOf<NativeTweet?>(null) }
    LaunchedEffect(tweetId) { tweet = TweetRepository.get(tweetId); loading = false }

    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(TweetShape).background(Color(0xFF090909))
            .border(1.dp, Color.White.copy(alpha = .10f), TweetShape)
            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tweetUrl))) } }
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        when {
            loading -> Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White.copy(alpha = .45f), strokeWidth = 1.5.dp)
                Text("Loading post…", color = Color.White.copy(alpha = .4f), fontSize = 12.5.sp, style = NoTweetPadding)
            }
            tweet == null -> Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("View this post on X", color = Color.White.copy(alpha = .62f), fontSize = 13.sp, style = NoTweetPadding)
                Text("𝕏", color = Color.White, fontSize = 22.sp, style = NoTweetPadding)
            }
            else -> NativeTweetContent(tweet!!)
        }
    }
}

@Composable
private fun NativeTweetContent(tweet: NativeTweet, nested: Boolean = false) {
    var expanded by remember(tweet.url, tweet.text) { mutableStateOf(false) }
    val shouldTruncate = tweet.text.length > 400
    val visibleText = if (shouldTruncate && !expanded) tweet.text.take(400).trimEnd() + "…" else tweet.text
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = tweet.avatar, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(if (nested) 36.dp else 42.dp).clip(RoundedCornerShape(if (nested) 9.dp else 10.dp)).background(Color.White.copy(alpha = .06f)),
            )
            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tweet.name, color = Color.White, fontSize = if (nested) 13.5.sp else 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false), style = NoTweetPadding)
                    if (tweet.verified) TwitterVerifiedBadge(Modifier.padding(start = 4.dp))
                }
                Text("@${tweet.handle}", color = Color.White.copy(alpha = .40f), fontSize = if (nested) 11.5.sp else 12.5.sp, maxLines = 1, style = NoTweetPadding)
                if (tweet.createdTimestamp > 0) Text(tweetDate(tweet.createdTimestamp), color = Color.White.copy(alpha = .32f), fontSize = 10.5.sp, modifier = Modifier.padding(top = 2.dp), style = NoTweetPadding)
            }
            if (!nested) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text("𝕏", color = Color.White.copy(alpha = .94f), fontSize = 30.sp, fontWeight = FontWeight.Medium, style = NoTweetPadding)
                }
            }
        }
        if (visibleText.isNotBlank()) LinkifiedText(
            text = visibleText,
            color = Color.White.copy(alpha = .92f),
            fontSize = if (nested) 14.sp else 15.5.sp,
            lineHeight = if (nested) 19.sp else 21.sp,
            modifier = Modifier.padding(top = 12.dp),
            linkColor = Color(0xFF1D9BF0),
        )
        if (shouldTruncate) {
            Text(
                if (expanded) "Show less" else "Show more",
                color = Color(0xFF1D9BF0),
                fontSize = if (nested) 12.sp else 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp).clickable { expanded = !expanded },
                style = NoTweetPadding,
            )
        }
        TweetMediaContent(tweet.media)
        tweet.quote?.let { quote ->
            Column(
                Modifier.fillMaxWidth().padding(top = 11.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .018f))
                    .border(1.dp, Color.White.copy(alpha = .11f), RoundedCornerShape(12.dp)).padding(11.dp),
            ) { NativeTweetContent(quote, nested = true) }
        }
    }
}

@Composable
private fun TweetMediaContent(media: List<TweetMedia>) {
    if (media.isEmpty()) return
    val images = media.filter { it.kind == TweetMediaKind.Image }.map { it.url }
    if (images.isNotEmpty()) FeedPostMedia(images, compact = true, preserveFullImage = true)
    media.filter { it.kind != TweetMediaKind.Image }.forEach { item ->
        val actualImageGif = item.kind == TweetMediaKind.Gif && !item.url.contains(".mp4", true) && !item.url.contains(".m3u8", true)
        if (actualImageGif) {
            AsyncImage(
                model = item.url, contentDescription = "GIF", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp).heightIn(min = 150.dp, max = 300.dp).clip(RoundedCornerShape(11.dp)).background(Color.Black),
            )
        } else {
            FeedVideoPlayer(item.url, compact = true)
        }
    }
}

@Composable
private fun TwitterVerifiedBadge(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.twitter_verified),
        contentDescription = "Verified",
        modifier = modifier.size(15.dp),
    )
}

private fun tweetDate(timestamp: Long): String = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(Date(timestamp * 1000))
private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else buildList {
    for (index in 0 until length()) optJSONObject(index)?.let(::add)
}
