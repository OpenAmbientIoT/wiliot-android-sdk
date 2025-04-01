package com.wiliot.wiliotupstream.domain.repository

import com.wiliot.wiliotcore.model.*
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotupstream.feature.withExtraEdgeProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import java.time.Instant

internal sealed class BridgePacketsRepoMsg

internal class PushBridgesHeartbeat(
    val data: List<BridgeHbPacketAbstract>,
) : BridgePacketsRepoMsg()

internal class AddBridges(
    val data: List<BridgeStatus>,
) : BridgePacketsRepoMsg()

internal class AddEarlyBridges(
    val data: List<BridgeEarlyPacket>
) : BridgePacketsRepoMsg()

internal data object ClearBridges : BridgePacketsRepoMsg()

@OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
internal fun CoroutineScope.bridgesRepoActor() = actor<BridgePacketsRepoMsg> {
    Reporter.log("BridgePackets actor created", logTag())
    val bridges = HashMap<String, BridgeStatus>()

    suspend fun pushBridgesHeartbeat(msg: PushBridgesHeartbeat) = with(msg) {
        BeaconDataRepository.sendBridgesHb(data)

        // update presence
        withExtraEdgeProcessor {
            notifyBridgesPresence(
                bridgesIds = data.map { it.srcMac() },
                rssiMap = data.associate { Pair(it.srcMac(), it.scanRssi) }
            )
        }
    }

    suspend fun addBridges(msg: AddBridges) = with(msg) {
        val newBridges = this.data.filter { !bridges.contains(it.id) }
        val updatedConfiguration = mutableListOf<BridgeStatus>()

        data.forEach { status ->
            val savedStatus = bridges[status.id]
            if (null == savedStatus) {
                bridges[status.id] = status
            } else {
                if (savedStatus.meaningFullUpdateUsing(status))
                    updatedConfiguration.add(savedStatus)
            }
        }
        (newBridges + updatedConfiguration).takeUnless { it.isEmpty() }?.apply {
            withExtraEdgeProcessor { bridgesAdded(this@apply) }
            BeaconDataRepository.sendBridgesPayload(this)
        }
        // filter outdated
        val instant10MinutesAgoMilli = Instant.now()
            .minusSeconds(600)
            .toEpochMilli()
        bridges
            .filterValues { value -> value.timestamp <= instant10MinutesAgoMilli }
            .takeUnless { it.isEmpty() }
            ?.values?.map { it.copy(timestamp = -1) }
            ?.apply {
                BeaconDataRepository.sendBridgesPayload(this)
            }
            ?.forEach {
                bridges.remove(it.id)
            }

        // update unresolved bridges
        withExtraEdgeProcessor { checkUnresolvedBridges() }
    }

    fun addEarlyBridges(msg: AddEarlyBridges) = with(msg) {
        val newBridges = this.data.filter { !bridges.contains(it.id) }

        newBridges.takeUnless { it.isEmpty() }?.apply {
            withExtraEdgeProcessor { bridgesAdded(this@apply.mapNotNull { it.toBridgeStatusOrNull() }) }
        }

        // update unresolved bridges
        withExtraEdgeProcessor { checkUnresolvedBridges() }

        // update presence
        withExtraEdgeProcessor {
            notifyBridgesPresence(
                bridgesIds = this@with.data.mapNotNull { it.id },
                rssiMap = this@with.data.filter { it.id != null }.associate { Pair(it.id!!, it.scanRssi) }
            )
        }
    }

    fun clearBridges() {
        bridges.clear()
    }

    for (msg in channel) {
        when (msg) {
            is PushBridgesHeartbeat -> pushBridgesHeartbeat(msg)
            is AddBridges -> addBridges(msg)
            is AddEarlyBridges -> addEarlyBridges(msg)
            is ClearBridges -> clearBridges()
        }
    }

}
