package com.wiliot.wiliotcore.embedded.auth

import android.os.NetworkOnMainThreadException
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.WiliotTokenKeeper
import com.wiliot.wiliotcore.contracts.PrimaryTokenExpirationCallback
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.isValidJwt
import com.wiliot.wiliotcore.utils.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object WiliotSdkAuthPoint : PrimaryTokenExpirationCallback {

    private val logTag = logTag()

    private val dummyLocker = DummyLocker()

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastConsumedToken: String? = null

    private fun getTokenAsync(callback: (String) -> Unit) {
        ioScope.launch {
            updateToken()?.let(callback)
        }
    }

    private fun updateToken(): String? {
        if (Wiliot.apiKey == null) throw RuntimeException(
            "API Key is not provided!"
        )
        synchronized(dummyLocker) {
            if (lastConsumedToken.isValidJwt("updateToken")) {
                return@synchronized
            }
            try {
                wltAuthApiService()
                    .apiKeyToAuth(
                        url = apiKeyAuthBaseUrl(),
                        apiKey = Wiliot.apiKey!!
                    ).execute()
                    .also { response ->
                        val accessToken: String = response.body()?.accessToken.orEmpty()
                        if (response.isSuccessful) {
                            lastConsumedToken = accessToken
                        } else {
                            lastConsumedToken = null
                            if (response.code() == 401) {
                                handleInvalidToken()
                            }
                        }
                        return@synchronized
                    }
            } catch (e: Exception) {
                Reporter.exception("Error occurred in updateToken()", e, logTag)
                if (e is NetworkOnMainThreadException) throw e
                if (e is InvalidApiKeyException) throw e
                lastConsumedToken = null
                return@synchronized
            }
        }
        return lastConsumedToken
    }

    private fun apiKeyAuthBaseUrl(): String {
        val base = Wiliot.configuration.environment.coreApiBase().let {
            if (it.endsWith("/")) {
                val i = it.lastIndex
                it.substring(0, i)
            } else it
        }

        return "$base/v1/auth/token/api"
    }

    override fun onPrimaryTokenExpired() {
        getTokenAsync { token ->
            WiliotTokenKeeper.setNewToken(token)
        }
    }

    private fun handleInvalidToken() {
        throw InvalidApiKeyException("Invalid API Key")
    }

}

private class InvalidApiKeyException(
    override val message: String? = null,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)

private class DummyLocker