package com.wiliot.wiliotqueue.di

import info.mqtt.android.service.MqttAndroidClient

interface MqttClientProviderContract {
    fun getForEnvironment(environment: String): MqttAndroidClient
}