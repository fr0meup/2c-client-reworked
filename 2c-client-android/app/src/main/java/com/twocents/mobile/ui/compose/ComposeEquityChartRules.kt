package com.twocents.mobile.ui.compose

import com.twocents.mobile.ui.common.validEquitySymbol

/** The documented type-6 contract is a standalone chart, not a poll/media/quote combination. */
internal fun validateComposeChart(draft: ComposePostDraft) {
    if (draft.option != ComposePostOption.EquityChart && draft.equityChart == null) return
    require(draft.option == ComposePostOption.EquityChart && draft.equityChart != null) { "Choose an equity chart first" }
    require(validEquitySymbol(draft.equityChart.symbol)) { "Invalid chart ticker" }
    require(draft.equityPrediction == null && draft.mediaUris.isEmpty() && draft.quotedPost == null && draft.pollLink.isNullOrBlank()) {
        "Equity charts cannot be combined with other attachments"
    }
}
