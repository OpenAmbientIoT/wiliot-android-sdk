package com.wiliot.wiliotcore.utils.helper

import com.wiliot.wiliotcore.FlowVersion
import com.wiliot.wiliotcore.ServiceState
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.WiliotCounter
import com.wiliot.wiliotcore.config.DynamicBrokerConfig
import com.wiliot.wiliotcore.config.Configuration
import com.wiliot.wiliotcore.contracts.CommandsQueueManagerContract
import com.wiliot.wiliotcore.contracts.EdgeNetworkManagerContract
import com.wiliot.wiliotcore.contracts.MessageQueueManagerContract
import com.wiliot.wiliotcore.contracts.MetaNetworkManagerContract
import com.wiliot.wiliotcore.contracts.WiliotDownstreamModule
import com.wiliot.wiliotcore.contracts.WiliotEdgeModule
import com.wiliot.wiliotcore.contracts.WiliotNetworkEdgeModule
import com.wiliot.wiliotcore.contracts.WiliotNetworkMetaModule
import com.wiliot.wiliotcore.contracts.WiliotQueueModule
import com.wiliot.wiliotcore.contracts.WiliotResolveDataModule
import com.wiliot.wiliotcore.contracts.WiliotResolveEdgeModule
import com.wiliot.wiliotcore.contracts.WiliotUpstreamModule
import com.wiliot.wiliotcore.contracts.WiliotVirtualBridgeModule
import com.wiliot.wiliotcore.contracts.wiring.CommandsQueueManagerProvider
import com.wiliot.wiliotcore.contracts.wiring.EdgeNetworkManagerProvider
import com.wiliot.wiliotcore.contracts.wiring.MessageQueueManagerProvider
import com.wiliot.wiliotcore.contracts.wiring.MetaNetworkManagerProvider
import com.wiliot.wiliotcore.health.WiliotHealthMonitor
import com.wiliot.wiliotcore.env.EnvironmentWiliot
import com.wiliot.wiliotcore.model.AdditionalGatewayConfig
import com.wiliot.wiliotcore.model.DownlinkAction
import com.wiliot.wiliotcore.model.DownlinkConfigurationMessage
import com.wiliot.wiliotcore.model.DownlinkCustomBrokerMessage
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

object WiliotAppConfigurationSource {

    interface WiliotSdkPreferenceSource {
        fun environment(): EnvironmentWiliot
        fun ownerId(): String?
        fun flowVersion(): FlowVersion
        fun isUpstreamEnabled(): Boolean
        fun resolveEnabled(): Boolean
        fun isDownstreamEnabled(): Boolean
        fun pixelsTrafficEnabled(): Boolean
        fun edgeTrafficEnabled(): Boolean
        fun excludeMqttTraffic(): Boolean
        fun isServicePhoenixEnabled(): Boolean
        fun isRunningInCloudManagedMode(): Boolean
        fun dataOutputTrafficFilter(): AdditionalGatewayConfig.DataOutputTrafficFilter
        fun btPacketsCounterEnabled(): Boolean
        fun isBleLogsEnabled(): Boolean
    }

    abstract class DefaultSdkPreferenceSource : WiliotSdkPreferenceSource {
        override fun environment(): EnvironmentWiliot = Configuration.DEFAULT_ENVIRONMENT
        override fun flowVersion(): FlowVersion = FlowVersion.m2m()
        override fun isUpstreamEnabled(): Boolean = true
        override fun isDownstreamEnabled(): Boolean = true
        override fun pixelsTrafficEnabled(): Boolean = true
        override fun edgeTrafficEnabled(): Boolean = true
        override fun resolveEnabled(): Boolean = false
        override fun excludeMqttTraffic(): Boolean = false
        override fun isServicePhoenixEnabled(): Boolean = false
        override fun isRunningInCloudManagedMode(): Boolean = true
        override fun dataOutputTrafficFilter(): AdditionalGatewayConfig.DataOutputTrafficFilter = AdditionalGatewayConfig.DataOutputTrafficFilter.BRIDGES_AND_PIXELS
        override fun btPacketsCounterEnabled(): Boolean = false
        override fun isBleLogsEnabled(): Boolean = false
    }

    private var mConfigSource: WiliotSdkPreferenceSource = object : DefaultSdkPreferenceSource() {
        override fun ownerId(): String? = null
    }

    val configSource: WiliotSdkPreferenceSource
        get() = mConfigSource

    fun initialize(source: WiliotSdkPreferenceSource) {
        mConfigSource = source
    }

}

fun Wiliot.refreshConfiguration() {
    configuration =
        configuration.copy(
            environment = WiliotAppConfigurationSource.configSource.environment(),
            ownerId = WiliotAppConfigurationSource.configSource.ownerId(),
            flowVersion = WiliotAppConfigurationSource.configSource.flowVersion(),
            enableEdgeTraffic = WiliotAppConfigurationSource.configSource.edgeTrafficEnabled(),
            enableDataTraffic = WiliotAppConfigurationSource.configSource.pixelsTrafficEnabled(),
            excludeMqttTraffic = WiliotAppConfigurationSource.configSource.excludeMqttTraffic(),
            phoenix = WiliotAppConfigurationSource.configSource.isServicePhoenixEnabled(),
            cloudManaged = WiliotAppConfigurationSource.configSource.isRunningInCloudManagedMode(),
            dataOutputTrafficFilter = WiliotAppConfigurationSource.configSource.dataOutputTrafficFilter(),
            btPacketsCounterEnabled = WiliotAppConfigurationSource.configSource.btPacketsCounterEnabled()
        )
    QueueModule?.setBleLogsEnabled(WiliotAppConfigurationSource.configSource.isBleLogsEnabled())
}

private class StateObserver {

    private val logTag = logTag()

    private val observerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var observerJob: Job? = null

    private val safeUpstreamState: StateFlow<UpstreamState>
        get() = UpstreamModule?.upstreamState ?: MutableStateFlow(UpstreamState.STOPPED)

    private val safeDownstreamState: StateFlow<DownstreamState>
        get() = DownstreamModule?.downstreamState ?: MutableStateFlow(DownstreamState.STOPPED)

    fun observeServicesState() {
        Reporter.log("observeServicesState", logTag)
        observerJob?.runCatching { cancel()}
        observerJob = observerScope.launch {
            safeUpstreamState.combine(safeDownstreamState) { upStream, downStream ->
                Pair(upStream, downStream)
            }.collect { state ->
                Reporter.log("upstreamState.collect -> $state", logTag)
                processServicesState(state)
            }
        }
    }

    fun stop() {
        Reporter.log("stop", logTag)
        observerJob?.runCatching { cancel()}
        observerJob = null
    }

    private fun processServicesState(state: Pair<UpstreamState, DownstreamState>) {
        if (state.first == UpstreamState.STOPPED && state.second == DownstreamState.STOPPED) {
            Wiliot.notifyServicesStopping()
        }
    }
}

private var stateObserver: StateObserver? = null

private object QueueManagerProvider : MessageQueueManagerProvider, CommandsQueueManagerProvider {
    override fun provideMessageQueueManager(): MessageQueueManagerContract {
        if (QueueModule == null) throw RuntimeException(
            "Unable to provide MessageQueueManagerContract. Queue module is not registered"
        )
        return QueueModule!!.msgQueueManager()
    }

    override fun provideCommandsQueueManager(): CommandsQueueManagerContract {
        if (QueueModule == null) throw RuntimeException(
            "Unable to provide CommandsQueueManagerContract. Queue module is not registered"
        )
        return QueueModule!!.cmdQueueManager()
    }
}

private object MetaNetworkProvider : MetaNetworkManagerProvider {
    override fun provideMetaNetworkManager(): MetaNetworkManagerContract {
        if (MetaNetworkModule == null) {
            throw RuntimeException(
                "Unable to provide MetaNetworkManagerContract. Meta Network module is not registered"
            )
        }
        return MetaNetworkModule!!.networkManager()
    }
}

private object EdgeNetworkProvider : EdgeNetworkManagerProvider {
    override fun provideEdgeNetworkManager(): EdgeNetworkManagerContract {
        if (EdgeNetworkModule == null) {
            throw RuntimeException(
                "Unable to provide EdgeNetworkManagerContract. Edge Network module is not registered"
            )
        }
        return EdgeNetworkModule!!.networkManager()
    }
}

fun Wiliot.prepareExternalDataResolver() {
    ResolveDataModule?.setupNetworkManagerProvider(MetaNetworkProvider)
}

fun Wiliot.prepareExternalEdgeResolver() {
    ResolveEdgeModule?.setupNetworkManagerProvider(EdgeNetworkProvider)
}

@Throws(IllegalStateException::class)
fun Wiliot.start() {
    if (!WiliotAppConfigurationSource.configSource.isUpstreamEnabled() && !WiliotAppConfigurationSource.configSource.isDownstreamEnabled()) {
        throw IllegalStateException(
            "Can not start Wiliot. Upstream and Downstream configured to be disabled."
        )
    }

    notifyServicesStarting(immediate = true)
    refreshConfiguration()
    if (WiliotAppConfigurationSource.configSource.isUpstreamEnabled()) {
        UpstreamModule?.setExtraEdgeProcessor(
            if (WiliotAppConfigurationSource.configSource.resolveEnabled())
                ResolveEdgeModule?.processor
            else
                null
        )
        ResolveDataModule?.setupNetworkManagerProvider(MetaNetworkProvider)
        ResolveEdgeModule?.setupNetworkManagerProvider(EdgeNetworkProvider)
        UpstreamModule?.setExtraDataProcessor(
            if (WiliotAppConfigurationSource.configSource.resolveEnabled())
                ResolveDataModule?.processor
            else
                null
        )
        UpstreamModule?.setupQueueProvider(QueueManagerProvider)
        UpstreamModule?.let {
            VirtualBridgeModule?.setUpstreamVirtualBridgeChannel(it.upstreamVirtualBridgeChannel)
        }
        VirtualBridgeModule?.let {
            UpstreamModule?.setVirtualBridge(it.virtualBridgeContract())
        }
        VirtualBridgeModule?.start()
        UpstreamModule?.start()
    }
    if (WiliotAppConfigurationSource.configSource.isDownstreamEnabled()) {
        DownstreamModule?.setEdgeProcessor(EdgeModule?.processor)
        DownstreamModule?.setUpstreamFeedbackChannel(
            if (WiliotAppConfigurationSource.configSource.isUpstreamEnabled()) {
                UpstreamModule?.feedbackChannel
            } else null
        )
        DownstreamModule?.setupQueueProvider(QueueManagerProvider)
        VirtualBridgeModule?.let {
            DownstreamModule?.setVirtualBridge(it.virtualBridgeContract())
        }
        DownstreamModule?.start()
    }

    WiliotHealthMonitor.updateLaunchedState(launched = true)

    if (stateObserver == null) stateObserver = StateObserver()
    stateObserver?.observeServicesState()

    Reporter.log("Attempt to start Wiliot with config: $configuration", "Wiliot.start()")
}

fun Wiliot.stop() {
    VirtualBridgeModule?.stop()
    UpstreamModule?.stop()
    DownstreamModule?.stop()
    WiliotCounter.reset()

    WiliotHealthMonitor.updateLaunchedState(launched = false)

    stateObserver?.stop()
    synchronized(extraGatewayInfoSynchronized) {
        extraGatewayInfoSynchronized.clear()
    }
    Reporter.log("Attempt to stop Wiliot", "Wiliot.stop()")
}

internal typealias UpstreamState = ServiceState
internal typealias DownstreamState = ServiceState

private val QueueModule: WiliotQueueModule?
    get() = Wiliot.modules.firstOrNull { it is WiliotQueueModule } as? WiliotQueueModule

private val MetaNetworkModule: WiliotNetworkMetaModule?
    get() = Wiliot.modules.firstOrNull { it is WiliotNetworkMetaModule } as? WiliotNetworkMetaModule

private val EdgeNetworkModule: WiliotNetworkEdgeModule?
    get() = Wiliot.modules.firstOrNull { it is WiliotNetworkEdgeModule } as? WiliotNetworkEdgeModule

private val UpstreamModule: WiliotUpstreamModule?
    get() = Wiliot.modules.firstOrNull { it is WiliotUpstreamModule } as? WiliotUpstreamModule

private val DownstreamModule: WiliotDownstreamModule?
    get() = Wiliot.modules.firstOrNull { it is WiliotDownstreamModule } as? WiliotDownstreamModule

private val ResolveEdgeModule: WiliotResolveEdgeModule?
    get() = Wiliot.modules.firstOrNull { it is WiliotResolveEdgeModule } as? WiliotResolveEdgeModule

private val ResolveDataModule: WiliotResolveDataModule?
    get() = Wiliot.modules.firstOrNull { it is WiliotResolveDataModule } as? WiliotResolveDataModule

private val EdgeModule: WiliotEdgeModule?
    get() = Wiliot.modules.firstOrNull { it is WiliotEdgeModule } as? WiliotEdgeModule

private val VirtualBridgeModule: WiliotVirtualBridgeModule?
    get() = Wiliot.modules.firstOrNull { it is WiliotVirtualBridgeModule } as? WiliotVirtualBridgeModule

//==============================================================================================
// *** Cloud Management ***
//==============================================================================================

// region [Cloud Management]

private val starterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

fun Wiliot.handleBrokerConfigurationChangeRequest(newConfiguration: DownlinkCustomBrokerMessage) {
    dynamicBrokerConfig = if (newConfiguration.customBroker) {
        DynamicBrokerConfig(newConfiguration)
    } else {
        DynamicBrokerConfig(null)
    }
    restartWiliot()
}

fun Wiliot.handleDownlinkActionGatewayMessage(actionMessage: DownlinkAction) {

    Reporter.log("handleDownlinkActionGatewayMessage: gwAction: ${actionMessage.name}", logTag())

    when (actionMessage) {
        DownlinkAction.GET_GW_INFO -> {
            QueueModule?.sendCapabilitiesAndHeartbeat()
        }
        else -> {
            Reporter.log("handleDownlinkActionGatewayMessage: gwAction: $actionMessage is not supported", logTag())
        }
    }
}

fun Wiliot.handleSdkConfigurationChangeRequest(newConfiguration: DownlinkConfigurationMessage) {

    val prevEnvironment = WiliotAppConfigurationSource.configSource.environment()
    val prevOwnerId = WiliotAppConfigurationSource.configSource.ownerId()
    val prevFlowVersion = WiliotAppConfigurationSource.configSource.flowVersion()
    val prevUpstreamEnabled = WiliotAppConfigurationSource.configSource.isUpstreamEnabled()
    val prevPixelsTrafficEnabled = WiliotAppConfigurationSource.configSource.pixelsTrafficEnabled()
    val prevEdgeTrafficEnabled = WiliotAppConfigurationSource.configSource.edgeTrafficEnabled()
    val prevResolveEnabled = WiliotAppConfigurationSource.configSource.resolveEnabled()
    val prevServicePhoenixEnabled = WiliotAppConfigurationSource.configSource.isServicePhoenixEnabled()
    val prevRunningInCloudManagedMode = WiliotAppConfigurationSource.configSource.isRunningInCloudManagedMode()
    val prevDataOutputTrafficFilter = WiliotAppConfigurationSource.configSource.dataOutputTrafficFilter()
    val prevBtPacketsCounterEnabled = WiliotAppConfigurationSource.configSource.btPacketsCounterEnabled()
    val prevBleLogsEnabled = WiliotAppConfigurationSource.configSource.isBleLogsEnabled()

    if (newConfiguration.hasChanges(WiliotAppConfigurationSource.configSource).not()) {
        Reporter.log("There is no changes in received Configuration message", logTag())
        QueueModule?.sendCapabilitiesAndHeartbeat()
        return
    }

    val newSource = object : WiliotAppConfigurationSource.WiliotSdkPreferenceSource {
        override fun environment(): EnvironmentWiliot = prevEnvironment
        override fun ownerId(): String? = prevOwnerId
        override fun flowVersion(): FlowVersion = prevFlowVersion
        override fun isUpstreamEnabled(): Boolean = newConfiguration.isUpstreamEnabled(prevUpstreamEnabled)
        override fun isDownstreamEnabled(): Boolean = true
        override fun pixelsTrafficEnabled(): Boolean = newConfiguration.isPixelsTrafficEnabled(prevPixelsTrafficEnabled)
        override fun edgeTrafficEnabled(): Boolean = newConfiguration.isEdgeTrafficEnabled(prevEdgeTrafficEnabled)
        override fun resolveEnabled(): Boolean = prevResolveEnabled
        override fun excludeMqttTraffic(): Boolean = false
        override fun isServicePhoenixEnabled(): Boolean = prevServicePhoenixEnabled
        override fun isRunningInCloudManagedMode(): Boolean = prevRunningInCloudManagedMode
        override fun dataOutputTrafficFilter(): AdditionalGatewayConfig.DataOutputTrafficFilter = newConfiguration.dataOutputTrafficFilter(prevDataOutputTrafficFilter)
        override fun btPacketsCounterEnabled(): Boolean = prevBtPacketsCounterEnabled
        override fun isBleLogsEnabled(): Boolean = newConfiguration.isBleLogsEnabled(prevBleLogsEnabled)
    }

    WiliotAppConfigurationSource.initialize(newSource)
    delegate.onNewSoftwareGatewayConfigurationApplied()
    restartWiliot()

}

@Suppress("RedundantIf")
private fun DownlinkConfigurationMessage.hasChanges(configSource: WiliotAppConfigurationSource.WiliotSdkPreferenceSource): Boolean {
    if (isUpstreamEnabled(configSource.isUpstreamEnabled()) != configSource.isUpstreamEnabled()) return true
    if (isPixelsTrafficEnabled(configSource.pixelsTrafficEnabled()) != configSource.pixelsTrafficEnabled()) return true
    if (isEdgeTrafficEnabled(configSource.edgeTrafficEnabled()) != configSource.edgeTrafficEnabled()) return true
    if (isBleLogsEnabled(configSource.isBleLogsEnabled()) != configSource.isBleLogsEnabled()) return true
    if (dataOutputTrafficFilter(configSource.dataOutputTrafficFilter()) != configSource.dataOutputTrafficFilter()) return true
    return false
}

private fun restartWiliot() {
    starterScope.launch {
        Wiliot.stop()
        delay(1000)
        Wiliot.start()
    }
}

// endregion