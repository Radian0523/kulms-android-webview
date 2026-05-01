package com.radian0523.kulms_plus_for_android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.radian0523.kulms_plus_for_android.data.CredentialStore
import com.radian0523.kulms_plus_for_android.data.WebViewManager
import com.radian0523.kulms_plus_for_android.ui.LMSWebViewScreen
import com.radian0523.kulms_plus_for_android.ui.login.LoginScreen
import com.radian0523.kulms_plus_for_android.ui.settings.SettingsScreen
import com.radian0523.kulms_plus_for_android.ui.theme.KULMSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()

        setContent {
            KULMSTheme {
                MainContent()
            }
        }

        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val url = intent?.getStringExtra("targetUrl") ?: return
        if (url.isNotEmpty()) {
            WebViewManager.webView.post {
                WebViewManager.webView.loadUrl(url)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }
}

@Composable
fun MainContent() {
    val context = LocalContext.current
    val isLoggedIn by WebViewManager.isLoggedIn.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoggedIn) {
            LMSWebViewScreen(
                onLogout = { WebViewManager.setLoggedIn(false) },
                onSettings = { showSettings = true }
            )
            if (showSettings) {
                SettingsScreen(
                    onBack = { showSettings = false },
                    onLogout = {
                        showSettings = false
                        WebViewManager.clearData()
                        CredentialStore.clear(context)
                        WebViewManager.setLoggedIn(false)
                    }
                )
            }
        } else {
            LoginScreen()
        }
    }
}
