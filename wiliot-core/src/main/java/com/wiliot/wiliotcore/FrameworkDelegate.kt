package com.wiliot.wiliotcore

import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag

abstract class FrameworkDelegate {

    protected val logTag = logTag()

    open fun onFail() {
        Reporter.log("onFail NOT IMPLEMENTED", logTag, highlightError = true)
    }

    open fun applicationVersion(): Int {
        return -1
    }

    open fun applicationVersionName(): String {
        return "unknown"
    }

    open fun applicationName(): String {
        return "Unknown Application"
    }

    open fun onNewSoftwareGatewayConfigurationApplied() {}

    companion object {
        fun String.extractSemanticVersion(): String {
            // Regular expression to match semantic versioning (MAJOR.MINOR.PATCH)
            val regex = "\\b(\\d+\\.\\d+\\.\\d+)\\b".toRegex()

            // Find the first match in the input string
            val matchResult = regex.find(this)

            // Return the matched version, or null if no version was found
            return matchResult?.value ?: this
        }
    }

}
