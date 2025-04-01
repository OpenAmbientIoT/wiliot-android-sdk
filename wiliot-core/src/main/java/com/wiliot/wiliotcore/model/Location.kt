package com.wiliot.wiliotcore.model

import java.io.Serializable
import java.util.Locale

data class Location(var lat: Double, var lng: Double) : Serializable {
    init {
        this.lat = lat.apiPrecise()
        this.lng = lng.apiPrecise()
    }
}

private fun Double.apiPrecise() = String.format(Locale.ROOT, "%.5f", this).toDouble()