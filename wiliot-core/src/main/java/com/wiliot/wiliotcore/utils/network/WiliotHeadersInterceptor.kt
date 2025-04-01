package com.wiliot.wiliotcore.utils.network

import android.os.Build
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor to add additional headers to the HTTP requests. Basically it adds User-Agent header
 * that contains information about app version and Device/OS version
 */
class WiliotHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.run {
        val url = chain.request()
            .url
            .newBuilder()
            .build()
        val request = chain.request()
            .newBuilder()
            .addHeader("User-Agent", userAgent())
            .url(url)
            .build()
        return chain.proceedSafely(request)
    }
}

private fun userAgent(): String = StringBuilder()
    .append("Wiliot App V3 ${WiliotHeadersSystemInfo.appVersion} BUILD ${WiliotHeadersSystemInfo.appVersionCode}")
    .append(" ")
    .append("OS ${WiliotHeadersSystemInfo.os} ${WiliotHeadersSystemInfo.osVersion}")
    .append(" ")
    .append("DEVICE ${WiliotHeadersSystemInfo.deviceName}")
    .append(" ")
    .append("GW ID ${WiliotHeadersSystemInfo.gwId}")
    .toString()

object WiliotHeadersSystemInfo {

    const val os = "Android"

    val osVersion: String = Build.VERSION.RELEASE

    val deviceName: String
        get() {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            return if (model.startsWith(manufacturer)) {
                model
            } else {
                "$manufacturer $model"
            }
        }

    /**
     * Application version name could not be retrieved by SDK itself. So it is necessary to provide it
     * at Application startup. It is recommended to use it in 'Application.onCreate()' method.
     */
    var appVersion: String = "Unknown"

    /**
     * Application version code could not be retrieved by SDK itself. So it is necessary to provide it
     * at Application startup. It is recommended to use it in 'Application.onCreate()' method.
     */
    var appVersionCode: String = "Unknown"

    /**
     * GW ID should be provided at Application startup.
     * It is recommended to use it in 'Application.onCreate()' method.
     */
    var gwId: String = "Unknown"

}