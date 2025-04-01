package com.wiliot.wiliotcore.model

interface BeaconDataShared {
    var data: String
    var signal: SignalStrength
    var amount: Int
    var deviceMAC: String
    var rssi: Int?
    var timestamp: Long
    var location: Location?

    fun isEncrypted(): Boolean
    fun maybeUpdateLocation(location: Location?) {
        location?.let { loc ->
            if (loc.lat != 0.0 && loc.lng != 0.0)
                this.location = location
        }
    }

    fun compareTo(t: BeaconDataShared): Int

    fun updateUsingData(data: BeaconDataShared)
}