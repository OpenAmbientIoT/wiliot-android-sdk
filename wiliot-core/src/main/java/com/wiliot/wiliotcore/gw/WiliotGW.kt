package com.wiliot.wiliotcore.gw

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.util.*

@SuppressLint("HardwareIds")
internal fun Context.gwId(): String {
    return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).uppercase(
        Locale.ROOT
    )
}