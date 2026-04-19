package com.radian0523.kulms_plus_for_android

import android.app.Application
import com.radian0523.kulms_plus_for_android.data.WebViewManager
import com.radian0523.kulms_plus_for_android.notification.NotificationHelper

class KULMSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WebViewManager.init(this)
        NotificationHelper.createChannel(this)
    }
}
