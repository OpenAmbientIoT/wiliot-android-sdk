package com.wiliot.wiliotcore.utils.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Safely proceed [Interceptor.Chain] taking into account auth endpoints.
 * In case auth-related request will fail due to network-related issue, this function
 * will generate synthetic HTTP Response with 418 status code that could be handled or skipped later.
 * In case auth-related request will fail due to non network-related issue, this function
 * will rethrow original [Exception] for further handling.
 * In case non auth-related request will fail by any reason it will be proceeded in a regular way.
 */
fun Interceptor.Chain.proceedSafely(request: Request): Response {
    if (isAuthUrl(request.url.toString())) {
        return try {
            proceed(request)
        } catch (ex: Exception) {
            when (ex) {
                is UnknownHostException,
                is SocketTimeoutException,
                -> Response.Builder()
                    .protocol(Protocol.HTTP_2)
                    .message("Fail")
                    .request(request)
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .code(418)
                    .build()
                else -> throw ex
            }
        }
    } else {
        return proceed(request)
    }
}

private fun isAuthUrl(url: String?): Boolean {
    if (url == null) return false
    if (url.contains("oauth2/token")) return true
    if (url.contains("auth/token/api")) return true
    if (url.contains("gateway/refresh")) return true
    if (url.contains("/mobile")) return true
    return false
}