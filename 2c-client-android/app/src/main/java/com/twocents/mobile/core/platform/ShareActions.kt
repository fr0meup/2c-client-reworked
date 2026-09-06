package com.twocents.mobile.core.platform

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.twocents.mobile.ui.common.AppToast

/** Platform actions shared by menus, lightboxes, profiles, and room invites. */
internal object ShareActions {
    fun copyText(context: Context, label: String, value: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(label, value))
    }

    fun shareText(context: Context, value: String, chooserTitle: String? = null) {
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, value)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    chooserTitle,
                ),
            )
        }
    }

    fun downloadImage(context: Context, uri: String) {
        runCatching {
            val extension = Uri.parse(uri).lastPathSegment?.substringAfterLast('.', "jpg")?.take(5) ?: "jpg"
            val fileName = "2c_image_${System.currentTimeMillis()}.$extension"
            val request = DownloadManager.Request(Uri.parse(uri))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, fileName)
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            AppToast.success("Image download started")
        }.onFailure {
            AppToast.error("Couldn't download the image")
        }
    }
}
