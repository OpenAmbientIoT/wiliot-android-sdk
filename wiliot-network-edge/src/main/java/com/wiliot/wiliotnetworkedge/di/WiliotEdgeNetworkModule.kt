package com.wiliot.wiliotnetworkedge.di

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.contracts.EdgeNetworkManagerContract
import com.wiliot.wiliotcore.model.Bridge
import com.wiliot.wiliotcore.model.BridgeWrapper
import com.wiliot.wiliotnetworkedge.api.WiliotEdgeApiService
import com.wiliot.wiliotnetworkedge.api.model.parseResponse

class EdgeNetworkManager private constructor() : EdgeNetworkManagerContract {

    companion object {
        private const val logTag = "EDGE/NetworkManager"

        private var INSTANCE: EdgeNetworkManager? = null

        internal fun getInstance(): EdgeNetworkManager {
            if (INSTANCE == null) INSTANCE = EdgeNetworkManager()
            return INSTANCE!!
        }
    }

    //==============================================================================================
    // *** Contract ***
    //==============================================================================================

    // region [Contract]

    override suspend fun askForBridge(bridgeId: String): BridgeWrapper {
        var bridgeResult = BridgeWrapper(BridgeWrapper.Result.ERROR, null)
        try {
            getOwnerId()?.let { savedOwnerId ->
                pickApiService().runCatching {
                    resolveBridgeAsync(savedOwnerId, bridgeId).await()
                }.onSuccess { result ->
                    if (result.isSuccessful) {
                        result.body()?.let {
                            bridgeResult =
                                BridgeWrapper(BridgeWrapper.Result.OK, it.parseResponse())
                        }
                    } else if (result.code() == 403) {
                        bridgeResult = BridgeWrapper(
                            BridgeWrapper.Result.UNAVAILABLE,
                            Bridge(
                                id = bridgeId,
                                name = null,
                                claimed = true,
                                owned = false,
                                fwVersion = null,
                                pacingRate = null,
                                energizingRate = null,
                                resolved = true,
                                processedButNotResolved = false,
                                zone = null,
                                location = null,
                                connections = null,
                                boardType = null
                            )
                        )
                    } else if (result.code() == 404) {
                        bridgeResult = BridgeWrapper(
                            BridgeWrapper.Result.UNKNOWN,
                            Bridge(
                                id = bridgeId,
                                name = null,
                                claimed = false,
                                owned = false,
                                fwVersion = null,
                                pacingRate = null,
                                energizingRate = null,
                                resolved = false,
                                processedButNotResolved = true,
                                zone = null,
                                location = null,
                                connections = null,
                                boardType = null
                            )
                        )
                    } else {
                        bridgeResult = BridgeWrapper(
                            BridgeWrapper.Result.ERROR,
                            null,
                            Exception(
                                "Result code for bridgeId $bridgeId: ${result.code()}"
                            )
                        )
                    }
                }.onFailure {
                    bridgeResult = BridgeWrapper(
                        BridgeWrapper.Result.ERROR,
                        null,
                        it
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            bridgeResult = BridgeWrapper(BridgeWrapper.Result.ERROR, null, e)
        }
        return bridgeResult
    }

    // endregion

    //==============================================================================================
    // *** Domain ***
    //==============================================================================================

    // region [Domain]

    private fun pickApiService(): WiliotEdgeApiService {
        return apiService()
    }

    private fun getOwnerId(): String? {
        val ownerId = Wiliot.configuration.ownerId
        ownerId.orEmpty().takeIf { it.isBlank() }?.apply {
            throw IllegalStateException(
                "ownerId is not specified in configuration"
            )
        }
        return ownerId
    }

}

// endregion

fun edgeNetworkManager(): EdgeNetworkManagerContract = EdgeNetworkManager.getInstance()