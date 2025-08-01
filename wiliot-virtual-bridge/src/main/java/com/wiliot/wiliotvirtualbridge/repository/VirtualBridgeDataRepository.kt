package com.wiliot.wiliotvirtualbridge.repository

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.health.WiliotHealthMonitor
import com.wiliot.wiliotcore.model.BridgeHbPacketAbstract
import com.wiliot.wiliotcore.model.DataPacket
import com.wiliot.wiliotcore.model.DataPacketType
import com.wiliot.wiliotcore.model.Packet
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.bitMask
import com.wiliot.wiliotcore.utils.every
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotvirtualbridge.BuildConfig
import com.wiliot.wiliotvirtualbridge.config.VConfig
import com.wiliot.wiliotvirtualbridge.config.model.DatapathConfigPacket
import com.wiliot.wiliotvirtualbridge.feature.virtualBridge
import com.wiliot.wiliotvirtualbridge.repository.VBridgeRepoMsg.Income
import com.wiliot.wiliotvirtualbridge.repository.VBridgeRepoMsg.PaceCycle
import com.wiliot.wiliotvirtualbridge.utils.DirectPixelPacketPacerTransformer.toEchoPacket
import com.wiliot.wiliotvirtualbridge.utils.HeartBeatPacketGenerator
import com.wiliot.wiliotvirtualbridge.utils.InterfacePacketGenerator
import com.wiliot.wiliotvirtualbridge.utils.MelModulePacketGenerator
import com.wiliot.wiliotvirtualbridge.utils.generateMacFromString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.max

sealed class VBridgeRepoMsg {

    class Income(
        val packet: DataPacket
    ): VBridgeRepoMsg() {
        init {
            if (packet.dataPacketType() != DataPacketType.DIRECT) throw IllegalStateException(
                "Packet is not DIRECT. Only DIRECT packets could be processed by Virtual Bridge."
            )
        }
    }

    data object Heartbeat : VBridgeRepoMsg()

    data object Interface : VBridgeRepoMsg()

    data object Configuration : VBridgeRepoMsg()

    data object PaceCycle : VBridgeRepoMsg()

}

private const val MAX_PROCESSING_ITERATIONS_DIFF_MS = 1000

internal data class PixelPacketGroup(
    val lastPacket: Packet?,

    /**
     * The time when Group was paced (sent to Upstream) last time
     */
    val lastPaceTime: Long,
    val counter: Int
)

private fun PixelPacketGroup.reset() = this.copy(
    lastPacket = null,
    lastPaceTime = System.currentTimeMillis(),
    counter = 0
)

@OptIn(ObsoleteCoroutinesApi::class)
fun CoroutineScope.vBridgeRepoActor() = actor<VBridgeRepoMsg> {
    val logTag = "VBridgeActor"
    Reporter.log("vBridgeRepoActor created", logTag)

    var lastProcessingTimestamp: Long = 0

    val packetsBuffer = hashMapOf<String, PixelPacketGroup>()

    var lastHbTimestamp = 0L
    var sequenceId: Int = 0
    var receivedTagsCounter: Int = 0 // since last HB
    fun uniqueTagsCounter(): Int = packetsBuffer.filter {
        it.value.lastPacket != null && (it.value.lastPacket?.timestamp ?: 0) > lastHbTimestamp
    }.size // since last HB
    var sentPacketsCounter: Int = 0 // since last HB

    fun incSequenceId(i: Int = 1) {
        val tmp = sequenceId + i
        sequenceId = tmp % 255
    }

    fun performPacing() {
        if (packetsBuffer.isEmpty()) return

        val referredTime = System.currentTimeMillis() - VConfig.config.pacingRate
        val referredDeadTime = System.currentTimeMillis() - max(VConfig.config.pacingRate, 30_000) // for HB case
        val pacingPredicate: (Map.Entry<String, PixelPacketGroup>) -> Boolean = { entry ->
            entry.value.lastPaceTime <= referredTime && entry.value.lastPacket != null
        }
        val deadPredicate: (Map.Entry<String, PixelPacketGroup>) -> Boolean = { entry ->
            entry.value.lastPaceTime <= referredDeadTime && entry.value.lastPacket == null
        }

        packetsBuffer.filter(pacingPredicate).let { filteredEntries ->
            // map and send relevant items
            filteredEntries.map {
                it.value.toEchoPacket()
            }.let {
                VirtualBridgeDataRepository.upstreamPackets(it)
                sentPacketsCounter += it.size
                incSequenceId(it.size)
                WiliotHealthMonitor.updateVirtualBridgePktOut(it.size)
            }

            // reset entries
            filteredEntries.keys.forEach { k ->
                val pkt = packetsBuffer[k]!!
                packetsBuffer[k] = pkt.reset()
            }
        }

        // remove dead entries
        packetsBuffer.filter(deadPredicate).keys.forEach(packetsBuffer::remove)

        WiliotHealthMonitor.updateVirtualBridgeUniquePxMAC(packetsBuffer.size)

        if (BuildConfig.DEBUG) Reporter.log(
            "PACER. Map size after pacing: ${packetsBuffer.size}; " +
                    "Pacer Interval: ${VConfig.config.pacingRate} ms; " +
                    "Alive entries: ${packetsBuffer.filter { it.value.lastPacket != null }.size}",
            logTag
        )
    }

    fun performIncome(msg: Income) {
        val macKey = msg.packet.deviceMac
        if (packetsBuffer.containsKey(macKey)) {
            val ctr = packetsBuffer[macKey]!!.counter + 1
            packetsBuffer[macKey]!!.copy(
                lastPacket = msg.packet,
                counter = ctr
            ).let { newRecord ->
                packetsBuffer[macKey] = newRecord
            }
        } else {
            PixelPacketGroup(
                lastPacket = msg.packet,
                lastPaceTime = msg.packet.timestamp,
                counter = 0
            ).let {
                // 0 sending (without pacing)
                VirtualBridgeDataRepository.upstreamPackets(listOf(it.toEchoPacket()))
                WiliotHealthMonitor.updateVirtualBridgePktOut(1)
                packetsBuffer[macKey] = it.copy(lastPacket = null) // to avoid sending it again
            }
        }
        WiliotHealthMonitor.updateVirtualBridgePktIn(1)
        WiliotHealthMonitor.updateVirtualBridgeUniquePxMAC(packetsBuffer.size)
        receivedTagsCounter++
    }

    fun sendEdgePacket(packet: Packet) {
        if (packet !is BridgeHbPacketAbstract) {
            sentPacketsCounter++
        }
        incSequenceId()
        VirtualBridgeDataRepository.upstreamPackets(listOf(packet))
    }

    fun performHeartbeat() {
        HeartBeatPacketGenerator.generateHeartbeatPacket(
            sequenceId = sequenceId,
            receivedPackets = receivedTagsCounter,
            sentPackets = sentPacketsCounter,
            uniqueTagsMacAddresses = uniqueTagsCounter()
        ).let {
            sendEdgePacket(it)
        }
        lastHbTimestamp = System.currentTimeMillis()
        receivedTagsCounter = 0
        sentPacketsCounter = 0
    }

    fun performInterface() {
        InterfacePacketGenerator.generateInterfacePacket(
            sequenceId = sequenceId,
            configHash = VConfig.hash()
        ).let {
            sendEdgePacket(it)
        }
    }

    fun performConfiguration() {
        MelModulePacketGenerator.generateMelPacket(
            sequenceId = sequenceId,
            config = VConfig.config
        ).let {
            sendEdgePacket(it)
        }
    }

    for (msg in channel) {

        if (BuildConfig.DEBUG) {
            Reporter.log("VirtualBridge message <${msg::class.java.simpleName}>", logTag)
        }

        when (msg) {
            is Income -> {
                // here direct Pixel packets should be added to packets buffer
                performIncome(msg)

                // extra processing check
                if (System.currentTimeMillis() - lastProcessingTimestamp >= MAX_PROCESSING_ITERATIONS_DIFF_MS) {
                    lastProcessingTimestamp = System.currentTimeMillis()
                    performPacing()
                }
            }

            is VBridgeRepoMsg.Heartbeat,
            is VBridgeRepoMsg.Interface,
            is VBridgeRepoMsg.Configuration -> {

                when(msg) {
                    is VBridgeRepoMsg.Heartbeat -> performHeartbeat()
                    is VBridgeRepoMsg.Interface -> performInterface()
                    is VBridgeRepoMsg.Configuration -> performConfiguration()
                    else -> {
                        // Nothing
                    }
                }

                // extra processing check
                if (System.currentTimeMillis() - lastProcessingTimestamp >= MAX_PROCESSING_ITERATIONS_DIFF_MS) {
                    lastProcessingTimestamp = System.currentTimeMillis()
                    performPacing()
                }
            }

            is PaceCycle -> {
                // here packets should be paced
                lastProcessingTimestamp = System.currentTimeMillis()
                performPacing()
            }
        }
    }
}

internal object VirtualBridgeDataRepository {

    private val logTag = logTag()

    private fun buildNewScope() = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var repoScope = buildNewScope()
    private var repoActor: SendChannel<VBridgeRepoMsg> = repoScope.vBridgeRepoActor()

    private var pacerScope = buildNewScope()
    private var pacerJob: Job? = null

    private var edgeScope = buildNewScope()
    private var hbJob: Job? = null
    private var ifJob: Job? = null

    private fun initRepoActor() {
        try {
            repoScope.ensureActive()
            Reporter.log("initRepoActor", logTag)
        } catch (e: CancellationException) {
            repoScope = buildNewScope().apply {
                repoActor = vBridgeRepoActor().apply {
                    invokeOnClose {
                        Reporter.log("Virtual bridge repo closed with: $it", logTag)
                    }
                }
            }
        }
    }

    private fun initPacer() {
        try {
            pacerScope.ensureActive()
            if (pacerJob?.isActive != true) {
                pacerJob = null
                pacerJob = pacerScope.every(1_000, 1_000) {
                    repoActor.send(PaceCycle)
                    if (BuildConfig.DEBUG) Reporter.log("Pacer -> send Pace Cycle msg", logTag)
                }
            }
            Reporter.log("initPacer", logTag)
        } catch (e: CancellationException) {
            pacerScope = buildNewScope().apply {
                pacerJob = every(1_000, 1_000) {
                    repoActor.send(PaceCycle)
                    if (BuildConfig.DEBUG) Reporter.log("Pacer -> send Pace Cycle msg", logTag)
                }
            }
        }
    }

    private fun initEdgeRoutine() {

        fun CoroutineScope.initHbJob() {
            if (hbJob?.isActive != true) {
                hbJob = every(30_000, 1_000) {
                    repoActor.send(VBridgeRepoMsg.Heartbeat)
                }
            }
        }

        fun CoroutineScope.initIfJob() {
            if (ifJob?.isActive != true) {
                ifJob = every(60_000, 1_000) {
                    repoActor.send(VBridgeRepoMsg.Interface)
                }
            }
        }

        try {
            edgeScope.ensureActive()
            edgeScope.initHbJob()
            edgeScope.initIfJob()
        } catch (e: CancellationException) {
            edgeScope = buildNewScope().apply {
                initHbJob()
                initIfJob()
            }
        }
    }

    private fun applyNewConfiguration(configPacket: String) {
        Reporter.log("applyNewConfiguration($configPacket)", logTag)
        DatapathConfigPacket.parsePayload(configPacket)?.let {
            if (it.pacerInterval < 1 || it.pacerInterval > 65000) {
                Reporter.exception(
                    message = "Invalid pacer interval was received from Cloud command: ${it.pacerInterval}",
                    exception = IllegalArgumentException("Invalid pacer interval was received from Cloud command: ${it.pacerInterval}"),
                    where = logTag
                )
                return@let
            }
            val cfg = VConfig.config
            VConfig.config = cfg.copy(pacingRate = it.pacerInterval.toLong() * 1000)
            repoScope.launch {
                delay(500)
                repoActor.send(VBridgeRepoMsg.Configuration)
            }
        }
    }

    private fun processIncomingMelPacket(packet: String) {
        Reporter.log("processIncomingMelPacket($packet)", logTag)
        if (packet.isActionGetModule) {
            Reporter.log("processIncomingMelPacket($packet) -> ActionGetModule", logTag)
            if (packet.containsGetInterface) {
                Reporter.log("processIncomingMelPacket($packet) -> Get_Interface", logTag)
                repoScope.launch {
                    repoActor.send(VBridgeRepoMsg.Interface)
                }
            }
            if (packet.containsGetDatapath) {
                Reporter.log("processIncomingMelPacket($packet) -> Get_Datapath", logTag)
                repoScope.launch {
                    repoActor.send(VBridgeRepoMsg.Configuration)
                }
            }
            return
        }
        if (packet.isConfigurationPacket) {
            Reporter.log("processIncomingMelPacket($packet) -> Configuration", logTag)
            applyNewConfiguration(packet)
            return
        }
    }

    internal fun initActorAsync() {
        initRepoActor()
        initPacer()
        initEdgeRoutine()
    }

    internal fun suspendActor() {
        repoScope.cancel()
        pacerJob?.cancel()
        pacerJob = null
        pacerScope.cancel()
        ifJob?.cancel()
        ifJob = null
        hbJob?.cancel()
        hbJob = null
        edgeScope.cancel()
    }

    internal fun upstreamPackets(packets: List<Packet>) {
        Wiliot.virtualBridge().upstreamChannel?.sendPackets(packets)
    }

    internal fun addDataPacket(packet: DataPacket) {
        repoScope.launch {
            repoActor.send(Income(packet))
        }
    }

    internal fun addMelPacket(packetRaw: String) {
        processIncomingMelPacket(packetRaw)
    }

    //==============================================================================================
    // *** Utils ***
    //==============================================================================================

    // region [Utils]

    private fun String.normalizeActionGetModule(): String {
        return this.uppercase().let {
            if (it.startsWith("1E16C6FC")) {
                it.replaceFirst("1E16C6FC", "")
            } else {
                it
            }
        }
    }

    private val String.isActionGetModule: Boolean
        get() {
            val origin = this.normalizeActionGetModule()
            val predicate = "${generateMacFromString(Wiliot.getFullGWId()).replace(":", "")}03".uppercase()
            return origin.contains(predicate)
        }

    private val String.containsGetInterface: Boolean
        get() {
            val origin = this.uppercase()
            if (origin.isActionGetModule.not()) return false
            return (origin.substring(26..27).toUInt(16) and bitMask(10000000) shr 7) == 1u
        }

    private val String.containsGetDatapath: Boolean
        get() {
            val origin = this.uppercase()
            if (origin.isActionGetModule.not()) return false
            return (origin.substring(32..33).toUInt(16) and bitMask(1000000) shr 6) == 1u
        }

    private val String.isConfigurationPacket: Boolean
        get() {
            val origin = this.uppercase()
            return origin.contains(generateMacFromString(Wiliot.getFullGWId()).replace(":", "").uppercase())
                    && origin.contains("C6FC0000ED25")
        }

    // endregion

}