package com.twocents.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.twocents.mobile.ui.settings.InteractionPreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Same cached card in feed/detail, comments and chat; text remains selectable separately. */
@Composable
internal fun LinkPreviewCards(text: String, attached: String? = null, modifier: Modifier = Modifier) {
    val links = remember(text, attached) { previewLinks(text, attached) }
    if (links.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        links.forEach { url -> key(url) { LinkPreviewCard(url) } }
    }
}

@Composable
private fun LinkPreviewCard(url: String) {
    val context = LocalContext.current
    val handler = LocalUriHandler.current
    val wifiOnly = InteractionPreferences.wifiOnlyMedia(context)
    var preview by remember(url) { mutableStateOf<LinkPreview?>(null) }
    LaunchedEffect(url, wifiOnly) {
        if (InteractionPreferences.automaticMediaAllowed(context)) {
            preview = LinkPreviewRepository.get(context.applicationContext, url) { metadata ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { preview = metadata }
            }
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(shape)
        .background(Color(0xFF171713)).border(.6.dp, Color.White.copy(alpha = .12f), shape)
        .clickable {
            if (!AppLinkRouter.open(url)) runCatching { handler.openUri(url) }
        }) {
        preview?.image?.let { image ->
            AsyncImage(image, null, Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Crop)
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(preview?.title?.ifBlank { null } ?: url.toHttpUrlOrNull()?.host.orEmpty(),
                color = Color.White.copy(alpha = .92f), fontSize = 13.sp, lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            preview?.description?.takeIf(String::isNotBlank)?.let {
                Text(it, color = Color.White.copy(alpha = .58f), fontSize = 12.sp, lineHeight = 16.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(url.removePrefix("https://").removePrefix("http://"), color = Color(0xFFC8A44D),
                fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}
