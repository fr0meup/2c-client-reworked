package com.twocents.mobile.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import com.twocents.mobile.ui.theme.Gold
import com.twocents.mobile.ui.feed.FeedVideoPlayer

@Composable
fun ComposePollCard(
    options: List<String>,
    onChange: (List<String>) -> Unit,
    link: String,
    onLinkChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var linkExpanded by remember { mutableStateOf(link.isNotBlank()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141410))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Poll Options", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Text(
                "Remove",
                color = Color(0xFFF43F5E),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onRemove),
            )
        }
        options.forEachIndexed { index, option ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComposeSmallInput(
                    value = option,
                    placeholder = "Option ${index + 1}",
                    onValueChange = { value ->
                        onChange(options.mapIndexed { i, old -> if (i == index) value else old })
                    },
                    modifier = Modifier.weight(1f),
                )
                if (options.size > 2) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Remove option",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { onChange(options.filterIndexed { i, _ -> i != index }) }
                            .padding(6.dp),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (options.size < 4) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onChange(options + "") }
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.Add, null, tint = Gold, modifier = Modifier.size(14.dp))
                    Text("Add option", color = Gold.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (!linkExpanded) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { linkExpanded = true }
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.Add, null, tint = Gold, modifier = Modifier.size(14.dp))
                    Text("Add a link", color = Gold.copy(alpha = .9f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            AnimatedVisibility(
                visible = linkExpanded,
                enter = fadeIn(tween(170)) + expandVertically(tween(220), expandFrom = Alignment.Top),
                exit = fadeOut(tween(170)) + shrinkVertically(tween(220), shrinkTowards = Alignment.Top),
            ) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    ComposeSmallInput(
                        value = link,
                        placeholder = "Paste a link",
                        onValueChange = onLinkChange,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Remove link",
                        tint = Color.White.copy(alpha = .42f),
                        modifier = Modifier.size(26.dp).clickable {
                            onLinkChange("")
                            linkExpanded = false
                        }.padding(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposeSmallInput(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(80)) },
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
            ),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(
                    placeholder,
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    style = TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                )
                inner()
            },
        )
    }
}

@Composable
fun ComposeLikertCard(onRemove: () -> Unit) {
    val labels = listOf("Strongly Disagree", "Disagree", "Neutral", "Agree", "Strongly Agree")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF161511))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("LIKERT SCALE", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(14.dp)) {
                    val scale = size.minDimension / 24f
                    val stroke = 2.5.dp.toPx() / scale
                    val path = Path().apply {
                        moveTo(18f, 6f)
                        lineTo(6f, 18f)
                        moveTo(6f, 6f)
                        lineTo(18f, 18f)
                    }
                    withTransform({ scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
                        drawPath(
                            path,
                            Color.White.copy(alpha = 0.45f),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
            labels.forEach { label ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("•", color = Color.White.copy(alpha = 0.25f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        label,
                        color = Color.White.copy(alpha = 0.28f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 12.sp,
                        minLines = 2,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ComposeMediaRow(
    mediaUris: List<String>,
    onRemove: (Int) -> Unit,
) {
    if (mediaUris.isEmpty()) return
    val context = LocalContext.current
    val indexedMedia = mediaUris.withIndex().toList()
    if (mediaUris.size == 1) {
        ComposeMediaCard(
            uri = mediaUris.first(),
            index = 0,
            onRemove = onRemove,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        listOf(
            indexedMedia.filter { it.index % 2 == 0 },
            indexedMedia.filter { it.index % 2 == 1 },
        ).forEach { columnMedia ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                columnMedia.forEach { indexed ->
                    ComposeMediaCard(
                        uri = indexed.value,
                        index = indexed.index,
                        onRemove = onRemove,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposeMediaCard(
    uri: String,
    index: Int,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isVideo = remember(uri) {
        context.contentResolver.getType(android.net.Uri.parse(uri))?.startsWith("video/") == true
    }
    if (isVideo) {
        Box(modifier = modifier.fillMaxWidth()) {
            FeedVideoPlayer(uri = uri, compact = true, modifier = Modifier.fillMaxWidth())
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 13.dp, end = 5.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { onRemove(index) }
                    .padding(5.dp),
            )
        }
        return
    }
    var ratio by remember(uri) { mutableStateOf(16f / 9f) }
    Box(
        modifier = modifier
            .aspectRatio(ratio.coerceAtLeast(0.2f))
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0A0907))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(uri).build(),
            contentDescription = "Selected image",
            contentScale = ContentScale.Crop,
            onSuccess = { success ->
                val image = success.result.image
                if (image.width > 0 && image.height > 0) {
                    ratio = image.width.toFloat() / image.height.toFloat()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            Icons.Outlined.Close,
            contentDescription = "Remove media",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(21.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable { onRemove(index) }
                .padding(4.dp),
        )
    }
}
