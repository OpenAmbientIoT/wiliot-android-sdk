package com.wiliot.wiliotupstream.domain.repository

import com.wiliot.wiliotcore.model.AbstractEchoPacket
import com.wiliot.wiliotcore.model.Ack
import com.wiliot.wiliotcore.model.BasePacketData
import com.wiliot.wiliotcore.model.BridgeEarlyPacket
import com.wiliot.wiliotcore.model.BridgeHbPacketAbstract
import com.wiliot.wiliotcore.model.BridgeStatus
import com.wiliot.wiliotcore.model.UnifiedEchoPacket
import com.wiliot.wiliotcore.model.MelModulePacket
import com.wiliot.wiliotcore.model.Packet
import com.wiliot.wiliotcore.model.PacketData
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.ScanResultInternal
import com.wiliot.wiliotcore.utils.logTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
internal object BeaconDataRepository {

    private val logTag = logTag()

    private fun buildNewScope() = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var packetsScope = buildNewScope()
    private var packetsActor: SendChannel<PacketsRepoMsg> = packetsScope.packetsRepoActor()

    private var beaconsScope = buildNewScope()
    private var beaconsActor: SendChannel<BeaconPacketsRepoMsg> = beaconsScope.beaconsRepoActor()

    private var outerDataScope = buildNewScope()

    private var bridgesScope = buildNewScope()
    private var bridgesActor: SendChannel<BridgePacketsRepoMsg> = bridgesScope.bridgesRepoActor()

    private val mMelAckPayload = MutableSharedFlow<Ack>()
    internal val melAckPayload: SharedFlow<Ack> = mMelAckPayload

    private val mLogsPayload = MutableSharedFlow<List<Any>>()
    internal val logsPayload: SharedFlow<List<Any>> = mLogsPayload

    private val mBridgeAckPayload = MutableSharedFlow<List<String>>()
    internal val bridgeAckPayload: SharedFlow<List<String>> = mBridgeAckPayload

    private val mInstantPayload = MutableSharedFlow<List<BasePacketData>>()
    internal val instantPayload: SharedFlow<List<BasePacketData>> = mInstantPayload

    private val mMelPayload = MutableSharedFlow<List<MelModulePacket>>()
    internal val melPayload: SharedFlow<List<MelModulePacket>> = mMelPayload

    private val mBridgesPayload = MutableSharedFlow<List<BridgeStatus>>()
    internal val bridgesPayload: SharedFlow<List<BridgeStatus>> = mBridgesPayload

    private val mBridgesHbPayload = MutableSharedFlow<List<BridgeHbPacketAbstract>>()
    internal val bridgesHbPayload: SharedFlow<List<BridgeHbPacketAbstract>> = mBridgesHbPayload

    private fun initPacketsActor(){
        try {
            packetsScope.ensureActive()
        } catch (e: CancellationException) {
            packetsScope = buildNewScope().apply {
                packetsActor = packetsRepoActor().apply {
                    invokeOnClose {
                        Reporter.log("Packets actor closed with: $it", logTag)
                    }
                }
            }
        }
    }
    private fun initBeaconsActor(){
        try {
            beaconsScope.ensureActive()
        } catch (e: CancellationException) {
            beaconsScope = buildNewScope().apply {
                beaconsActor = beaconsRepoActor().apply {
                    invokeOnClose {
                        Reporter.log("Beacons actor closed with: $it", logTag)
                    }
                }
            }
        }
    }
    private fun initBridgesActor(){
        try {
            bridgesScope.ensureActive()
        } catch (e: CancellationException) {
            bridgesScope = buildNewScope().apply {
                bridgesActor = bridgesRepoActor().apply {
                    invokeOnClose {
                        Reporter.log("Bridges actor closed with: $it", logTag)
                    }
                }
            }
        }
    }

    @ObsoleteCoroutinesApi
    internal fun initActorAsync() {
        initPacketsActor()
        initBeaconsActor()
        initBridgesActor()
    }

    internal fun suspendActor() {
        packetsScope.cancel()
        beaconsScope.cancel()
        bridgesScope.cancel()
    }

    internal fun sendPacketsFromVirtualBridge(payload: List<Packet>) {
        outerDataScope.launch {
            payload.filterIsInstance<AbstractEchoPacket>().takeIf { it.isEmpty().not() }?.let { si ->
                si.map {
                    PacketData(it)
                }.let {
                    sendInstantPayload(it)
                }
            }
            payload.filterIsInstance<MelModulePacket>().takeIf { it.isEmpty().not() }?.let {
                sendMelPayload(it)
            }
            payload.filterIsInstance<BridgeHbPacketAbstract>().takeIf { it.isEmpty().not() }?.let {
                sendBridgesHb(it)
            }
        }
    }

    internal suspend fun sendInstantPayload(payload: List<BasePacketData>) {
        mInstantPayload.emit(payload)
    }

    internal suspend fun sendMelPayload(payload: List<MelModulePacket>) {
        mMelPayload.emit(payload)
    }

    internal suspend fun sendLogPayload(payload: List<Any>) {
        mLogsPayload.emit(payload)
    }

    internal fun sendMelAckPayload(payload: Ack) {
        outerDataScope.launch {
            mMelAckPayload.emit(payload)
        }
    }

    internal suspend fun sendBridgeAckPayload(payload: List<String>) {
        mBridgeAckPayload.emit(payload)
    }

    suspend fun addBridges(bridges: List<BridgeStatus>) {
        bridgesActor.send(AddBridges(bridges))
    }

    suspend fun addEarlyBridge(bridges: List<BridgeEarlyPacket>) {
        bridgesActor.send(AddEarlyBridges(bridges))
    }

    suspend fun addBridgesHeartbeat(bridges: List<BridgeHbPacketAbstract>) {
        bridgesActor.send(PushBridgesHeartbeat(bridges))
    }

    internal suspend fun sendBridgesHb(payload: List<BridgeHbPacketAbstract>) {
        mBridgesHbPayload.emit(payload)
    }

    internal suspend fun sendBridgesPayload(payload: List<BridgeStatus>) {
        mBridgesPayload.emit(payload)
    }

    suspend fun addPacketDataToResolveOnly(packetData: PacketData) {
        beaconsActor.send(AddBeaconToResolveOnly(packetData))
    }

    fun clearList() {
        beaconsScope.launch {
            beaconsActor.send(ClearBeacons)
        }
        bridgesScope.launch {
            bridgesActor.send(ClearBridges)
        }
    }

    fun getAllBeacons(isProMode: Boolean = false) {
        beaconsScope.launch {
            beaconsActor.send(GetForSignalLevel(isProMode))
        }
    }

    internal fun judgeResult(result: ScanResultInternal) {
        packetsScope.launch {
            packetsActor.send(JudgeResult(result))
        }
    }

}
