package com.twocents.mobile.ui.compose

import android.content.Context
import android.net.Uri

internal data class ComposeMediaSelection(
    val uris: List<String>,
    val option: ComposePostOption?,
    val clearPollLink: Boolean,
)

/** Applies the existing four-image/one-video policy before editor state is mutated. */
internal fun resolveComposeMediaSelection(
    context: Context,
    selectedUris: List<Uri>,
    currentUris: List<String>,
    activeOption: ComposePostOption?,
): ComposeMediaSelection? {
    val selected = selectedUris.distinct().filter { uri ->
        val type = context.contentResolver.getType(uri).orEmpty()
        type.startsWith("image/") || type.startsWith("video/")
    }
    if (selected.isEmpty()) return null
    val selectedVideos = selected.filter { context.contentResolver.getType(it)?.startsWith("video/") == true }
    val resolved = if (activeOption == ComposePostOption.Poll) {
        selected.filter { context.contentResolver.getType(it)?.startsWith("image/") == true }
            .map(Uri::toString).distinct().take(4)
    } else if (selectedVideos.isNotEmpty()) {
        listOf(selectedVideos.first().toString())
    } else {
        val existingImages = currentUris.filter { raw ->
            context.contentResolver.getType(Uri.parse(raw))?.startsWith("video/") != true
        }
        val selectedImages = selected.filter { context.contentResolver.getType(it)?.startsWith("image/") == true }
        (existingImages + selectedImages.map(Uri::toString)).distinct().take(4)
    }
    return ComposeMediaSelection(
        uris = resolved,
        option = if (activeOption == ComposePostOption.Poll) activeOption else ComposePostOption.Image,
        clearPollLink = activeOption == ComposePostOption.Poll,
    )
}
