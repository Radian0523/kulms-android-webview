package com.radian0523.kulms_plus_for_android.ui.login

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.radian0523.kulms_plus_for_android.R
import com.radian0523.kulms_plus_for_android.data.WebViewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    var isVerifying by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            if (errorText != null) {
                Text(
                    text = errorText!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    if (isVerifying) return@Button
                    isVerifying = true
                    errorText = null

                    scope.launch {
                        try {
                            // セッション確認: LMS の API を叩いてレスポンスを検証
                            val confirmed = withContext(Dispatchers.Main) {
                                val url = WebViewManager.webView.url
                                url != null && url.startsWith(WebViewManager.BASE_URL)
                            }
                            if (confirmed) {
                                WebViewManager.setLoggedIn(true)
                            } else {
                                errorText = context.getString(R.string.session_not_confirmed)
                            }
                        } catch (e: Exception) {
                            errorText = context.getString(R.string.verification_failed, e.localizedMessage ?: "")
                        }
                        isVerifying = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isVerifying
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Text("  " + stringResource(R.string.verifying), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.login_done))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tap_after_auth),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onBack, enabled = !isVerifying) {
                Text(stringResource(R.string.back_to_credentials))
            }
        }
    }
}
