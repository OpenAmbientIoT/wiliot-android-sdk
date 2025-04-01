package com.wiliot.wiliotqueue.utils

import android.app.Notification
import android.app.Service
import info.mqtt.android.service.MqttAndroidClient
import info.mqtt.android.service.MqttService
import org.eclipse.paho.client.mqttv3.IMqttToken

/**
 * Works with com.github.hannesa2:paho.mqtt.android:4.1
 */
fun MqttAndroidClient.disconnectAndStopService(): IMqttToken {
    val token = disconnect()
    this::class.java.getDeclaredField("mqttService").let { serviceField ->
        serviceField.isAccessible = true
        val serviceInstance = serviceField.get(this) as? MqttService?
        serviceInstance?.stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }
    return token
}

/**
 * Works with com.github.hannesa2:paho.mqtt.android:4.1
 */
fun MqttAndroidClient.ensureForegroundRunning() {
    var notificationInstance: Notification?
    this::class.java.getDeclaredField("foregroundServiceNotification").let { notificationField ->
        notificationField.isAccessible = true
        notificationInstance = notificationField.get(this) as? Notification?
    }
    if (notificationInstance != null) {
        this::class.java.getDeclaredField("mqttService").let { serviceField ->
            serviceField.isAccessible = true
            val serviceInstance = serviceField.get(this) as? MqttService?
            serviceInstance?.startForeground(77 /* MqttAndroidClient.FOREGROUND_ID */, notificationInstance)
        }
    }
}