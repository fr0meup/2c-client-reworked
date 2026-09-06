package com.twocents.mobile.updates

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.common.AppToast

@Composable
internal fun UpdatePrompt() {
    val update by AppUpdates.available.collectAsState()
    val uriHandler = LocalUriHandler.current
    val release = update ?: return
    AlertDialog(
        onDismissRequest = AppUpdates::dismiss,
        containerColor = Color(0xFF141410),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = .75f),
        title = { Text("2c ${release.version} is available") },
        text = {
            Text(release.notes.ifBlank { "A newer version of 2c is ready." },
                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                fontSize = 13.sp, lineHeight = 18.sp)
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { uriHandler.openUri(release.download) }
                    .onSuccess { AppUpdates.downloadOpened() }
                    .onFailure { AppToast.error("Couldn't open the download") }
            }) { Text("Download update", color = Color(0xFFC8A44D)) }
        },
        dismissButton = {
            TextButton(onClick = AppUpdates::dismiss) { Text("Later", color = Color.White.copy(alpha = .6f)) }
        },
    )
}
