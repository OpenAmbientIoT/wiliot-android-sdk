package com.wiliot.wiliotnetworkmeta.di

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.WiliotCounter
import com.wiliot.wiliotcore.contracts.MetaNetworkManagerContract
import com.wiliot.wiliotcore.model.Asset
import com.wiliot.wiliotcore.model.AssetWrapper
import com.wiliot.wiliotcore.model.IResolveInfo
import com.wiliot.wiliotcore.model.IResolveInfoImpl
import com.wiliot.wiliotcore.model.Location
import com.wiliot.wiliotcore.model.PackedData
import com.wiliot.wiliotcore.model.PacketData
import com.wiliot.wiliotcore.sensors.WiliotSensorManager
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotnetworkmeta.api.WiliotMetaApiService
import com.wiliot.wiliotnetworkmeta.api.model.Gateway
import com.wiliot.wiliotnetworkmeta.gql.GQLApiService
import com.wiliot.wiliotnetworkmeta.gql.GQLAssetForTag
import com.wiliot.wiliotnetworkmeta.gql.model.ApiRequest

class MetaNetworkManager private constructor(): MetaNetworkManagerContract {

    companion object {
        private const val logTag = "META/NetworkManager"

        private var INSTANCE: MetaNetworkManager? = null

        internal fun getInstance(): MetaNetworkManager {
            if (INSTANCE == null) INSTANCE = MetaNetworkManager()
            return INSTANCE!!
        }
    }

    //==============================================================================================
    // *** Contract ***
    //==============================================================================================

    // region [Contract]

    override suspend fun resolvePacketData(packetData: PacketData): IResolveInfoImpl? {
        var resolveResult: IResolveInfoImpl? = null
        try {
            getOwnerId()?.let { savedOwnerId ->
                val gateway = createGateway(getLocation(), packetData)
                pickApiService().runCatching {
                    resolveBeaconDataAsync(gateway, savedOwnerId).await()
                }.onSuccess { result ->
                    result.takeIf { it.isSuccessful }?.apply {
                        resolveResult = IResolveInfoImpl(packetData.deviceMAC)
                        body()?.data?.takeIf { data -> data.isNotEmpty() }?.first()?.apply {
                            resolveResult?.name = externalId
                            resolveResult?.labels = labels
                            resolveResult?.ownerId = ownerId
                            resolveResult?.asset = asset
                        }
                    }
                    result.takeIf { !it.isSuccessful }?.let {
                        Reporter.log("resolvePacketData: success = ${it.isSuccessful}", logTag)
                        if (it.code() == 400) {
                            resolveResult = IResolveInfoImpl(packetData.deviceMAC)
                            resolveResult?.name = "Unresolved"
                            resolveResult?.labels = null
                            resolveResult?.ownerId = null
                            resolveResult?.asset = null
                            resolveResult?.waitingForUpdate = false
                        }
                    }
                }.onFailure {
                    Reporter.log("$it", logTag, highlightError = true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resolveResult
    }

    override suspend fun askForAssets(packet: IResolveInfo, ignoreFlowVersion: Boolean): List<Asset>? {
        var assetsResult: List<Asset>? = null
        try {
            GQLAssetForTag(packet, ignoreFlowVersion).apply {
                getOwnerId()?.let { savedOwnerId ->
                    pickGqlService().runCatching {
                        askForAssetsAsync(
                            savedOwnerId,
                            ApiRequest(this@apply.generateQuery())
                        ).await()
                    }.onSuccess { result ->
                        result.body()?.let { resp ->
                            assetsResult = parseResponse(resp)
                        }
                    }.onFailure { err ->
                        Reporter.log("$err", logTag, highlightError = true)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return assetsResult
    }

    override suspend fun askForAssetByPixelId(pixelId: String): AssetWrapper {
        var assetResult = AssetWrapper(null, Exception("Unknown"))

        try {
            GQLAssetForTag(
                object : IResolveInfo {
                    override var name: String = pixelId

                    // dummy implementation
                    override val deviceMAC: String = "000000000000"
                    override var ownerId: String? = null
                    override var labels: List<String>? = null
                    override var resolveTimestamp: Long = 1
                    override var waitingForUpdate: Boolean? = false
                    override var asset: Asset? = null
                },
                useDefaultFlowVersion = true
            ).apply {
                getOwnerId()?.let { savedOwnerId ->
                    pickGqlService().runCatching {
                        askForAssetsAsync(
                            savedOwnerId,
                            ApiRequest(this@apply.generateQuery())
                        ).await()
                    }.onSuccess { result ->
                        result.body()?.let { resp ->
                            assetResult = AssetWrapper(parseResponse(resp)?.firstOrNull(), null)
                        }
                    }.onFailure { err ->
                        Reporter.log("$err", logTag, highlightError = true)
                        assetResult = AssetWrapper(null, err)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return assetResult
    }

    // endregion

    //==============================================================================================
    // *** Domain ***
    //==============================================================================================

    // region [Domain]

    private fun pickApiService(): WiliotMetaApiService {
        return apiService()
    }

    private fun pickGqlService(): GQLApiService {
        return gqlService()
    }

    private fun getLocation() = Wiliot.locationManager.getLastLocation()

    private fun getOwnerId(): String? {
        val ownerId = Wiliot.configuration.ownerId
        ownerId.orEmpty().takeIf { it.isBlank() }?.apply {
            throw IllegalStateException(
                "ownerId is not specified in configuration"
            )
        }
        return ownerId
    }

    private fun createGateway(
        location: android.location.Location?,
        beacon: PacketData,
    ): Gateway {
        val parameterLocation: Location? = if (location == null) {
            null
        } else {
            Location(location.latitude, location.longitude)
        }
        val packedData = PackedData(
            WiliotSensorManager.acceleration,
            if (null == beacon.location) parameterLocation else beacon.location,
            beacon.name,
            beacon.data,
            WiliotCounter.value
        )
        val packetList = ArrayList<PackedData>()
        packetList.add(packedData)
        return Gateway(
            packetList,
            WiliotSensorManager.ambientTemperature,
            WiliotSensorManager.temperature
        )
    }

    // endregion

}

fun metaNetworkManager(): MetaNetworkManagerContract = MetaNetworkManager.getInstance()
