package com.twocents.mobile.ui.compose

/** Keep server type selection independently testable; quote posts retain their existing precedence. */
internal fun composePostType(draft: ComposePostDraft, hasVideo: Boolean = false, hasImages: Boolean = false): Int = when {
    draft.quotedPost != null -> 3
    draft.equityChart != null -> 6
    draft.equityPrediction != null -> 11
    draft.option == ComposePostOption.Poll -> 2
    hasVideo -> 10
    hasImages -> 4
    draft.option == ComposePostOption.Likert -> 5
    else -> 0
}
