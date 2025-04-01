package com.wiliot.wiliotcore.model

data class PrecisePosition(
    val x: Double,
    val y: Double,
    val z: Double,
    val accuracy: Double,
    val floorOrder: Byte,
    val prevIsLocked: Byte,
    val isLocked: Byte,
    val detectionTimestamp: Long
) {
    fun isValid(): Boolean = detectionTimestamp > 0
}

@ExperimentalStdlibApi
fun PrecisePosition.toHexPayload(): String {
    return StringBuilder().apply {
        append("000a")
        append(x.toBits().toHexString())
        append(y.toBits().toHexString())
        append(accuracy.toBits().toHexString())
        append(floorOrder.toHexString())
        append((isLocked + prevIsLocked).toByte().toHexString())
        append("00")
    }.toString()
}
