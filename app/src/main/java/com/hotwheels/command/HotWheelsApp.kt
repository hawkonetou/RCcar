package com.hotwheels.command

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.hotwheels.command.data.SessionStatsStore
import com.hotwheels.command.data.SteeringEnabledStore
import com.hotwheels.command.data.ThrottleLimitStore
import com.hotwheels.command.data.TuningStore
import com.hotwheels.command.ui.theme.ThemeStore
import com.hotwheels.command.util.DiagLog

class HotWheelsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        ThemeStore.init(this)
        ThrottleLimitStore.init(this)
        SteeringEnabledStore.init(this)
        TuningStore.init(this)
        SessionStatsStore.init(this)
        DiagLog.log("APP", "onCreate start")
        installCrashHandler()
        try {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null) }
            mgr.createNotificationChannel(channel)
            DiagLog.log("APP", "notif channel created id=$NOTIF_CHANNEL_ID")
        } catch (e: Throwable) {
            DiagLog.log("APP", "notif channel FAILED ${e::class.java.simpleName}: ${e.message}")
            throw e
        }
        DiagLog.log("APP", "onCreate done")
    }

    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            val stack = ex.stackTraceToString().lines().take(20).joinToString(" | ")
            DiagLog.log("CRASH", "${ex::class.java.simpleName}: ${ex.message} :: $stack")
            prev?.uncaughtException(thread, ex)
        }
    }

    companion object {
        const val NOTIF_CHANNEL_ID = "bluetooth_car_connection"
    }
}
