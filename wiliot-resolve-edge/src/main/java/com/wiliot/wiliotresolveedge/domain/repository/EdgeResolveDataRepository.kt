package com.wiliot.wiliotresolveedge.domain.repository

import com.wiliot.wiliotcore.model.*
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
internal object EdgeResolveDataRepository {

    private val logTag = logTag()

    private fun buildNewScope() = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var bridgesScope = buildNewScope()
    private var bridgesActor: SendChannel<BridgeRepoMsg> = bridgesScope.bridgesRepoActor()

    private val mBridgesState = MutableStateFlow<List<Bridge>>(listOf())
    val bridgesState = mBridgesState.asStateFlow()

    internal fun initActorAsync() {
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

    internal fun suspendActor() {
        bridgesScope.cancel()
    }

    internal fun clearList() {
        bridgesScope.launch {
            bridgesActor.send(ClearBridges)
        }
        clearStates()
    }

    private fun clearStates() {
        mBridgesState.value = listOf()
    }

    internal fun updateBridgesState(bridges: List<Bridge>) {
        mBridgesState.value = bridges.sortedWith { o1, o2 ->
            o1.applySort(o2)
        }
    }

    internal fun sendAskForBridgeInfo(payload: BridgeStatus) {
        bridgesScope.launch {
            bridgesActor.send(AskForBridgeInfo(payload))
        }
    }

    internal fun sendAskForBridgesInfoIfNeeded(payload: List<BridgeStatus>) {
        bridgesScope.launch {
            bridgesActor.send(AskForBridgeInfoIfNeeded(payload))
        }
    }

    internal fun sendCheckUnresolvedBridges() {
        bridgesScope.launch {
            bridgesActor.send(CheckUnresolvedBridges)
        }
    }

    internal fun flagBridge(bridgeId: String) {
        bridgesScope.launch {
            bridgesActor.send(FlagBridge(bridgeId))
        }
    }

    internal fun updateBridgePresence(bridgesIds: List<String>, rssiMap: Map<String, Int>) {
        bridgesScope.launch {
            bridgesActor.send(UpdatePresence(bridgesIds, rssiMap))
        }
    }

    internal fun notifyBridgeFirmwareVersionChanged(bridgeId: String, newVersion: String) {
        bridgesScope.launch {
            bridgesActor.send(UpdateFirmwareVersionInfo(bridgeId, newVersion))
        }
    }

    internal fun sendBridgesAdded(bridges: List<BridgeStatus>) {
        bridgesScope.launch {
            bridgesActor.send(BridgesAdded(bridges))
        }
    }

}