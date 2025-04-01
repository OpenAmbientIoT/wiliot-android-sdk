package com.wiliot.wiliotupstream.utils

import android.content.ComponentCallbacks2.*
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import java.util.concurrent.TimeUnit

object MemoryClearanceUtil {

    private val logTag = logTag()

    sealed class Call {
        data class TrimCallback(val trimLevel: Int): Call()
        object Clearance: Call()
    }

    private val DEFAULT_CLEARANCE_INTERVAL = TimeUnit.HOURS.toMillis(1)
    private val MINIMUM_CLEARANCE_INTERVAL = TimeUnit.MINUTES.toMinutes(3)

    private var lastTrimCall: Long = System.currentTimeMillis()
    private var lastCalculatedClearance = DEFAULT_CLEARANCE_INTERVAL

    /**
     * @return time in millis to drop all outdated data from memory
     */
    fun getOptimisedExpirationLimit(
        call: Call,
        doOnCriticalLevel: (() -> Unit)? = null
    ): Long {
        Reporter.log("getOptimisedExpirationLimit(${call})", logTag)

        val newLimit: Long

        if (call is Call.TrimCallback) {
            var trimFactor = 1f
            lastTrimCall = System.currentTimeMillis()
            trimFactor -= getFactorCorrectionByTrimLevel(call.trimLevel)
            newLimit = (DEFAULT_CLEARANCE_INTERVAL * trimFactor).toLong().takeIf {
                it >= MINIMUM_CLEARANCE_INTERVAL
            } ?: MINIMUM_CLEARANCE_INTERVAL
            lastCalculatedClearance = newLimit
            if (call.trimLevel >= TRIM_MEMORY_RUNNING_LOW) {
                doOnCriticalLevel?.invoke()
            }
        } else {
            newLimit = if (System.currentTimeMillis() - lastTrimCall > lastCalculatedClearance) {
                DEFAULT_CLEARANCE_INTERVAL
            } else {
                lastCalculatedClearance
            }
        }

        Reporter.log("getOptimisedExpirationLimit -> $newLimit", logTag)
        return newLimit
    }

    private fun getFactorCorrectionByTrimLevel(level: Int): Float {
        val tLevel = if (level > TRIM_MEMORY_COMPLETE) TRIM_MEMORY_COMPLETE else level
        return (tLevel.toFloat() * 99f / TRIM_MEMORY_COMPLETE.toFloat()) * 0.01f
    }

}