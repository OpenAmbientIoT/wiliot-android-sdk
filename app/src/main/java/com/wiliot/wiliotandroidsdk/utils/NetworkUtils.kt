package com.wiliot.wiliotandroidsdk.utils

import android.content.Context
import android.net.ConnectivityManager

object NetworkUtils {
    fun isInternetConnected(applicationContext: Context): Boolean {
        var status = false
        val cm =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            if (cm.activeNetwork != null && cm.getNetworkCapabilities(cm.activeNetwork) != null) {
                status = true
            }
        }
        return status
    }
}