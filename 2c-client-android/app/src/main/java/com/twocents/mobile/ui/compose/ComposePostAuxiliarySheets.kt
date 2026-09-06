package com.twocents.mobile.ui.compose

import androidx.compose.runtime.Composable

/** Modal-adjacent pickers are stateless; session restoration remains in ComposePostModal. */
@Composable
internal fun ComposePostAuxiliarySheets(
    gifPickerOpen: Boolean,
    draftsOpen: Boolean,
    drafts: List<StoredComposeDraft>,
    onDismissGifPicker: () -> Unit,
    onSelectGif: (String) -> Unit,
    onDismissDrafts: () -> Unit,
    onLoadDraft: (StoredComposeDraft) -> Unit,
    onDeleteDraft: (StoredComposeDraft) -> Unit,
) {
    if (gifPickerOpen) {
        GifPickerSheet(onDismiss = onDismissGifPicker, onSelect = onSelectGif)
    }
    if (draftsOpen) {
        ComposeDraftPickerSheet(
            drafts = drafts,
            onDismiss = onDismissDrafts,
            onLoad = onLoadDraft,
            onDelete = onDeleteDraft,
        )
    }
}
