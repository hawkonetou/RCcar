package com.hotwheels.command.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

object PermissionUtils {
    const val BT_CONNECT = Manifest.permission.BLUETOOTH_CONNECT

    fun hasBluetoothConnect(context: Context): Boolean =
        context.checkSelfPermission(BT_CONNECT) == PackageManager.PERMISSION_GRANTED
}
