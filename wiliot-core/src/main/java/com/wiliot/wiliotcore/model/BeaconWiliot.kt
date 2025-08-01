package com.wiliot.wiliotcore.model

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.os.Parcel
import android.os.ParcelUuid
import com.wiliot.wiliotcore.BuildConfig
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.ScanResultInternal
import com.wiliot.wiliotcore.utils.bitMask
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotcore.utils.toHexString
import kotlin.experimental.and

class BeaconWiliot {
    companion object {
        const val MANUFACTURED_UUID = 0x0500
        val serviceUuidTest: ParcelUuid =
            ParcelUuid.fromString("0000FDD0-0000-1000-8000-00805F9B34FB")
        val bridgeServiceUuid: ParcelUuid =
            ParcelUuid.fromString("0000180a-0000-1000-8000-00805f9b34fb") // for bridges in connectable mode
        val serviceUuid: ParcelUuid = ParcelUuid.fromString("0000FDAF-0000-1000-8000-00805F9B34FB")
        val serviceUuid2: ParcelUuid = ParcelUuid.fromString("0000FCC6-0000-1000-8000-00805F9B34FB")
        val sensorServiceUuid: ParcelUuid = ParcelUuid.fromString("0000FC90-0000-1000-8000-00805F9B34FB")
        val serviceUuidD2p2: ParcelUuid =
            ParcelUuid.fromString("000005AF-0000-1000-8000-00805F9B34FB")
        val manufactureUuid: ParcelUuid =
            ParcelUuid.fromString("00000500-0000-1000-8000-00805F9B34FB")
    }
}

private val beaconLogTag = BeaconWiliot.logTag()

fun BridgeEarlyPacket.toBridgeStatusOrNull(): BridgeStatus? = with(id) {
    return@with if (this != null) BridgeStatus(
        id = this,
        formationType = BridgeStatus.FormationType.FROM_EARLY_PKT
    ) else null
}

fun MetaPacket.toBridgeStatus(): BridgeStatus = BridgeStatus(
    id = bridgeMacAddress,
    packet = this,
    formationType = BridgeStatus.FormationType.FROM_SIDE_INFO
)

fun MelModulePacket.toBridgeStatus(): BridgeStatus = BridgeStatus(
    id = bridgeId,
    packet = this,
    formationType = BridgeStatus.FormationType.FROM_MEL
)

fun BridgeHbPacketAbstract.toBridgeStatus(): BridgeStatus = BridgeStatus(
    id = srcMac(),
    packet = this,
    formationType = BridgeStatus.FormationType.FROM_HB
)

fun BridgePacketAbstract.toBridgeStatus(): BridgeStatus = BridgeStatus(
    id = srcMac,
    packet = this,
    config = toBridgeConfiguration(),
    boardType = extractBoardTypeOrNull(),
    version = version(),
    formationType = BridgeStatus.FormationType.FROM_CONFIG_PKT
)

enum class DataPacketType(val prefix: String) {
    SHORT_MD("0500"), // Manufacturer data
    SHORT_SD("05af"), // Service data
    DIRECT("affd"),
    RETRANSMITTED("c6fc"), // fcc6
    SENSOR_DATA("90fc"); // fc90

    fun isOneOf(vararg s: DataPacketType): Boolean = this in s
}

data class DataPacket(
    override val value: String,
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis(),
) : PacketAbstract() {
    override val deviceMac: String
        get() = scanResult.device.address
    override val scanRssi: Int
        get() = scanResult.rssi

    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readParcelable(ScanResult::class.java.classLoader)!!,
        parcel.readLong()
    )

    fun dataPacketType(): DataPacketType? {
        return DataPacketType.values().firstOrNull { value.startsWith(it.prefix) }
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

}

abstract class BaseMetaPacket : PacketAbstract()

data class MetaPacket(
    override var value: String,
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis(),
) : BaseMetaPacket() {
    override fun equals(other: Any?): Boolean = when (other) {
        is MetaPacket -> super.equals(other) && sourceAddress == other.sourceAddress
        else -> false
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    internal val sourceAddress: Long
        get() = bridgeMacAddress.toLong(16)
    val bridgeMacAddress: String
        get() = value.substring(10..21).uppercase()
    val filteredCount: Int
        get() = value.substring(22..25).toInt(16)
    val rssi: Int
        get() = value.substring(26..27).toInt(16)
    val brgId: String
        get() = value.substring(28..29)
    override val deviceMac: String
        get() = scanResult.device.address
    override val scanRssi: Int
        get() = scanResult.rssi

    val aliasBridgeId: String
        get() = scanResult.device.address.replace(":", "")

    override fun describeData() = Reporter.log(
        "MPack: bMAC:$bridgeMacAddress brId: [$brgId] nfpkt: [$filteredCount] rssi: [$rssi] val:[$value]",
        beaconLogTag
    )
}

data class ExternalSensorPacket(
    override var value: String,
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis(),
) : BaseMetaPacket() {

    override fun equals(other: Any?): Boolean = when (other) {
        is ExternalSensorPacket -> super.equals(other) && sourceAddress == other.sourceAddress
        else -> false
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    internal val sourceAddress: Long
        get() = bridgeMacAddress.toLong(16)

    val bridgeMacAddress: String
        get() = value.substring(10..21).uppercase()

    val sensorServiceId: String
        get() = value.substring(42..47).uppercase()

    val sensorId: String
        get() = value.substring(30..41).uppercase()

    val rssi: Int
        get() = value.substring(26..27).toInt(16)

    val nfpkt: Int
        get() = value.substring(22..25).toInt(16)

    private val globalPacingGroup: Int
        get() = value.substring(28..29).toInt(16) shr 4

    private val sensorFlags: Byte
        get() = value.substring(48..49).toByte(16)

    private val apiVersion: Int
        get() = (sensorFlags and bitMask(11110000L).toByte()).toInt() shr 4

    val isSensor: Boolean
        get() = (sensorFlags and bitMask(1).toByte()) > 0
    val isEmbedded: Boolean
        get() = (sensorFlags and bitMask(10).toByte()) > 0
    val isScrambled: Boolean
        get() = (sensorFlags and bitMask(100).toByte()) > 0

    override val deviceMac: String
        get() = scanResult.device.address
    override val scanRssi: Int
        get() = scanResult.rssi

}

data class CombinedSiPacket(
    override var value: String,
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMetaPacket() {

    override fun equals(other: Any?): Boolean = when(other) {
        is CombinedSiPacket -> super.equals(other) && sourceAddress == other.sourceAddress
        else -> false
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override val deviceMac: String
        get() = scanResult.device.address

    override val scanRssi: Int
        get() = scanResult.rssi

    internal val sourceAddress: Long
        get() = aliasBridgeId.toLong(16)

    val aliasBridgeId: String
        get() = scanResult.device.address.replace(":", "")

}

data class ControlPacket(
    override var value: String,
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis(),
) : PacketAbstract() {
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    var msgType: String
        get() = value.substring(10..11)
        set(value) = with(value) {
            if (length != 2)
                throw Exception("Invalid length for MsgType")

            value.replaceRange(10..11, value)
        }
    var brgId: String
        get() = value.substring(12..13)
        set(value) = with(value) {
            if (length != 2)
                throw Exception("Invalid length for brgId")

            value.replaceRange(12..13, value)
        }
    var seqId: String
        get() = value.substring(14..15)
        set(value) = with(value) {
            if (length != 2)
                throw Exception("Invalid length for seqId")

            value.replaceRange(14..15, value)
        }
    var destMac: String
        get() = value.substring(16..27)
        set(value) = with(value) {
            if (length != 12)
                throw Exception("Invalid length for destMac")

            value.replaceRange(16..27, value)
        }
    var srcMac: String
        get() = value.substring(28..39)
        set(value) = with(value) {
            if (length != 12)
                throw Exception("Invalid length for srcMac")

            value.replaceRange(28..39, value)
        }
    var rssi: String
        get() = value.substring(40..41)
        set(value) = with(value) {
            if (length != 2)
                throw Exception("Invalid length for rssi")

            value.replaceRange(40..41, value)
        }
    var cycleTime: String
        get() = value.substring(42..43)
        set(value) = with(value) {
            if (length != 2)
                throw Exception("Invalid length for cycleTime")

            value.replaceRange(42..43, value)
        }
    var txTime: String
        get() = value.substring(44..45)
        set(value) = with(value) {
            if (length != 2)
                throw Exception("Invalid length for txTime")

            value.replaceRange(44..45, value)
        }
    var energyPattern: String
        get() = value.substring(46..47)
        set(value) = with(value) {
            if (length != 2)
                throw Exception("Invalid length for energyPattern")

            value.replaceRange(46..47, value)
        }
    var outputPower: String
        get() = value.substring(48..49)
        set(value) = with(value) {
            if (length != 2)
                throw Exception("Invalid length for outputPower")

            value.replaceRange(48..49, value)
        }
    var pacerInterval: String
        get() = value.substring(50..51)
        set(value) = with(value) {
            if (length != 2)
                throw Exception("Invalid length for pacerInterval")

            value.replaceRange(50..51, value)
        }
    override val deviceMac: String
        get() = scanResult.device.address
    override val scanRssi: Int
        get() = scanResult.rssi

    override fun describeData() = Reporter.log(
        "CPack: type:${msgType.toUInt(16)} brId: [$brgId] sqId: [$seqId] dstMAC: [$destMac] val:[$value]",
        beaconLogTag
    )
}

//==============================================================================================
// *** Bridge ACK packet ***
//==============================================================================================

// region [Bridge ACK packet]

data class BridgeACKPacket(
    override var value: String,
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis(),
) : PacketAbstract() {

    override fun equals(other: Any?): Boolean = when (other) {
        is BridgeACKPacket -> {
            other.value.contentEquals(value)
        }

        else -> false
    }

    override fun hashCode(): Int = value.hashCode()

    override val deviceMac: String
        get() = scanResult.device.address
    override val scanRssi: Int
        get() = scanResult.rssi

    override fun describeData() = Reporter.log(
        "BridgeACKPacket(" +
                " deviceMac='$deviceMac'," +
                " scanRssi=$scanRssi)",
        beaconLogTag
    )

}

// endregion

//==============================================================================================
// *** Bridge HB V5 packet ***
//==============================================================================================

// region [Bridge HB V5 packet]

data class BridgeHbPacketV5(
    override var value: String,
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis(),
) : BridgeHbPacketAbstract() {
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override val apiVersion: UInt
        get() = value.substring(12..13).toUInt(16)
    val seqId: UInt
        get() = value.substring(14..15).toUInt(16)
    val brgMac: String
        get() = value.substring(16..27)
    val nonWltRxPktsCtr: UInt
        get() = value.substring(28..33).toUInt(16)
    val badCrcPktsCtr: UInt
        get() = value.substring(34..39).toUInt(16)
    val wltRxPktsCtr: UInt
        get() = value.substring(40..45).toUInt(16)
    val wltTxPktsCtr: UInt
        get() = value.substring(46..49).toUInt(16)
    val tagsCtr: UInt
        get() = value.substring(50..53).toUInt(16)

    override fun srcMac(): String {
        return brgMac
    }

    override fun aliasDeviceId(): String =
        scanResult.device.address.replace(":", "")

    override val deviceMac: String
        get() = scanResult.device.address
    override val scanRssi: Int
        get() = scanResult.rssi

    override fun describeData() = Reporter.log(
        "BridgeHbPacketV5(" +
                "apiVersion=$apiVersion, " +
                "seqId=$seqId, " +
                "brgMac='$brgMac', " +
                "nonWltRxPktsCtr=$nonWltRxPktsCtr, " +
                "badCrcPktsCtr=$badCrcPktsCtr, " +
                "wltRxPktsCtr=$wltRxPktsCtr, " +
                "wltTxPktsCtr=$wltTxPktsCtr, " +
                "tagsCtr=$tagsCtr, " +
                "deviceMac='$deviceMac', " +
                "scanRssi=$scanRssi" +
                ")",
        beaconLogTag
    )

}

// endregion

//==============================================================================================
// *** Bridge HB packet ***
//==============================================================================================

// region [Bridge HB packet]

data class BridgeHbPacket(
    override var value: String,
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis(),
) : BridgeHbPacketAbstract() {

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override val apiVersion: UInt
        get() = 1u

    val seqId: UInt
        get() = value.substring(14..15).toUInt(16)
    val destMac: String
        get() = ""
    val srcMac: String
        get() = value.substring(28..39)
    val sentPktsCtr: UInt
        get() = value.substring(40..43).toUInt(16)
    val nonWiliotPktsCtr: UInt
        get() = value.substring(44..47).toUInt(16)
    val tagsCtr: UInt
        get() = value.substring(48..51).toUInt(16)

    override fun srcMac(): String {
        return srcMac
    }

    override fun aliasDeviceId(): String =
        scanResult.device.address.replace(":", "")

    override val deviceMac: String
        get() = scanResult.device.address
    override val scanRssi: Int
        get() = scanResult.rssi

    override fun describeData() = Reporter.log(
        "BridgeHbPacket(" +
                "seqId=$seqId," +
                " destMac='$destMac'," +
                " srcMac='$srcMac', " +
                "sentPktsCtr=$sentPktsCtr, " +
                "nonWiliotPktsCtr=$nonWiliotPktsCtr," +
                " tagsCtr=$tagsCtr," +
                " deviceMac='$deviceMac'," +
                " scanRssi=$scanRssi)",
        beaconLogTag
    )

}

// endregion

//==============================================================================================
// *** Bridge HB Packet Abstract ***
//==============================================================================================

// region [Bridge HB Packet Base]

abstract class BridgeHbPacketAbstract : PacketAbstract() {
    abstract fun srcMac(): String
    abstract fun aliasDeviceId(): String
    abstract val apiVersion: UInt
    override fun equals(other: Any?): Boolean {
        return when (other) {
            is BridgeHbPacketAbstract -> value.contentEquals(other.value)
            else -> false
        }
    }

    override fun hashCode(): Int = value.hashCode()

}

// endregion

//==============================================================================================
// *** MelModulePacket ***
//==============================================================================================

// region [MelModulePacket]

data class MelModulePacket(
    override var value: String,
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis()
) : PacketAbstract() {

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is MelModulePacket -> other.value.contentEquals(value)
            else -> false
        }
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    val brgMac: String
        get() = value.substring(16..27)

    val apiVersion: UInt
        get() = value.substring(12..13).toUInt(16)

    val bridgeId: String
        get() = brgMac

    fun aliasDeviceId(): String =
        scanResult.device.address.replace(":", "")

    override val deviceMac: String
        get() = scanResult.device.address
    override val scanRssi: Int
        get() = scanResult.rssi
}

// endregion

//==============================================================================================
// *** BridgeEarlyPacket ***
//==============================================================================================

// region [BridgeEarlyPacket]

data class BridgeEarlyPacket(
    override var value: String = "",
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis(),
) : PacketAbstract() {

    @SuppressLint("MissingPermission")
    override fun equals(other: Any?): Boolean {
        if (other !is BridgeEarlyPacket) return false
        return other.scanResult.device.name == scanResult.device.name
    }

    @SuppressLint("MissingPermission")
    override fun hashCode(): Int {
        return scanResult.device.name.hashCode()
    }

    val id: String?
        @SuppressLint("MissingPermission")
        get() = try {
            scanResult.device.name!!.split("_")[1].takeIf { it.length == 12 }
        } catch (ex: Exception) {
            null
        }

    override val deviceMac: String
        get() = scanResult.device.address
    override val scanRssi: Int
        get() = scanResult.rssi

    override fun describeData() = Reporter.log(
        "BridgeEarlyPacket(" +
                "deviceMac='$deviceMac'," +
                " scanRssi=$scanRssi)",
        beaconLogTag
    )

}

// endregion

//==============================================================================================
// *** BridgePacketV5 ***
//==============================================================================================

// region [BridgePacketV5]

data class BridgeConfigPacketV5(
    override var value: String,
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis(),
) : BridgePacketAbstract(value, scanResult, timestamp) {

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override fun aliasDeviceId(): String =
        scanResult.device.address.replace(":", "")

    override val msgType: UInt
        get() = value.substring(10..11).toUInt(16)
    override val brgId: String
        get() = ""
    val apiVersion: UInt
        get() = value.substring(12..13).toUInt(16)
    override val destMac: String
        get() = ""
    override val seqId: UInt
        get() = value.substring(14..15).toUInt(16)
    val globalPacing: UInt
        get() = ((value.substring(16..17).toUInt(16) and bitMask(10000000)) shr 7)
    val txProbability: UInt
        get() = 30u + 10u * ((value.substring(16..17).toUInt(16) and bitMask(1110000)) shr 4)
    val statFreq: UInt
        get() = (value.substring(16..17).toUInt(16) and bitMask(1111))
    val outputPowerSub1G: UInt
        get() = (value.substring(18..19).toUInt(16) and bitMask(1111))
    val txTimeSub1G: UInt
        get() = ((value.substring(20..21).toUInt(16) and bitMask(11110000)) shr 4)
    val sub1GFrequency: UInt
        get() = (value.substring(20..21).toUInt(16) and bitMask(1111))
    val bl_version: UInt
        get() = value.substring(22..23).toUInt(16)
    val boardType: UInt
        get() = value.substring(24..25).toUInt(16)
    override val srcMac: String
        get() = value.substring(28..39)
    override val majorSWVer: String
        get() = value.substring(40..41).toUInt(16).toString()
    override val minorSWVer: String
        get() = value.substring(42..43).toUInt(16).toString()
    override val patchSWVer: String
        get() = value.substring(44..45).toUInt(16).toString()
    val cycleTime: UInt
        get() = value.substring(46..47).toUInt(16)
    val txTime2_4G: UInt
        get() = value.substring(48..49).toUInt(16)
    val energyPattern: UInt
        get() = value.substring(50..51).toUInt(16)
    val outputPower2_4Ghz: UInt
        get() = value.substring(52..53).toUInt(16)
    val pacerInterval: Long
        get() = value.substring(54..57).toLong(16)

    override fun version(): String {
        return "$majorSWVer.$minorSWVer.$patchSWVer"
    }

    override fun toBridgeConfiguration(): BridgeConfiguration = BridgeConfiguration(
        txProbability = txProbability,
        energyPattern = energyPattern,
        globalPacingEnabled = globalPacing,
        txPeriodMs = txTime2_4G,
        _2_4GhzOutputPower = outputPower2_4Ghz,
        rxTxPeriodMs = cycleTime,
        pacerInterval = pacerInterval,
        sub1GhzOutputPower = Sub1gOutputPower.parse(outputPowerSub1G),
        sub1GhzFrequency = Sub1gFrequency.parse(sub1GFrequency)
    )

    override fun extractBoardTypeOrNull(): UInt {
        return boardType
    }

    override fun describeData() = Reporter.log(
        "BridgePacketV5(" +
                "value='$value', " +
                "scanResult=$scanResult, " +
                "timestamp=$timestamp, " +
                "msgType='$msgType', " +
                "boardType='$boardType', " +
                "seqId=$seqId, " +
                "srcMac='$srcMac', " +
                "majorSWVer='$majorSWVer', " +
                "minorSWVer='$minorSWVer', " +
                "patchSWVer='$patchSWVer', " +
                "cycleTime=$cycleTime, " +
                "energyPattern=$energyPattern, " +
                "outputPower2_4Ghz=$outputPower2_4Ghz, " +
                "pacerInterval=$pacerInterval, " +
                "deviceMac='$deviceMac', " +
                "scanRssi=$scanRssi)",
        beaconLogTag
    )

}

// endregion

// ==============================================================================================
// *** BridgePacketV3 ***
// ==============================================================================================

// region [BridgePacketV3]

data class BridgeConfigPacketV3(
    override var value: String,
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis(),
) : BridgePacketAbstract(value, scanResult, timestamp) {

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override fun aliasDeviceId(): String =
        scanResult.device.address.replace(":", "")

    override val msgType: UInt
        get() = value.substring(10..11).toUInt(16)
    override val brgId: String
        get() = ""
    val boardType: UInt
        get() = value.substring(12..13).toUInt(16)
    override val seqId: UInt
        get() = value.substring(14..15).toUInt(16)
    override val destMac: String
        get() = ""
    val globalPacing: UInt
        get() = ((value.substring(16..17).toUInt(16) and bitMask(10000000)) shr 7)
    val txProbability: UInt
        get() = 30u + 10u * ((value.substring(16..17).toUInt(16) and bitMask(1110000)) shr 4)
    val statFreq: UInt
        get() = (value.substring(16..17).toUInt(16) and bitMask(1111))
    val outputPowerSub1G: UInt
        get() = (value.substring(18..19).toUInt(16) and bitMask(1111))
    val txTimeSub1G: UInt
        get() = ((value.substring(20..21).toUInt(16) and bitMask(11110000)) shr 4)
    val sub1GFrequency: UInt
        get() = (value.substring(20..21).toUInt(16) and bitMask(1111))
    val bl_version: UInt
        get() = value.substring(22..23).toUInt(16)
    override val srcMac: String
        get() = value.substring(28..39)
    override val majorSWVer: String
        get() = value.substring(40..41).toUInt(16).toString()
    override val minorSWVer: String
        get() = value.substring(42..43).toUInt(16).toString()
    override val patchSWVer: String
        get() = value.substring(44..45).toUInt(16).toString()
    val cycleTime: UInt
        get() = value.substring(46..47).toUInt(16)
    val txTime2_4G: UInt
        get() = value.substring(48..49).toUInt(16)
    val energyPattern: UInt
        get() = value.substring(50..51).toUInt(16)
    val outputPower2_4Ghz: UInt
        get() = value.substring(52..53).toUInt(16)
    val pacerInterval: Long
        get() = value.substring(54..57).toLong(16)

    override fun describeData() = Reporter.log(
        "BridgePacketV3(" +
                "value='$value', " +
                "scanResult=$scanResult, " +
                "timestamp=$timestamp, " +
                "msgType='$msgType', " +
                "boardType='$boardType', " +
                "seqId=$seqId, " +
                "srcMac='$srcMac', " +
                "majorSWVer='$majorSWVer', " +
                "minorSWVer='$minorSWVer', " +
                "patchSWVer='$patchSWVer', " +
                "cycleTime=$cycleTime, " +
                "energyPattern=$energyPattern, " +
                "outputPower2_4Ghz=$outputPower2_4Ghz, " +
                "pacerInterval=$pacerInterval, " +
                "deviceMac='$deviceMac', " +
                "scanRssi=$scanRssi)",
        beaconLogTag
    )

    override fun toBridgeConfiguration(): BridgeConfiguration = BridgeConfiguration(
        txProbability = txProbability,
        energyPattern = energyPattern,
        globalPacingEnabled = globalPacing,
        txPeriodMs = txTime2_4G,
        _2_4GhzOutputPower = outputPower2_4Ghz,
        rxTxPeriodMs = cycleTime,
        pacerInterval = pacerInterval,
        sub1GhzOutputPower = Sub1gOutputPower.parse(outputPowerSub1G),
        sub1GhzFrequency = Sub1gFrequency.parse(sub1GFrequency)
    )

    override fun extractBoardTypeOrNull(): UInt {
        return boardType
    }

    override fun version(): String {
        return "$majorSWVer.$minorSWVer.$patchSWVer"
    }
}

// endregion

//==============================================================================================
// *** BridgePacketV2 ***
//==============================================================================================

// region [BridgePacketV2]

data class BridgeConfigPacketV2(
    override var value: String,
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis(),
) : BridgePacketAbstract(value, scanResult, timestamp) {
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override fun aliasDeviceId(): String =
        scanResult.device.address.replace(":", "")

    override val msgType: UInt
        get() = value.substring(10..11).toUInt(16)
    override val brgId: String
        get() = value.substring(12..13)
    override val seqId: UInt
        get() = value.substring(14..15).toUInt(16)
    override val destMac: String
        get() = value.substring(16..27)
    override val srcMac: String
        get() = value.substring(28..39)
    override val majorSWVer: String
        get() = value.substring(40..40)
    override val minorSWVer: String
        get() = value.substring(41..41)
    override val patchSWVer: String
        get() = value.substring(42..42)

    val singleBand: UInt
        get() = (value.substring(44..45).toUInt(16) and bitMask(1))
    val txProbability: UInt
        get() = ((value.substring(44..45).toUInt(16) and bitMask(1111110)) shr 1)
    val cycleTime: UInt
        get() = value.substring(46..47).toUInt(16)
    val txTime: UInt
        get() = value.substring(48..49).toUInt(16)
    val energyPattern: UInt
        get() = value.substring(50..51).toUInt(16)
    val outputPower: UInt
        get() = value.substring(52..53).toUInt(16)
    val pacerInterval: Long
        get() = value.substring(54..57).toLong(16)

    override fun describeData() = Reporter.log(
        "BPackV2: " +
                "type:${msgType} " +
                "brId: [$brgId] sqId: [$seqId] " +
                "dstMAC: [$destMac] srcMac: [$srcMac] " +
                "v: [$majorSWVer.$minorSWVer.$patchSWVer] " +
                "band: [$singleBand] " +
                "cycleTime: [$cycleTime] " +
                "txTime: [$txTime] " +
                "engPattern: [$energyPattern] " +
                "outPower: [$outputPower] " +
                "pacerInterval: [$pacerInterval] " +
                "val: [$value]",
        beaconLogTag
    )

    override fun toBridgeConfiguration(): BridgeConfiguration = BridgeConfiguration(
        txProbability = txProbability,
        energyPattern = energyPattern,
        txPeriodMs = txTime,
        _2_4GhzOutputPower = outputPower,
        rxTxPeriodMs = cycleTime,
        pacerInterval = pacerInterval
    )

    override fun extractBoardTypeOrNull(): UInt? = null

    override fun version(): String {
        return "$majorSWVer.$minorSWVer.$patchSWVer"
    }
}

// endregion

abstract class BridgePacketAbstract(
    override var value: String,
    private val scanResult: ScanResultInternal,
    override val timestamp: Long = System.currentTimeMillis(),
) : PacketAbstract() {

    abstract val msgType: UInt
    abstract val brgId: String
    abstract val seqId: UInt
    abstract val destMac: String
    abstract val srcMac: String
    abstract val majorSWVer: String
    abstract val minorSWVer: String
    abstract val patchSWVer: String

    abstract fun aliasDeviceId(): String

    override val deviceMac: String
        get() = scanResult.device.address
    override val scanRssi: Int
        get() = scanResult.rssi

    override fun describeData() = Reporter.log(
        "BPack: " +
                "type:${msgType} " +
                "brId: [$brgId] sqId: [$seqId] " +
                "dstMAC: [$destMac] srcMac: [$srcMac] " +
                "v: [$majorSWVer.$minorSWVer.$patchSWVer] " +
                "val:[$value]",
        beaconLogTag
    )

    abstract fun toBridgeConfiguration(): BridgeConfiguration?

    abstract fun extractBoardTypeOrNull(): UInt?

    abstract fun version(): String

    override fun equals(other: Any?): Boolean = when (other) {
        is BridgePacketAbstract -> value.contentEquals(other.value)
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
}

abstract class PacketAbstract : Packet {
    override fun equals(other: Any?): Boolean {
        return when (other) {
            is PacketAbstract -> value == other.value
            else -> false
        }
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

interface Packet {

    companion object {
        private val String.pGroupIdMajor
            get() = substring(8..9).toUInt(16)

        private val String.pGroupIdMinor
            get() = substring(4..7).toUInt(16)

        private val String.msgType
            get() = (substring(10..11).toUInt(16) and bitMask(1111))

        private val String.moduleType
            get() = (substring(10..11).toUInt(16) and bitMask(11110000))

        private val String.isControlPacket: Boolean
            get() = GROUP_ID_CONTROL == pGroupIdMajor

        private val String.isMetaPacket: Boolean
            get() = GROUP_ID_META == pGroupIdMajor

        private val String.isSensorPacket: Boolean
            get() = startsWith(DataPacketType.SENSOR_DATA.prefix)

        private val String.isSensorMetaPacket: Boolean
            get() = GROUP_ID_SENSOR == pGroupIdMajor

        private val String.isCombinedSiPacket: Boolean
            get() = (GROUP_ID_COMBINED_SI == pGroupIdMajor.and(GROUP_ID_COMBINED_SI))
                    || (GROUP_ID_COMBINED_SI_2 == pGroupIdMajor.and(GROUP_ID_COMBINED_SI_2))
                    || (GROUP_ID_COMBINED_SI_3 == pGroupIdMajor.and(GROUP_ID_COMBINED_SI_3))

        private val String.isBridgeCfgPacket: Boolean
            get() = GROUP_ID_BRIDGE == pGroupIdMajor && (
                    msgType == BrgMgmtMsgType.CFG_GET.value
                            || msgType == BrgMgmtMsgType.CFG_SET.value
                            || msgType == BrgMgmtMsgType.ASSIGN_ID.value
                    ) && (isV3 || isV5plus)

        private val String.isMelModulePacket: Boolean
            get() = GROUP_ID_BRIDGE == pGroupIdMajor && (
                    msgType == BrgMgmtMsgType.CFG_GET.value
                            || msgType == BrgMgmtMsgType.CFG_SET.value
                            || msgType == BrgMgmtMsgType.ASSIGN_ID.value
                    ) && moduleType > 0u

        private val String.isBridgeACK: Boolean
            get() = GROUP_ID_BRIDGE == pGroupIdMajor && (
                    msgType == BrgMgmtMsgType.LED_BLINK.value
                            || msgType == BrgMgmtMsgType.BRG_ACTION.value
                    )

        private val String.isV3: Boolean
            get() = substring(40..41).toUInt(16).toInt().equals(3) && isV5plus.not()

        private val String.isV5plus: Boolean
            get() = substring(12..13).toUInt(16) >= 5u && moduleType == 0u

        private val String.isHBMessage: Boolean
            get() = GROUP_ID_BRIDGE == pGroupIdMajor && msgType == BrgMgmtMsgType.HB.value

        private val String.isHbV1: Boolean
            get() = isHBMessage && isHbV5.not()

        private val String.isHbV5: Boolean
            get() = isHBMessage && substring(12..13).toUInt(16) >= 5u

        private val ScanResultInternal.isBridgeEarlyPacket: Boolean
            @SuppressLint("MissingPermission")
            get() {
                if (isConnectable.not()) return false
                val prefixMatches = device?.name?.startsWith("WLT") == true
                val lengthMatches = try {
                    device.name!!.split("_")[1].length == 12
                } catch (ex: Exception) {
                    false
                }
                return prefixMatches && lengthMatches
            }

        private fun ScanResultInternal.toSensorBle5PacketOrNull(): String? {
            try {
                val normalized = scanRecord?.raw?.uppercase() ?: return null
                var index = 0
                var endIndex = 0

                while (index + 2 <= normalized.length) {
                    val lengthHex = normalized.substring(index, index + 2)
                    val length = lengthHex.toIntOrNull(16) ?: return null
                    if (length == 0) break

                    val chunkTotalLength = (1 + length) * 2 // length + type + payload, in hex chars
                    if (index + chunkTotalLength > normalized.length) break

                    endIndex = index + chunkTotalLength
                    index = endIndex
                }

                return if (endIndex > 0) normalized.substring(4, endIndex).also {
                    if (BuildConfig.DEBUG) {
                        Reporter.log("Sensor BLE 5 packet: $it", beaconLogTag)
                    }
                } else null
            } catch (ex: Exception) {
                return null
            }
        }

        private const val DATA_PACKET_LEN_CHARS = 58
        private val GROUP_ID_META: UInt = "ec".toUInt(16)
        private val GROUP_ID_CONTROL: UInt = "ed".toUInt(16)
        private val GROUP_ID_BRIDGE: UInt = "ee".toUInt(16)
        private val GROUP_ID_SENSOR: UInt = "eb".toUInt(16)
        private val GROUP_ID_COMBINED_SI: UInt = "3f".toUInt(16)
        private val GROUP_ID_COMBINED_SI_2: UInt = "3d".toUInt(16)
        private val GROUP_ID_COMBINED_SI_3: UInt = "3c".toUInt(16)

        fun from(data: String, scanRecord: ScanResultInternal): PacketAbstract? {
            if (scanRecord.isBridgeEarlyPacket) {
                return BridgeEarlyPacket(scanResult = scanRecord)
            }

            var finalData = data

            if (data.length != DATA_PACKET_LEN_CHARS) {
                // Maybe Sensor BLE 5?
                scanRecord.toSensorBle5PacketOrNull()?.let {
                    finalData = it
                } ?: run {
                    Reporter.exception(
                        message = "Packet length mismatch",
                        exception = Exception("Packet length mismatch"),
                        where = "Packet/BeaconWiliot"
                    )
                    return null
                }
            }

            return when {
                finalData.isSensorPacket -> {
                    when {
                        finalData.isSensorMetaPacket -> ExternalSensorPacket(finalData, scanRecord)
                        else -> DataPacket(finalData, scanRecord)
                    }
                }
                finalData.isCombinedSiPacket -> CombinedSiPacket(finalData, scanRecord)
                finalData.isSensorMetaPacket -> ExternalSensorPacket(finalData, scanRecord) // should always execute BEFORE 'isMetaPacket'
                finalData.isMetaPacket -> MetaPacket(finalData, scanRecord)
                finalData.isControlPacket -> ControlPacket(finalData, scanRecord)
                finalData.isBridgeCfgPacket -> with(finalData) {
                    when {
                        isV5plus -> BridgeConfigPacketV5(finalData, scanRecord)
                        isV3 -> BridgeConfigPacketV3(finalData, scanRecord)
                        else -> null
                    }
                }

                data.isMelModulePacket -> MelModulePacket(finalData, scanRecord)

                data.isBridgeACK -> BridgeACKPacket(finalData, scanRecord)
                data.isHBMessage -> with(finalData) {
                    when {
                        isHbV5 -> BridgeHbPacketV5(this, scanRecord)
                        else -> BridgeHbPacket(this, scanRecord)
                    }
                }

                else -> DataPacket(finalData, scanRecord)
            }
        }
    }

    val value: String
    val timestamp: Long
    val deviceMac: String
    val scanRssi: Int
    val pSId: String
        get() = value.substring(0..3)
    val pGroupId: String
        get() = value.substring(4..9)
    val pNonce: String
        get() = value.substring(10..17)
    val pEpId: String
        get() = value.substring(18..29)
    val pMic: String
        get() = value.substring(30..41)
    val pTel1: String
        get() = value.substring(42..49)
    val pTel2: String
        get() = value.substring(50..57)

    fun describeData() {
        Reporter.log(
            "\nBData: sID[$pSId] gID[$pGroupId] pN[$pNonce] pE[$pEpId] pM[$pMic] pT[$pTel1 $pTel2]\n",
            beaconLogTag
        )
    }

}

enum class BrgMgmtMsgType(val value: UInt) {
    ASSIGN_ID(1u),
    HB(2u),
    REBOOT(3u),
    LED_BLINK(4u),
    CFG_SET(5u),
    CFG_GET(6u),
    BRG_ACTION(7u)
}

val ScanResultInternal.wiliotBridgeEarlyPacket: PacketAbstract?
    get() = scanRecord?.run {
        val hasBridgeDeviceName = deviceName?.takeIf {
            it.startsWith("WLT") && try {
                it.split("_")[1].length == 12
            } catch (ex: Exception) {
                false
            }
        } != null
        if (hasBridgeDeviceName) {
            Packet.from("brgEarlyPckt", this@wiliotBridgeEarlyPacket)
        } else null
    }

val ScanResultInternal.wiliotManufacturerData: PacketAbstract?
    get() = scanRecord?.run {
        with(manufacturerSpecificData) {
            this?.get(BeaconWiliot.MANUFACTURED_UUID)?.run {
                Packet.from(
                    DataPacketType.SHORT_MD.prefix + toHexString(),
                    this@wiliotManufacturerData
                )
            }
        }
    }

val ScanResultInternal.wiliotServiceData: PacketAbstract?
    get() = scanRecord?.run {
        with(serviceData) {
            when {
                this?.contains(BeaconWiliot.serviceUuid) ?: false -> {
                    this?.get(BeaconWiliot.serviceUuid)?.run {
                        Packet.from(
                            DataPacketType.DIRECT.prefix + toHexString(),
                            this@wiliotServiceData
                        )
                    }
                }

                this?.contains(BeaconWiliot.serviceUuid2) ?: false -> {
                    this?.get(BeaconWiliot.serviceUuid2)?.run {
                        Packet.from(
                            DataPacketType.RETRANSMITTED.prefix + toHexString(),
                            this@wiliotServiceData
                        )
                    }
                }

                this?.contains(BeaconWiliot.sensorServiceUuid) ?: false -> {
                    this?.get(BeaconWiliot.sensorServiceUuid)?.run {
                        Packet.from(
                            DataPacketType.SENSOR_DATA.prefix + toHexString(),
                            this@wiliotServiceData
                        )
                    }
                }

                this?.contains(BeaconWiliot.serviceUuidD2p2) ?: false -> {
                    this?.get(BeaconWiliot.serviceUuidD2p2)?.run {
                        Packet.from(
                            DataPacketType.SHORT_SD.prefix + toHexString(),
                            this@wiliotServiceData
                        )
                    }
                }

                else -> null
            }
        }
    }
