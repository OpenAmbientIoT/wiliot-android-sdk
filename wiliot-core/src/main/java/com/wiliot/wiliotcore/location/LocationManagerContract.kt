package com.wiliot.wiliotcore.location

import android.content.Context
import android.location.Location

interface LocationManagerContract {

    /**
     * Used to start observing location updates. Called by Upstream at startup
     */
    fun startObserveLocation(context: Context)

    /**
     * Used to stop observing location updates. Called by Upstream when SDK (GW Mode) turning off
     */
    fun stopLocationUpdates(context: Context?)

    /**
     * Used by SDK components to send last obtained location along with Packets data
     */
    fun getLastLocation(): Location?

}