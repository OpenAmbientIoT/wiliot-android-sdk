package com.wiliot.wiliotvirtualbridge.feature

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.contracts.UpstreamVirtualBridgeChannel
import com.wiliot.wiliotcore.contracts.VirtualBridgeContract
import com.wiliot.wiliotcore.contracts.WiliotVirtualBridgeModule
import com.wiliot.wiliotcore.getWithApplicationContext
import com.wiliot.wiliotcore.model.DataPacket
import com.wiliot.wiliotcore.registerModule
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotvirtualbridge.config.VConfig
import com.wiliot.wiliotvirtualbridge.repository.VirtualBridgeDataRepository
import com.wiliot.wiliotvirtualbridge.utils.generateMacFromString

class VirtualBridge private constructor() : WiliotVirtualBridgeModule, VirtualBridgeContract {

    private val logTag = logTag()

    internal var upstreamChannel: UpstreamVirtualBridgeChannel? = null

    companion object {

        private var bInstance: VirtualBridge? = null

        internal fun getInstance(): VirtualBridge {
            if (bInstance == null) {
                bInstance = VirtualBridge()
                Wiliot.registerModule(bInstance!!)
            }
            return bInstance!!
        }

    }

    override fun start() {
        Reporter.log("start", logTag)
        getWithApplicationContext(VConfig::load)
        VirtualBridgeDataRepository.initActorAsync()
    }

    override fun stop() {
        Reporter.log("stop", logTag)
        VirtualBridgeDataRepository.suspendActor()
    }

    override fun addDirectPacket(dataPacket: DataPacket) {
        VirtualBridgeDataRepository.addDataPacket(dataPacket)
    }

    override fun addMelPacket(packetRaw: String) {
        VirtualBridgeDataRepository.addMelPacket(packetRaw)
    }

    override fun setUpstreamVirtualBridgeChannel(channel: UpstreamVirtualBridgeChannel?) {
        this.upstreamChannel = channel
    }

    override fun virtualBridgeContract(): VirtualBridgeContract {
        return this
    }

    override val bridgeId: String
        get() = generateMacFromString(Wiliot.getFullGWId())

}

/**
 * Returns instance of [VirtualBridge] singleton
 */
@Suppress("UnusedReceiverParameter")
fun Wiliot.virtualBridge(): VirtualBridge = VirtualBridge.getInstance()

@Suppress("UnusedReceiverParameter")
fun Wiliot.WiliotInitializationScope.initVirtualBridge() {
    if (Wiliot.isInitialized.not()) throw RuntimeException(
        "Unable to execute `initVirtualBridge`. First you should initialize Wiliot with ApplicationContext."
    )
    VirtualBridge.getInstance()
}