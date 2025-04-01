package com.wiliot.wiliotdfu

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.wiliot.wiliotcore.Wiliot
import no.nordicsemi.android.dfu.DfuBaseService

/**
 * Service for upgrade Bridge firmware
 */
class BridgeDfuService : DfuBaseService() {
    @Suppress("UNCHECKED_CAST")
    override fun getNotificationTarget(): Class<out Activity> =
        Class.forName(Wiliot.launcherActivity) as Class<out Activity>

    override fun isDebug(): Boolean = BuildConfig.DEBUG

    companion object {
        /**
         * Force stop service
         */
        fun abort(context: Context) {
            val abortIntent = Intent(BROADCAST_ACTION)
            abortIntent.putExtra(EXTRA_ACTION, ACTION_ABORT)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            flags = flags or PendingIntent.FLAG_IMMUTABLE
            val pendingAbortIntent = PendingIntent.getBroadcast(context, 1, abortIntent, flags)
            pendingAbortIntent.send()
        }
    }

}