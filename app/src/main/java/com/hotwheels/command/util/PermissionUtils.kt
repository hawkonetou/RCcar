package com.hotwheels.command.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

object PermissionUtils {
    const val BT_CONNECT = Manifest.permission.BLUETOOTH_CONNECT
    const val BT_SCAN = Manifest.permission.BLUETOOTH_SCAN

    val required: Array<String> = arrayOf(BT_CONNECT, BT_SCAN)

    fun hasAllRequired(context: Context): Boolean =
        required.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun missing(context: Context): List<String> =
        required.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
}
