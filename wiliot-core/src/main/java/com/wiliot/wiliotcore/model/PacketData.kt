package com.wiliot.wiliotcore.model

import android.os.Parcel
import android.os.Parcelable
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag

abstract class BasePacketData : BeaconDataShared

data class PacketData(
    var packet: Packet,
    override var amount: Int = 1
) :
    Comparable<PacketData>,
    IResolveInfo,
    BasePacketData() {

    override var name: String = packet.pEpId
    override var ownerId: String? = null
    override var labels: List<String>? = null
    override var resolveTimestamp: Long = -1L
    override var waitingForUpdate: Boolean? = null
    override var asset: Asset? = null

    override var data: String
        get() = this.packet.value
        set(_) {}
    override var signal: SignalStrength
        get() {
            return with(packet.scanRssi) {
                when {
                    this > -70 -> SignalStrength.EXCELLENT
                    this <= -70 && this >= -85 -> SignalStrength.GOOD
                    this <= -86 && this >= -100 -> SignalStrength.FAIR
                    this <= -101 && this >= -110 -> SignalStrength.POOR
                    this > -110 -> SignalStrength.NO_SIGNAL
                    else -> SignalStrength.NO_SIGNAL
                }
            }
        }
        set(_) {}

    override var deviceMAC: String
        get() = this.packet.deviceMac
        set(_) {}
    override var rssi: Int?
        get() = this.packet.scanRssi
        set(_) {}
    override var timestamp: Long
        get() = this.packet.timestamp
        set(_) {}
    override var location: Location? = Wiliot.locationManager.getLastLocation()?.run {
        Location(latitude, longitude)
    }
    val reversedTag: String
        get() = packet.pEpId.reversed()

    constructor(parcel: Parcel) : this(
        parcel.readParcelable(Packet::class.java.classLoader)!!,
        parcel.readInt()
    ) {
        name = parcel.readString().toString()
        ownerId = parcel.readString()
        labels = parcel.createStringArrayList()
    }

    override fun isEncrypted(): Boolean =
        0 != this.packet.pGroupId.toInt(16)

    override fun compareTo(t: BeaconDataShared): Int = when (t) {
        is PacketData -> compareTo(t)
        else -> throw Exception("Type mismatch")
    }

    fun updateResolve(resolve: IResolveInfo?) {
        if (!deviceMAC.contentEquals(resolve?.deviceMAC))
            return

        resolve?.name?.let {
            this.name = it
        }
        resolve?.resolveTimestamp?.let {
            this.resolveTimestamp = it
        }
        this.ownerId = resolve?.ownerId
        this.labels = resolve?.labels
    }

    override fun updateUsingData(
        data: BeaconDataShared
    ) {
        when (data) {
            is PacketData -> {
                this.packet = data.packet
                this.amount += 1
                maybeUpdateLocation(data.location)
            }
            else -> {
                Reporter.log(data.toString(), this@PacketData.logTag(), highlightError = true)
            }
        }
    }

    override fun compareTo(other: PacketData): Int =
        this.deviceMAC.compareTo(other.deviceMAC)

    companion object CREATOR : Parcelable.Creator<PacketData> {
        override fun createFromParcel(parcel: Parcel): PacketData {
            return PacketData(parcel)
        }

        override fun newArray(size: Int): Array<PacketData?> {
            return arrayOfNulls(size)
        }
    }

    val isStarterKitTag: Boolean
        get() = "devkit".contentEquals(this.ownerId)

    val belongsToMe: Boolean
        get() = Wiliot.configuration.ownerId?.contentEquals(this.ownerId) ?: false

    val isUnknown: Boolean
        get() = name.contentEquals(packet.pEpId) && this.ownerId.isNullOrBlank()

}