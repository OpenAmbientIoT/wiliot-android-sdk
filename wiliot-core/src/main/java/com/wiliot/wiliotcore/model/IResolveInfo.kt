package com.wiliot.wiliotcore.model

import com.wiliot.wiliotcore.utils.PixelTypeUtil
import java.io.Serializable

interface IResolveInfo : Serializable {
    val deviceMAC: String
    var name: String
    var ownerId: String?
    var labels: List<String>?
    var resolveTimestamp: Long
    var waitingForUpdate: Boolean?
    var asset: Asset?
}

/**
 * Set it to 'true' in case when you need to force get new [IResolveInfo] for beacon but not to clear
 * previous [IResolveInfo] for that beacon.
 * E. g. for refresh feature
 */
var IResolveInfo.shouldUpdate: Boolean
    get() = waitingForUpdate != null && waitingForUpdate == true
    set(value) {
        waitingForUpdate = value
    }

val IResolveInfo.serialNumber: String
    get() = if (PixelTypeUtil.isPixel(name)) {
        PixelTypeUtil.extractPixelIdOrNull(name)!!
    } else {
        name
    }

val IResolveInfo.gqlQueryIdentifier: String
    get() = if (serialNumber.contains("-")) serialNumber.replace("-", "") else serialNumber

val IResolveInfo.isStarterKitTag: Boolean
    get() = "devkit".contentEquals(this.ownerId)
