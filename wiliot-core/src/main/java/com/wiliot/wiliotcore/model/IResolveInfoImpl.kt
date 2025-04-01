package com.wiliot.wiliotcore.model

class IResolveInfoImpl(
    override val deviceMAC: String,
    override var resolveTimestamp: Long = System.currentTimeMillis()
) : IResolveInfo {
    override var name: String = ""
    override var ownerId: String? = null
    override var labels: List<String>? = null
    override var waitingForUpdate: Boolean? = null
    override var asset: Asset? = null
}