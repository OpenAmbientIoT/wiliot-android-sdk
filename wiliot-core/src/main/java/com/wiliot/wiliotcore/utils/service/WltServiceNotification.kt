package com.wiliot.wiliotcore.utils.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.wiliot.wiliotcore.R
import com.wiliot.wiliotcore.Wiliot

/**
 * Util to generate, keep and manage Wiliot service notifications that is required by Foreground Services
 */
object WltServiceNotification {

    const val WLT_MAIN_NOTIFICATION_ID = 100
    const val WLT_US_SERVICE_NOTIFICATION_ID = 101
    const val WLT_DS_SERVICE_NOTIFICATION_ID = 102

    private const val desiredChannelId = "wiliot_service"
    private const val channelName = "Wiliot Channel"

    private const val wltNotificationGroup = "WLT-SERVICES"

    private var sharedChannel: NotificationChannel? = null

    private var mainNotification: Notification? = null
    private var usNotification: Notification? = null
    private var dsNotification: Notification? = null
    private var mqttNotification: Notification? = null

    /**
     * Main [Notification] of Wiliot Services. It used as a parent (grouping) notification for all
     * other Wiliot service notifications
     */
    fun getWltMainNotification(contextWrapper: ContextWrapper): Notification? = with(contextWrapper) {
        if (mainNotification == null) {
            contextWrapper.createNotificationChannel()?.let { channelId ->
                val notificationBuilder = NotificationCompat.Builder(this, channelId)
                mainNotification = notificationBuilder.setOngoing(true)
                    .setGroup(wltNotificationGroup)
                    .setGroupSummary(true)
                    .setSmallIcon(R.drawable.wiliot_services_icon)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setContentTitle(getString(R.string.service_main_title))
                    .setContentText(
                        getString(
                            R.string.service_content,
                            Wiliot.getFullGWId(),
                            Wiliot.delegate.applicationVersionName()
                        )
                    )
                    .setContentIntent(PendingIntent.getActivity(
                        contextWrapper.applicationContext,
                        0,
                        Intent(),
                        PendingIntent.FLAG_IMMUTABLE)
                    )
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
            }
        }
        mainNotification
    }

    /**
     * Upstream service [Notification]. Used for the Upstream service when it goes foreground
     */
    fun getWltUsServiceNotification(contextWrapper: ContextWrapper): Notification? = with(contextWrapper) {
        if (usNotification == null) {
            contextWrapper.createNotificationChannel()?.let { channelId ->
                val notificationBuilder = NotificationCompat.Builder(this, channelId)
                usNotification = notificationBuilder.setOngoing(true)
                    .setGroup(wltNotificationGroup)
                    .setSmallIcon(R.drawable.upstream)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setContentTitle(getString(R.string.service_us_title))
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
            }
        }
        usNotification
    }

    /**
     * Downstream service [Notification]. Used for the Downstream service when it goes foreground
     */
    fun getWltDsServiceNotification(contextWrapper: ContextWrapper): Notification? = with(contextWrapper) {
        if (dsNotification == null) {
            contextWrapper.createNotificationChannel()?.let { channelId ->
                val notificationBuilder = NotificationCompat.Builder(this, channelId)
                dsNotification = notificationBuilder.setOngoing(true)
                    .setGroup(wltNotificationGroup)
                    .setSmallIcon(R.drawable.downstream)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setContentTitle(getString(R.string.service_ds_title))
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
            }
        }
        dsNotification
    }

    /**
     * Transport/MQTT service [Notification]. Used for the MQTT client service when it goes foreground
     */
    fun getWltMQTTServiceNotification(contextWrapper: ContextWrapper): Notification? = with(contextWrapper) {
        if (mqttNotification == null) {
            contextWrapper.createNotificationChannel()?.let { channelId ->
                val notificationBuilder = NotificationCompat.Builder(this, channelId)
                mqttNotification = notificationBuilder.setOngoing(true)
                    .setGroup(wltNotificationGroup)
                    .setSmallIcon(R.drawable.transport_service)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setContentTitle(getString(R.string.service_mqtt_title))
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
            }
        }
        mqttNotification
    }

    private fun ContextWrapper.createNotificationChannel(): String? {
        sharedChannel?.let {
            return it.id
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?)?.run {
            val channel = NotificationChannel(
                desiredChannelId,
                channelName,
                NotificationManager.IMPORTANCE_NONE
            )
            channel.lightColor = Color.BLUE
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            createNotificationChannel(channel)
            sharedChannel = channel
        }
        return sharedChannel!!.id
    }

}
