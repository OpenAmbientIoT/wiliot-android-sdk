package com.wiliot.wiliotqueue

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.addToTokenInjectionRegistry
import com.wiliot.wiliotcore.contracts.CommandsQueueManagerContract
import com.wiliot.wiliotcore.contracts.MessageQueueManagerContract
import com.wiliot.wiliotcore.contracts.WiliotQueueModule
import com.wiliot.wiliotcore.embedded.auth.PrimaryTokenInjectionConsumer
import com.wiliot.wiliotcore.legacy.EnvironmentWiliot
import com.wiliot.wiliotcore.registerModule
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotqueue.config.Configuration
import com.wiliot.wiliotqueue.di.commandsQueueManager
import com.wiliot.wiliotqueue.di.messageQueueManager
import com.wiliot.wiliotqueue.repository.TokenStorageSource
import com.wiliot.wiliotqueue.repository.tokenStorageSource

object WiliotQueue : PrimaryTokenInjectionConsumer, WiliotQueueModule {

    var configuration: Configuration = Configuration()

    private var initialized: Boolean = false

    private lateinit var mTokenStorage: TokenStorageSource

    private val logTag = logTag()

    internal fun initialize() {
        initialized.takeIf { !it }.apply {
            initialized = true
            mTokenStorage = tokenStorageSource()
            Wiliot.registerModule(this@WiliotQueue)
        }
    }

    /**
     * Use it in case of logout (manual)
     *
     * If [ownerId] is not specified, than all the tokens will be cleared for environment
     */
    fun clearGwTokens(env: EnvironmentWiliot, ownerId: String?) {
        resetGwTokens()
        ownerId?.let {
            mTokenStorage.saveGwRefreshToken(null, env, it)
        } ?: kotlin.run {
            mTokenStorage.clearAllTokens(env)
        }
    }

    private fun resetGwTokens() {
        Reporter.log("resetGwTokens", logTag)
        configuration = configuration.copy(gwToken = null, gwRefreshToken = null)
    }

    override fun injectToken(token: String?) {
        if (token == null) {
            resetGwTokens()
        } else {
            configuration = configuration.copy(authToken = token)
        }
    }

    override fun msgQueueManager(): MessageQueueManagerContract = messageQueueManager()

    override fun cmdQueueManager(): CommandsQueueManagerContract = commandsQueueManager()

    override fun setBleLogsEnabled(enabled: Boolean) {
        configuration = configuration.copy(
            sendLogs = enabled
        )
    }

}

@Suppress("UnusedReceiverParameter")
fun Wiliot.WiliotInitializationScope.initQueue() {
    if (Wiliot.isInitialized.not()) throw RuntimeException(
        "Unable to execute `initQueue`. First you should initialize Wiliot with ApplicationContext."
    )
    WiliotQueue.initialize()
    Wiliot.addToTokenInjectionRegistry(WiliotQueue)
}