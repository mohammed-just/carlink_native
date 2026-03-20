package com.carlink.util

import android.app.PendingIntent
import android.os.Build

/** API-safe PendingIntent flag helpers for mixed Android 9 / modern AAOS builds. */
object PendingIntentFlagsCompat {
    fun mutableUpdateCurrent(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }

    fun immutableUpdateCurrent(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
}
