package com.wiliot.wiliotcore.utils

import android.util.Log
import com.wiliot.wiliotcore.BuildConfig
import java.util.*

typealias Reporter = WiliotReporter

private class WiliotReporterException(
    override val message: String?,
    override val cause: Throwable?
) : Exception(message, cause)

object WiliotReporter {

    private val logTag = logTag()

    private val mReporterLogsEnabled = BuildConfig.DEBUG && BuildConfig.SDK_REPORTER_LOGS_PRINTING_ENABLED

    //==============================================================================================
    // *** Config ***
    //==============================================================================================

    // region [Config]

    private var mExternalReporters: MutableSet<ExternalReporter> = mutableSetOf()

    abstract class ExternalReporter constructor(
        private val id: String = UUID.randomUUID().toString()
    ) {

        /**
         * You can write your own implementation; e.g. logging to Firebase
         */
        abstract fun logReport(msg: String)

        /**
         * You can write your own implementation; e.g. logging to Firebase
         */
        abstract fun recordException(e: Exception)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ExternalReporter)
                return false
            else {
                if (other.id == this.id)
                    return true
            }
            return false
        }

        override fun hashCode() = Objects.hash(id)
    }

    fun addExternalReporter(externalReporter: ExternalReporter) {
        mExternalReporters.add(externalReporter)
    }

    fun removeAllExternalReporters() {
        mExternalReporters.clear()
    }

    // endregion

    //==============================================================================================
    // *** API ***
    //==============================================================================================

    // region [API]

    /**
     * App logic. e.g. making request, receiving response etc.
     */
    fun log(description: String, where: String, highlightError: Boolean = false) {
        val m = "[WiliotSDK][$where] $description"
        if (mReporterLogsEnabled) {
            if (highlightError) Log.e(logTag, m) else Log.d(logTag, m)
        }
        logToExternalReporter(m)
    }

    fun exception(message: String? = null, exception: Throwable?, where: String) {
        val m = "[WiliotSDK][$where] $message"
        if (mReporterLogsEnabled) Log.e(logTag, m, exception)
        logExceptionToExternalReporter(WiliotReporterException("Error occurred ($m)", exception))
    }

    // endregion

    //==============================================================================================
    // *** Domain ***
    //==============================================================================================

    // region [Domain]

    private fun logToExternalReporter(msg: String) {
        try {
            mExternalReporters.forEach { it.logReport(msg) }
        } catch (ex: ConcurrentModificationException) {
            Log.e(logTag, "Missed message: $msg", ex)
        }
    }

    private fun logExceptionToExternalReporter(e: Exception) {
        try {
            mExternalReporters.forEach { it.recordException(e) }
        } catch (ex: ConcurrentModificationException) {
            Log.e(logTag, "Missed exception: ${e.message}", ex)
        }
    }

    // endregion

}