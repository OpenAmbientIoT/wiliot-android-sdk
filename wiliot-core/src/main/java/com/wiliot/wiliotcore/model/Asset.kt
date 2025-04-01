package com.wiliot.wiliotcore.model

import com.google.gson.annotations.SerializedName
import com.wiliot.wiliotcore.utils.PixelTypeUtil

data class AssetWrapper(
    val asset: Asset?,
    val error: Throwable? = null
)

data class Asset(
    val id: String,
    val tags: List<Tag>,
    val name: String?,
    val category: AssetCategory?,
    val categoryId: String?,
    val postponedForUpdate: Pair<Boolean, Long>? = Pair(false, 0L)
) {
    companion object {
        fun sortingComparator(): Comparator<Asset> {
            return compareBy {
                it.id
            }
        }
    }

    fun isPostponedForUpdate(): Boolean {
        return postponedForUpdate != null && postponedForUpdate.first
    }

    fun postponedBlockerTimestamp(): Long {
        return postponedForUpdate?.second ?: 0L
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean =
        when (other) {
            is Asset -> id == other.id && isPostponedForUpdate() == other.isPostponedForUpdate() && category == other.category
            else -> false
        }
}

data class AssetCategory(
    val id: String?,
    val name: String?,
    @SerializedName("sku_upc")
    val sku: String?
)

data class Tag(
    val tagId: String
)

val Asset.serialNumber: String
    get() = if (PixelTypeUtil.isPixel(tags.firstOrNull()?.tagId)) {
        PixelTypeUtil.extractPixelIdOrNull(tags.first().tagId)!!
    } else {
        tags.firstOrNull()?.tagId ?: "INVALID"
    }

val Tag.serialNumber: String
    get() = if (PixelTypeUtil.isPixel(tagId)) {
        PixelTypeUtil.extractPixelIdOrNull(tagId)!!
    } else {
        tagId
    }
