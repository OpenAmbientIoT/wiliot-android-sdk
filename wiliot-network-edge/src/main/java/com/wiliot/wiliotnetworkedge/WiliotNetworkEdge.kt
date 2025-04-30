package com.wiliot.wiliotnetworkedge

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.addToTokenInjectionRegistry
import com.wiliot.wiliotcore.contracts.EdgeNetworkManagerContract
import com.wiliot.wiliotcore.contracts.WiliotNetworkEdgeModule
import com.wiliot.wiliotcore.embedded.auth.PrimaryTokenInjectionConsumer
import com.wiliot.wiliotcore.registerModule
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotnetworkedge.config.Configuration
import com.wiliot.wiliotnetworkedge.di.edgeNetworkManager

object WiliotNetworkEdge : PrimaryTokenInjectionConsumer, WiliotNetworkEdgeModule {

    private val logTag = logTag()

    var configuration: Configuration = Configuration()

    override fun injectToken(token: String?) {
        Reporter.log("injected token: ${token?.take(5)}***...=", logTag)
        configuration = configuration.copy(authToken = token)
    }

    override fun networkManager(): EdgeNetworkManagerContract = edgeNetworkManager()

    internal fun initialize() {
        Wiliot.addToTokenInjectionRegistry(this)
        Wiliot.registerModule(this)
    }

}

@Suppress("UnusedReceiverParameter")
fun Wiliot.WiliotInitializationScope.initEdgeNetwork() {
    if (Wiliot.isInitialized.not()) throw RuntimeException(
        "Unable to execute `initEdgeNetwork`. First you should initialize Wiliot with ApplicationContext."
    )
    WiliotNetworkEdge.initialize()
}