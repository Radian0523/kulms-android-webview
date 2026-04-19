package com.radian0523.kulms_plus_for_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.radian0523.kulms_plus_for_android.data.WebViewManager
import com.radian0523.kulms_plus_for_android.ui.LMSWebViewScreen
import com.radian0523.kulms_plus_for_android.ui.login.LoginScreen
import com.radian0523.kulms_plus_for_android.ui.theme.KULMSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            KULMSTheme {
                MainContent()
            }
        }
    }
}

@Composable
fun MainContent() {
    val isLoggedIn by WebViewManager.isLoggedIn.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoggedIn) {
            LMSWebViewScreen(onLogout = {
                WebViewManager.setLoggedIn(false)
            })
        } else {
            LoginScreen()
        }
    }
}
