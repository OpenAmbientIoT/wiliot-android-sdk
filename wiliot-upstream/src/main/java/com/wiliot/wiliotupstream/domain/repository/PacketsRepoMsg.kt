package com.wiliot.wiliotupstream.domain.repository

import com.wiliot.wiliotcore.BuildConfig
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.config.util.TrafficRule
import com.wiliot.wiliotcore.model.BaseMetaPacket
import com.wiliot.wiliotcore.model.BridgeACKPacket
import com.wiliot.wiliotcore.model.BridgeEarlyPacket
import com.wiliot.wiliotcore.model.BridgeHbPacketAbstract
import com.wiliot.wiliotcore.model.BridgePacketAbstract
import com.wiliot.wiliotcore.model.BridgeStatus
import com.wiliot.wiliotcore.model.UnifiedEchoPacket
import com.wiliot.wiliotcore.model.ControlPacket
import com.wiliot.wiliotcore.model.DataPacket
import com.wiliot.wiliotcore.model.DataPacketType
import com.wiliot.wiliotcore.model.ExternalSensorPacket
import com.wiliot.wiliotcore.model.MelModulePacket
import com.wiliot.wiliotcore.model.MetaPacket
import com.wiliot.wiliotcore.model.Packet
import com.wiliot.wiliotcore.model.PacketAbstract
import com.wiliot.wiliotcore.model.PacketData
import com.wiliot.wiliotcore.model.toBridgeStatus
import com.wiliot.wiliotcore.model.wiliotBridgeEarlyPacket
import com.wiliot.wiliotcore.model.wiliotManufacturerData
import com.wiliot.wiliotcore.model.wiliotServiceData
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.ScanResultInternal
import com.wiliot.wiliotcore.utils.helper.WiliotAppConfigurationSource
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotupstream.domain.repository.PacketsRepoMsg.Companion.SYNC_PERIOD
import com.wiliot.wiliotupstream.domain.repository.PacketsRepoMsg.Companion.dataPacketFilterPredicate
import com.wiliot.wiliotupstream.domain.repository.PacketsRepoMsg.Companion.managementFilterPredicate
import com.wiliot.wiliotupstream.domain.repository.PacketsRepoMsg.Companion.mapJob
import com.wiliot.wiliotupstream.domain.repository.PacketsRepoMsg.Companion.mappingScope
import com.wiliot.wiliotupstream.domain.repository.PacketsRepoMsg.Companion.metaPacketFilterPredicate
import com.wiliot.wiliotupstream.domain.repository.PacketsRepoMsg.Companion.referredTime
import com.wiliot.wiliotupstream.domain.repository.PacketsRepoMsg.Companion.siFilterPredicate
import com.wiliot.wiliotupstream.feature.upstream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

sealed class PacketsRepoMsg {
    companion object {
        private const val FILTER_TIME_WINDOW_SIZE = 200L
        private const val MANAGEMENT_TIME_WINDOW_SIZE = 1000L
        const val SYNC_PERIOD = 1000L
        var referredTime = 0L
        val dataPacketFilterPredicate: (Packet) -> Boolean = { packet ->
            val windowCondition = packet.timestamp + FILTER_TIME_WINDOW_SIZE < referredTime
            val classificationCondition = packet is DataPacket
            windowCondition && classificationCondition
        }
        val siFilterPredicate: (Packet) -> Boolean = { packet ->
            val windowCondition = packet.timestamp + FILTER_TIME_WINDOW_SIZE < referredTime
            val classificationCondition = packet is UnifiedEchoPacket
            windowCondition && classificationCondition
        }
        val metaPacketFilterPredicate: (Packet) -> Boolean = { packet ->
            val windowCondition = packet.timestamp + FILTER_TIME_WINDOW_SIZE < referredTime
            val classificationCondition = packet is BaseMetaPacket && packet !is UnifiedEchoPacket
            windowCondition && classificationCondition
        }
        val managementFilterPredicate: (Packet) -> Boolean = { packet ->
            packet.timestamp + MANAGEMENT_TIME_WINDOW_SIZE < referredTime
        }
        var mapJob: Job? = null
        val mappingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
}

class JudgeResult(
    val result: ScanResultInternal,
) : PacketsRepoMsg()

data object MapBuffer : PacketsRepoMsg()

private const val MAX_MAP_ITERATIONS_DIFF_MS = 1000

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
fun CoroutineScope.packetsRepoActor() = actor<PacketsRepoMsg> {
    Reporter.log("Packets actor created", logTag())

    var lastMapTimestamp: Long = 0

    val judgeBufferBridge = HashSet<BridgePacketAbstract>()
    val judgeBufferMel = HashSet<MelModulePacket>()
    val judgeBufferBrgHb = HashSet<BridgeHbPacketAbstract>()
    val judgeBufferBridgeEarly = HashSet<BridgeEarlyPacket>()
    val judgeBufferBridgeAck = HashSet<BridgeACKPacket>()
    val judgeBufferMeta = HashSet<BaseMetaPacket>()
    val judgeBufferData = HashSet<DataPacket>()
    mapJob = mappingScope.launch {
        Reporter.log("mapJob launched", logTag())
        do {
            runCatching {
                mapJob?.ensureActive()
                delay(SYNC_PERIOD)
                this@actor.channel.send(MapBuffer)
            }
        } while (true == mapJob?.isActive)
        Reporter.log("mapJob not active anymore", logTag())
    }.apply {
        invokeOnCompletion {
            Reporter.log("mapJob completed", logTag())
        }
    }

    fun handleServiceUUIDPacket(servicePacket: PacketAbstract?) =
        servicePacket?.apply {
            // skip all GW <=> Bridge control packets
            when {
                this is DataPacket && Wiliot.configuration.enableDataTraffic -> {
                    judgeBufferData.add(this)
                }

                this is MetaPacket && Wiliot.configuration.enableDataTraffic -> {
                    judgeBufferMeta.add(this)
                }

                this is ExternalSensorPacket && Wiliot.configuration.enableDataTraffic -> {
                    judgeBufferMeta.add(this)
                }

                this is UnifiedEchoPacket && Wiliot.configuration.enableDataTraffic -> {
                    judgeBufferMeta.add(this)
                }

                this is BridgePacketAbstract && Wiliot.configuration.enableEdgeTraffic -> {
                    judgeBufferBridge.add(this)
                }

                this is BridgeHbPacketAbstract && Wiliot.configuration.enableEdgeTraffic -> {
                    judgeBufferBrgHb.add(this)
                }

                this is MelModulePacket && Wiliot.configuration.enableEdgeTraffic -> {
                    judgeBufferMel.add(this)
                }

                this is BridgeACKPacket -> {
                    judgeBufferBridgeAck.add(this)
                }

                this is ControlPacket -> {
                    // Nothing
                }

                else -> {}
            }
        }

    suspend fun performMap() {
        if (
            0 == judgeBufferData.size
            && 0 == judgeBufferMeta.size
            && 0 == judgeBufferBridge.size
            && 0 == judgeBufferBrgHb.size
            && 0 == judgeBufferMel.size
            && 0 == judgeBufferBridgeEarly.size
            && 0 == judgeBufferBridgeAck.size
        ) {
            return
        }

        referredTime = System.currentTimeMillis()
        val dataPacketsOutOfWindow: List<DataPacket> = judgeBufferData.filter(
            dataPacketFilterPredicate
        )

        val metaOutOfWindow: List<BaseMetaPacket> = if (TrafficRule.shouldUploadRetransmittedDataTraffic)
            judgeBufferMeta.filter(metaPacketFilterPredicate)
        else emptyList() // early skip if no retransmitted data traffic is allowed
        val siOutOfWindow: List<BaseMetaPacket> =
            judgeBufferMeta.filter(siFilterPredicate)
        val bridgeOutOfWindow: List<BridgePacketAbstract> =
            judgeBufferBridge.filter(managementFilterPredicate)
        val hbOutOfWindow: List<BridgeHbPacketAbstract> =
            judgeBufferBrgHb.filter(managementFilterPredicate)
        val melOutOfWindow: List<MelModulePacket> =
            judgeBufferMel.filter(managementFilterPredicate)
        val bridgeEarlyOutOfWindow: List<BridgeEarlyPacket> =
            judgeBufferBridgeEarly.filter(managementFilterPredicate)
        judgeBufferData.removeAll(dataPacketFilterPredicate)
        judgeBufferMeta.removeAll(siFilterPredicate)
        judgeBufferMeta.removeAll(metaPacketFilterPredicate)
        judgeBufferBridge.removeAll(managementFilterPredicate)
        judgeBufferBrgHb.removeAll(managementFilterPredicate)
        judgeBufferMel.removeAll(managementFilterPredicate)
        judgeBufferBridgeEarly.removeAll(managementFilterPredicate)

        //send all ACKs at once
        judgeBufferBridgeAck.apply {
            takeUnless { it.isEmpty() }?.map { "WLT_INFO: ReceivedAction=${it.value}" }
                ?.apply {
                    BeaconDataRepository.sendBridgeAckPayload(this)
                }
            clear()
        }

        (
                bridgeOutOfWindow.map { bridgePacket -> bridgePacket.toBridgeStatus() } +
                        metaOutOfWindow.filterIsInstance<MetaPacket>().map { metaPacket ->
                            metaPacket.toBridgeStatus()
                        } +
                        melOutOfWindow.map { melPacket ->
                            melPacket.toBridgeStatus()
                        } +
                        hbOutOfWindow.map { hbPacket ->
                            hbPacket.toBridgeStatus()
                        }
                )
            .fold(mutableListOf<BridgeStatus>()) { acc, item ->
                with(acc) {
                    val first = acc.find { it.id == item.id }
                    if (null == first) {
                        acc.add(item)
                    } else {
                        first.meaningFullUpdateUsing(item)
                    }
                    acc
                }
            }
            .apply {
                BeaconDataRepository.addBridges(this.map { it.copy(id = it.id.uppercase()) })
            }

        hbOutOfWindow.takeUnless {
            it.isEmpty()
        }?.distinctBy {
            it.srcMac()
        }?.apply {
            BeaconDataRepository.addBridgesHeartbeat(this)
        }

        melOutOfWindow.takeUnless {
            it.isEmpty()
        }?.apply {
            BeaconDataRepository.sendMelPayload(this)
        }

        bridgeEarlyOutOfWindow.takeUnless {
            it.isEmpty()
        }?.apply {
            BeaconDataRepository.addEarlyBridge(this)
        }

        if (dataPacketsOutOfWindow.isEmpty() && siOutOfWindow.isEmpty() && metaOutOfWindow.isEmpty()) {
            return
        }

        val isResolveEnabled = WiliotAppConfigurationSource.configSource.resolveEnabled()

        dataPacketsOutOfWindow.forEach { dataPacket ->
            if (dataPacket.dataPacketType() == DataPacketType.DIRECT && TrafficRule.shouldUploadDirectDataTraffic) {
                Wiliot.upstream().vBridge?.addDirectPacket(dataPacket)
            }
            if (isResolveEnabled) {
                BeaconDataRepository.addPacketDataToResolveOnly(PacketData(dataPacket))
            }
        }

        if (TrafficRule.shouldUploadRetransmittedDataTraffic) {
            (dataPacketsOutOfWindow + metaOutOfWindow).mapNotNull { abstractPacket ->
                when (abstractPacket) {
                    is DataPacket,
                    is ExternalSensorPacket,
                    is MetaPacket
                        -> PacketData(abstractPacket)

                    else -> null
                }
            }.apply {
                this.apply fApply@{
                    if (isEmpty())
                        return@fApply

                    BeaconDataRepository.sendInstantPayload(
                        this
                            .filter {
                                if (it.packet is DataPacket)
                                    (it.packet as DataPacket).dataPacketType() != DataPacketType.DIRECT
                                else
                                    true
                            }
                    )
                }
            }
        }

        siOutOfWindow.filter { p ->

            fun BaseMetaPacket.isFromVirtualBridge(): Boolean {
                return this.deviceMac.replace(":", "").lowercase() == Wiliot.virtualBridgeId?.replace(":", "")?.lowercase()
            }

            if (TrafficRule.shouldUploadAnyDataTraffic) {
                true
            } else if (TrafficRule.shouldUploadDirectDataTraffic) {
                p.isFromVirtualBridge()
            } else {
                // TrafficRule.shouldUploadRetransmittedDataTraffic == true
                p.isFromVirtualBridge().not()
            }
        }.map {
            PacketData(it)
        }.apply {
            this.apply fApply@{
                if (isEmpty()) return@fApply

                BeaconDataRepository.sendInstantPayload(this)
            }
        }

    }

    for (msg in channel) {
        when (msg) {
            is JudgeResult -> with(msg.result) {
                when {
                    null != wiliotBridgeEarlyPacket && Wiliot.configuration.enableEdgeTraffic -> {
                        with(wiliotBridgeEarlyPacket!!) {
                            if (this is BridgeEarlyPacket && !judgeBufferBridgeEarly.contains(this)) {
                                judgeBufferBridgeEarly.add(this)
                            }
                        }
                    }

                    null != wiliotManufacturerData && Wiliot.configuration.enableDataTraffic -> {
                        /** this is old data packet. we should process it using the internal device pacing
                         * but filter duplicates first */
                        with(wiliotManufacturerData!!) {
                            if (this is DataPacket && !judgeBufferData.contains(this)) {
                                judgeBufferData.add(this)
                            }
                        }
                    }

                    null != wiliotServiceData -> {
                        /** Service Id packets having a lot of logic. the action depends on the group Id*/
                        handleServiceUUIDPacket(wiliotServiceData)
                    }

                    else -> {}
                }

                // extra mapping?
                if (System.currentTimeMillis() - lastMapTimestamp >= MAX_MAP_ITERATIONS_DIFF_MS) {
                    if (BuildConfig.DEBUG) Reporter.log("extra mapping invoked", "PacketsRepoMsg")
                    lastMapTimestamp = System.currentTimeMillis()
                    performMap()
                }
            }

            MapBuffer -> {
                lastMapTimestamp = System.currentTimeMillis()
                performMap()
            }
        }
    }
}