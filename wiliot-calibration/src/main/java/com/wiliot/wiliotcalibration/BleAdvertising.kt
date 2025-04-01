package com.wiliot.wiliotcalibration

import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.util.Log
import com.wiliot.wiliotcore.model.BeaconWiliot
import com.wiliot.wiliotcore.utils.WiliotReporter
import com.wiliot.wiliotcore.utils.bluetoothManager
import com.wiliot.wiliotcore.utils.logTag

/**
 * Tool for periodical BLE packets advertising. Used to calibrate Pixel's transmission frequency.
 */
object BleAdvertising {

    private var mBluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private val data: AdvertiseData = buildAdvertiseData()
    private val settings = buildAdvertiseSettings()
    private val logTag = this.logTag()
    private val callback: SampleAdvertiseCallback = SampleAdvertiseCallback()

    /**
     * Get references to system Bluetooth objects if we don't have them already.
     */
    private fun initialize(context: Context) {
        if (mBluetoothLeAdvertiser == null) {
            val mBluetoothManager = context.bluetoothManager
            val mBluetoothAdapter = mBluetoothManager.adapter
            if (mBluetoothAdapter != null) {
                mBluetoothLeAdvertiser = mBluetoothAdapter.bluetoothLeAdvertiser
            } else {
                Log.e(logTag, "BT is null")
            }
        }
    }

    /**
     * This function performs initialization of [BluetoothLeAdvertiser] and starts advertising.
     * Basically this function used in Upstream module and in common case should not be used manually.
     *
     * Note, that Bluetooth permission should be granted before accessing this function.
     */
    @SuppressLint("MissingPermission")
    fun startAdvertising(context: Context) {
        initialize(context)
        val multipleAdvertisementSupported =
            context.bluetoothManager.adapter?.isMultipleAdvertisementSupported ?: false
        WiliotReporter.log("Support BLE Advertising: $multipleAdvertisementSupported", logTag)
        mBluetoothLeAdvertiser?.apply { this.startAdvertising(settings, data, callback) }
    }

    /**
     * Returns an AdvertiseSettings object set to use low power (to help preserve battery life)
     * and disable the built-in timeout since this code uses its own timeout runnable.
     */
    private fun buildAdvertiseSettings(): AdvertiseSettings {
        val settingsBuilder = AdvertiseSettings.Builder()
        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        settingsBuilder.setTimeout(0)
        settingsBuilder.setConnectable(false)
        return settingsBuilder.build()
    }

    /**
     * Returns an AdvertiseData object which includes the Service UUID and Device Name.
     */
    private fun buildAdvertiseData(): AdvertiseData {

        /*
         * Note: There is a strict limit of 31 Bytes on packets sent over BLE Advertisements.
         * This includes everything put into AdvertiseData including UUIDs, device info, &
         * arbitrary service or manufacturer data.
         * Attempting to send packets over this limit will result in a failure with error code
         * BleAdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE. Catch this error in the
         * onStartFailure() method of an BleAdvertiseCallback implementation.
         */

        val advertisingData = AdvertiseData.Builder()
        advertisingData.addServiceData(BeaconWiliot.manufactureUuid, ByteArray(27))
        advertisingData.setIncludeDeviceName(false)
        return advertisingData.build()
    }

    /**
     * Force stop advertising process.
     */
    @SuppressLint("MissingPermission")
    fun stop() {
        WiliotReporter.log("Service: Stopping Advertising", logTag)
        mBluetoothLeAdvertiser?.stopAdvertising(callback)
    }

    /**
     * Custom callback after Advertising succeeds or fails to start. Broadcasts the error code
     * in an Intent to be picked up by system and stops this Service.
     */
    private class SampleAdvertiseCallback : AdvertiseCallback() {

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            WiliotReporter.log("Advertising failed: $errorCode", logTag)
            stop()
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            WiliotReporter.log("Advertising successfully started", logTag)
        }
    }
}
