package com.wiliot.wiliotnetworkmeta.interceptor

import com.wiliot.wiliotcore.contracts.PrimaryTokenExpirationCallback
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.isValidJwt
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotnetworkmeta.BuildConfig
import com.wiliot.wiliotnetworkmeta.WiliotNetworkMeta
import com.wiliot.wiliotnetworkmeta.utils.signedRequest
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

internal class MetaTokenRefreshAuthenticator(
    private val primaryTokenExpirationCallback: PrimaryTokenExpirationCallback?
): Authenticator {

    private val logTag = logTag()

    override fun authenticate(route: Route?, response: Response): Request? = when {
        response.retryCount > 2 -> null
        else -> response.createSignedRequest()
    }

    private fun Response.createSignedRequest(): Request? = try {
        val accessToken = WiliotNetworkMeta.configuration.authToken
        if (accessToken.isValidJwt("TokenRefreshAuthenticator[gw_access_token]").not())
            primaryTokenExpirationCallback?.onPrimaryTokenExpired()
        request.signedRequest(WiliotNetworkMeta.configuration.authToken).also {
            if (BuildConfig.DEBUG) Reporter.log("Request SIGNED: ${it.url}, ${it.headers["Authorization"]}", logTag)
        }
    } catch (error: Throwable) {
        Reporter.log("Failed to re-sign request", logTag, highlightError = true)
        null
    }

    private val Response.retryCount: Int
        get() {
            var currentResponse = priorResponse
            var result = 0
            while (currentResponse != null) {
                result++
                currentResponse = currentResponse.priorResponse
            }
            return result
        }

}