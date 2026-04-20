package com.radian0523.kulms_plus_for_android.ui.login

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.radian0523.kulms_plus_for_android.R
import com.radian0523.kulms_plus_for_android.data.WebViewManager

/**
 * ログイン画面のルート。
 * デフォルトでは独自 UI（CredentialLoginScreen）を表示。
 * 多要素認証が必要な場合や、ユーザーが明示的に選択した場合は WebView ログイン UI に切り替える。
 */
@Composable
fun LoginScreen() {
    var useWebView by remember { mutableStateOf(false) }
    val isLoggedIn by WebViewManager.isLoggedIn.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            useWebView = false
        }
    }

    if (useWebView) {
        WebViewLoginPanel(
            onBack = { useWebView = false }
        )
    } else {
        CredentialLoginScreen(
            onRequireWebViewLogin = { useWebView = true }
        )
    }
}

/**
 * 従来の WebView ベースのログイン UI（多要素認証用フォールバック）。
 */
@Composable
private fun WebViewLoginPanel(
    onBack: () -> Unit
) {
    DisposableEffect(Unit) {
        WebViewManager.onPortalReached = {
            WebViewManager.setLoggedIn(true)
        }
        onDispose {
            WebViewManager.onPortalReached = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
            },
            update = { _ -> }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back_to_credentials))
            }
        }
    }
}
