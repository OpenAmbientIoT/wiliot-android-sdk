package com.wiliot.wiliotresolvedata.domain.repository

import com.wiliot.wiliotcore.model.Asset
import com.wiliot.wiliotcore.model.IResolveInfo
import com.wiliot.wiliotresolvedata.WiliotDataResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor

sealed class AssetRepoMsg

class AskForAsset(
    val packet: IResolveInfo,
) : AssetRepoMsg()

class AddClaimableAsset(val asset: Asset) : AssetRepoMsg()

class ClearAssets(val assetResolveInfo: IResolveInfo?) : AssetRepoMsg()

class FlagAssets(val assetResolveInfo: IResolveInfo?) : AssetRepoMsg()

@OptIn(ExperimentalCoroutinesApi::class)
@ObsoleteCoroutinesApi
fun CoroutineScope.assetsRepoActor() = actor<AssetRepoMsg> {
    val assets = HashSet<Asset>()

    //region implementations

    fun addClaimableAsset(msg: AddClaimableAsset) = with(msg) {
        if (assets.find { it.id == asset.id } != null) return@with
        assets.removeIf { it.id == asset.id }
        assets.add(asset)
        MetaResolveDataRepository.updateAssetsState(assets.toList())
    }

    suspend fun askForAsset(msg: AskForAsset) = with(msg) {
        WiliotDataResolver.askForAssets(this.packet)?.takeIf { it.isNotEmpty() }?.apply {
            val filteredResponse = this.mapNotNull {
                it.takeIf { rAsset ->
                    val foundPostponed = assets.firstOrNull { a ->
                        a.id == rAsset.id && a.isPostponedForUpdate()
                    }
                    val isConflictingWithPostponed = foundPostponed?.let { p ->
                        (System.currentTimeMillis() - p.postponedBlockerTimestamp()) < WiliotDataResolver.DEFAULT_ASSET_BLOCKER_DELAY
                    } ?: false
                    isConflictingWithPostponed.not()
                }
            }
            val mappedNewIds = filteredResponse.map { it.id }
            assets.removeAll { storedAsset ->
                storedAsset.id in mappedNewIds
            }
            assets.addAll(filteredResponse)
            MetaResolveDataRepository.updateAssetsState(assets.toList())
        }
    }

    fun clearAssets(msg: ClearAssets) = with(msg) {
        when (assetResolveInfo) {
            null -> {
                assets.clear()
                MetaResolveDataRepository.updateAssetsState(listOf())
            }
            else -> {
                assets.removeIf { item ->
                    item.tags.any { tag ->
                        tag.tagId.contentEquals(assetResolveInfo.name)
                                || assetResolveInfo.labels?.contains(tag.tagId) == true
                    }
                }
                MetaResolveDataRepository.updateAssetsState(assets.toList())
            }
        }
    }

    fun flagAssets(msg: FlagAssets) = with(msg) {
        when (assetResolveInfo) {
            null -> {
                val flagged = assets.map {
                    it.copy(postponedForUpdate = Pair(true, System.currentTimeMillis()))
                }
                assets.clear()
                assets.addAll(flagged)
                MetaResolveDataRepository.updateAssetsState(assets.toList())
            }
            else -> {
                val flagged = mutableListOf<Asset>()
                assets.removeIf { item ->
                    val predicate = item.tags.any { tag ->
                        tag.tagId.contentEquals(assetResolveInfo.name)
                                || assetResolveInfo.labels?.contains(tag.tagId) == true
                    }
                    if (predicate) flagged.add(item.copy(postponedForUpdate = Pair(true,
                        System.currentTimeMillis())))
                    predicate
                }
                assets.addAll(flagged)
                MetaResolveDataRepository.updateAssetsState(assets.toList())
            }
        }
    }

    //endregion

    for (msg in channel) {
        when (msg) {
            is AskForAsset -> askForAsset(msg)
            is ClearAssets -> clearAssets(msg)
            is FlagAssets -> flagAssets(msg)
            is AddClaimableAsset -> addClaimableAsset(msg)
        }
    }
}