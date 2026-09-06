package com.twocents.mobile

import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.twocents.mobile.ui.app.TwoCentsApp
import com.twocents.mobile.ui.theme.TwoCentsTheme
import com.twocents.mobile.notifications.NotificationNavigationBus
import com.twocents.mobile.ui.common.AppLinkRouter

class MainActivity : ComponentActivity() {
    private val authStore by lazy { SecureAuthStore(applicationContext) }
    private val rpcApi by lazy { RpcApi() }
    private val mediaPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra("open_notifications", false) == true) {
            NotificationNavigationBus.openNotifications(
                postUuid = intent.getStringExtra("post_uuid"),
                commentUuid = intent.getStringExtra("comment_uuid"),
                roomUuid = intent.getStringExtra("room_uuid"),
                messageUuid = intent.getStringExtra("message_uuid"),
            )
        }
        intent?.dataString?.let(AppLinkRouter::open)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false

        setContent {
            TwoCentsTheme {
                TwoCentsApp(
                    authStore = authStore,
                    rpcApi = rpcApi,
                )
            }
        }
        requestMediaAccessIfNeeded()
    }

    private fun requestMediaAccessIfNeeded() {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = required.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) mediaPermissions.launch(missing.toTypedArray())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_notifications", false)) {
            NotificationNavigationBus.openNotifications(
                postUuid = intent.getStringExtra("post_uuid"),
                commentUuid = intent.getStringExtra("comment_uuid"),
                roomUuid = intent.getStringExtra("room_uuid"),
                messageUuid = intent.getStringExtra("message_uuid"),
            )
        }
        intent.dataString?.let(AppLinkRouter::open)
    }
}
