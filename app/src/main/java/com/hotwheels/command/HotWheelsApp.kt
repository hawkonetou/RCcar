package com.hotwheels.command

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class HotWheelsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setSound(null, null) }
        mgr.createNotificationChannel(channel)
    }
    companion object {
        const val NOTIF_CHANNEL_ID = "bluetooth_car_connection"
    }
}
