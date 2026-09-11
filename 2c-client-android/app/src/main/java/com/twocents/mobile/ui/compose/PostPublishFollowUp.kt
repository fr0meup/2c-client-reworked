package com.twocents.mobile.ui.compose

import com.twocents.mobile.ui.common.AppToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Run only after create has succeeded. Follow-up failures are separate from
 * publication, and UI work must receive a composition-owned scope/frame clock. */
internal fun CoroutineScope.launchPostPublishFollowUp(
    failureMessage: String,
    reportFailure: (String) -> Unit = { AppToast.error(it) },
    block: suspend () -> Unit,
) = launch {
    try { block() }
    catch (error: CancellationException) { throw error }
    catch (_: Exception) { reportFailure(failureMessage) }
}
