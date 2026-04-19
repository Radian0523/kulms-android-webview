package com.radian0523.kulms_plus_for_android

import android.app.Application
import com.radian0523.kulms_plus_for_android.data.WebViewManager

class KULMSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WebViewManager.init(this)
    }
}
