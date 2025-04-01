package com.wiliot.wiliotnetworkmeta

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.addToTokenInjectionRegistry
import com.wiliot.wiliotcore.contracts.MetaNetworkManagerContract
import com.wiliot.wiliotcore.contracts.WiliotNetworkMetaModule
import com.wiliot.wiliotcore.embedded.auth.PrimaryTokenInjectionConsumer
import com.wiliot.wiliotcore.registerModule
import com.wiliot.wiliotnetworkmeta.config.Configuration
import com.wiliot.wiliotnetworkmeta.di.metaNetworkManager

object WiliotNetworkMeta : PrimaryTokenInjectionConsumer, WiliotNetworkMetaModule {

    var configuration: Configuration = Configuration()

    override fun injectToken(token: String?) {
        configuration = configuration.copy(authToken = token)
    }

    override fun networkManager(): MetaNetworkManagerContract = metaNetworkManager()

    fun initialize() {
        Wiliot.addToTokenInjectionRegistry(this)
        Wiliot.registerModule(this)
    }

}

@Suppress("UnusedReceiverParameter")
fun Wiliot.WiliotInitializationScope.initMetaNetwork() {
    if (Wiliot.isInitialized.not()) throw RuntimeException(
        "Unable to execute `initMetaNetwork`. First you should initialize Wiliot with ApplicationContext."
    )
    WiliotNetworkMeta.initialize()
}