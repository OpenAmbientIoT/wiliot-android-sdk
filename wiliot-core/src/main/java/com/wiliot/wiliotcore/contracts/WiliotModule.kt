package com.wiliot.wiliotcore.contracts

import com.wiliot.wiliotcore.ServiceState
import com.wiliot.wiliotcore.contracts.wiring.CommandsQueueManagerProvider
import com.wiliot.wiliotcore.contracts.wiring.EdgeNetworkManagerProvider
import com.wiliot.wiliotcore.contracts.wiring.MessageQueueManagerProvider
import com.wiliot.wiliotcore.contracts.wiring.MetaNetworkManagerProvider
import kotlinx.coroutines.flow.StateFlow

interface WiliotModule

interface WiliotDownstreamModule : WiliotModule {
    val downstreamState: StateFlow<ServiceState>
    fun setEdgeProcessor(processor: EdgeJobProcessorContract?)
    fun setUpstreamFeedbackChannel(channel: UpstreamFeedbackChannel?)
    fun setupQueueProvider(provider: CommandsQueueManagerProvider)
    fun setVirtualBridge(vBridge: VirtualBridgeContract)
    fun start()
    fun stop()
}

interface WiliotEdgeModule : WiliotModule {
    val processor: EdgeJobProcessorContract?
}

interface WiliotNetworkEdgeModule : WiliotModule {
    fun networkManager(): EdgeNetworkManagerContract
}

interface WiliotNetworkMetaModule : WiliotModule {
    fun networkManager(): MetaNetworkManagerContract
}

interface WiliotResolveDataModule : WiliotModule {
    val processor: UpstreamExtraDataProcessingContract?
    fun setupNetworkManagerProvider(provider: MetaNetworkManagerProvider)
}

interface WiliotResolveEdgeModule : WiliotModule {
    val processor: UpstreamExtraEdgeProcessingContract?
    fun setupNetworkManagerProvider(provider: EdgeNetworkManagerProvider)
}

interface WiliotUpstreamModule : WiliotModule {
    val upstreamState: StateFlow<ServiceState>
    val feedbackChannel: UpstreamFeedbackChannel
    val upstreamVirtualBridgeChannel: UpstreamVirtualBridgeChannel
    fun setExtraEdgeProcessor(processor: UpstreamExtraEdgeProcessingContract?)
    fun setExtraDataProcessor(processor: UpstreamExtraDataProcessingContract?)
    fun setPrecisePositionSource(source: UpstreamPrecisePositioningContract?)
    fun setVirtualBridge(vBridge: VirtualBridgeContract)
    fun setupQueueProvider(provider: MessageQueueManagerProvider)
    fun start()
    fun stop()
}

interface WiliotVirtualBridgeModule : WiliotModule {
    fun start()
    fun stop()
    fun setUpstreamVirtualBridgeChannel(channel: UpstreamVirtualBridgeChannel?)
    fun virtualBridgeContract(): VirtualBridgeContract
    val bridgeId: String
}

interface WiliotQueueModule : WiliotModule {
    fun msgQueueManager(): MessageQueueManagerContract
    fun cmdQueueManager(): CommandsQueueManagerContract
    fun setBleLogsEnabled(enabled: Boolean)
}

interface WiliotPositioningModule : WiliotModule {
    val positioning: UpstreamPrecisePositioningContract?
    fun start()
    fun stop()
}