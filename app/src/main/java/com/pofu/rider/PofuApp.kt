package com.pofu.rider

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.pofu.rider.core.Prefs

class PofuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sürüş modu çalışırken görünen kalıcı bildirim"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "pofu_ride"
    }
}
