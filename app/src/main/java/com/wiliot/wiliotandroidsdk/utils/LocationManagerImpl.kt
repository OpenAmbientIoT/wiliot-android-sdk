package com.wiliot.wiliotandroidsdk.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.wiliot.wiliotcore.location.LocationManagerContract

object LocationManagerImpl : LocationManagerContract {

    private var lastLocation: Location? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            lastLocation = locationResult.lastLocation
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun startObserveLocation(context: Context) {
        if (!hasLocationPermission(context)) {
            Log.w(logTag(), "Location permissions not granted")
            return
        }

        startLocationUpdates(context)
    }

    override fun stopLocationUpdates(context: Context?) {
        context?.let {
            try {
                LocationServices.getFusedLocationProviderClient(it)
                    .removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                Log.e(logTag(), "Failed to stop location updates", e)
            }
        }
    }

    override fun getLastLocation(): Location? {
        return lastLocation
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates(context: Context) {
        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

        // Fetch the last known location
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
            lastLocation = location
        }

        // Start listening for location updates
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fineLocationPermission = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationPermission = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocationPermission || coarseLocationPermission
    }

    private fun logTag(): String = "LocationManagerImpl"

    private const val timeInterval: Long = 15000L // Update every 15 seconds

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, timeInterval).apply {
        setMinUpdateIntervalMillis(1000) // Minimum 1-second updates
        setWaitForAccurateLocation(true) // Wait for high-accuracy updates
    }.build()
}