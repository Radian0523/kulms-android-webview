package com.radian0523.kulms_plus_for_android.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.radian0523.kulms_plus_for_android.R
import com.radian0523.kulms_plus_for_android.data.CredentialStore
import com.radian0523.kulms_plus_for_android.data.WebViewManager
import kotlinx.coroutines.delay

/**
 * メイン画面: LMS を WebView でフルスクリーン表示し、
 * 拡張機能のコンテンツスクリプトが注入された状態で操作できる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LMSWebViewScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // WebView のナビゲーション状態をポーリングで監視
    LaunchedEffect(Unit) {
        WebViewManager.loadPortal()
        while (true) {
            delay(300)
            try {
                canGoBack = WebViewManager.webView.canGoBack()
                canGoForward = WebViewManager.webView.canGoForward()
            } catch (_: Exception) {}
        }
    }

    // セッション切れ検知
    DisposableEffect(Unit) {
        WebViewManager.onSessionExpired = {
            onLogout()
        }
        onDispose {
            WebViewManager.onSessionExpired = null
        }
    }

    // ハードウェア/ジェスチャーの「戻る」ボタン
    BackHandler(enabled = canGoBack) {
        WebViewManager.webView.goBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("KULMS+") },
            navigationIcon = {
                IconButton(
                    onClick = { WebViewManager.webView.goBack() },
                    enabled = canGoBack
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                }
            },
            actions = {
                IconButton(
                    onClick = { WebViewManager.webView.goForward() },
                    enabled = canGoForward
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.go_forward))
                }
                IconButton(onClick = { WebViewManager.webView.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload))
                }
                IconButton(onClick = { showLogoutConfirm = true }) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.logout))
                }
            }
        )

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { _ ->
                WebViewManager.webView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    (parent as? ViewGroup)?.removeView(this)
                }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.logout_confirm)) },
            text = { Text(stringResource(R.string.logout_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    WebViewManager.clearData()
                    CredentialStore.clear(context)
                    WebViewManager.setLoggedIn(false)
                    onLogout()
                }) {
                    Text(stringResource(R.string.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
