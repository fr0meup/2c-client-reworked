package com.twocents.mobile.ui.compose

import com.twocents.mobile.ui.common.validEquitySymbol
import com.twocents.mobile.ui.common.validPredictionDate
import java.time.LocalDate

/** Validate before any upload or publication; don't silently discard another attachment. */
internal fun validateComposePrediction(draft: ComposePostDraft, today: LocalDate = LocalDate.now()) {
    if (draft.option != ComposePostOption.EquityPrediction && draft.equityPrediction == null) return
    val prediction = requireNotNull(draft.equityPrediction) { "Choose an equity prediction first" }
    require(draft.option == ComposePostOption.EquityPrediction && validEquitySymbol(prediction.symbol)) { "Invalid equity prediction" }
    require(prediction.targetPrice.isFinite() && prediction.targetPrice > 0) { "Enter a valid target price" }
    val date = runCatching { LocalDate.parse(prediction.targetDate) }.getOrNull()
    require(date != null && validPredictionDate(date, today)) { "Choose a future weekday for the prediction" }
    require(draft.equityChart == null && draft.mediaUris.isEmpty() && draft.quotedPost == null && draft.pollLink.isNullOrBlank()) {
        "Equity predictions cannot be combined with other attachments"
    }
}
