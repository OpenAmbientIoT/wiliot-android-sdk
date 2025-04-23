package com.wiliot.wiliotqueue.di

import android.app.Application
import android.util.Log
import com.google.gson.Gson
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.getWithApplicationContext
import com.wiliot.wiliotcore.health.WiliotHealthMonitor
import com.wiliot.wiliotcore.legacy.EnvironmentWiliot
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.ResettableLazy
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotcore.utils.resettableLazy
import com.wiliot.wiliotcore.utils.weak
import com.wiliot.wiliotqueue.BuildConfig
import com.wiliot.wiliotqueue.mqtt.payloads.SoftwareGatewayCapabilitiesPayload
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.lang.ref.WeakReference

internal class MqttProvider(
    private val prodAwsClient: Lazy<MqttAndroidClient> = WiliotMqttClientsModule.provideProdAwsMQTTClient(),
    private val prodGcpClient: Lazy<MqttAndroidClient> = WiliotMqttClientsModule.provideProdGcpMQTTClient(),
    private val testAwsClient: Lazy<MqttAndroidClient> = WiliotMqttClientsModule.provideTestAwsMQTTClient(),
    private val testGcpClient: Lazy<MqttAndroidClient> = WiliotMqttClientsModule.provideTestGcpMQTTClient(),
    private val devAwsClient: Lazy<MqttAndroidClient> = WiliotMqttClientsModule.provideDevAwsMQTTClient(),
    private val devGcpClient: Lazy<MqttAndroidClient> = WiliotMqttClientsModule.provideDevGcpMQTTClient(),
    private val customClient: ResettableLazy<MqttAndroidClient> = WiliotMqttClientsModule.provideCustomMQTTClient()
) : MqttClientProviderContract {

    private val logTag = logTag()

    private var customClientInitialized = false

    override fun getForEnvironment(environment: String): MqttAndroidClient {
        if (Wiliot.brokerConfig.isCustomBroker) {
            Reporter.log("QUEUE CONFIGURED TO USE CUSTOM BROKER!", logTag, highlightError = true)
            return customClient.value.also {
                customClientInitialized = true
            }
        } else {
            if (customClientInitialized) {
                WiliotMqttClientsModule.resetCustomMqttClient()
                customClient.reset().also {
                    customClientInitialized = false
                }
            }
        }
        return EnvironmentWiliot.entries.first {
            it.value == environment
        }.let { env ->
            Reporter.log("getForEnvironment -> $env", logTag)
            when (env) {
                EnvironmentWiliot.PROD_AWS -> prodAwsClient.value
                EnvironmentWiliot.PROD_GCP -> prodGcpClient.value
                EnvironmentWiliot.TEST_AWS -> testAwsClient.value
                EnvironmentWiliot.TEST_GCP -> testGcpClient.value
                EnvironmentWiliot.DEV_AWS -> devAwsClient.value
                EnvironmentWiliot.DEV_GCP -> devGcpClient.value
            }
        }
    }
}

private object WiliotMqtt {

    private var mqttClientProvider: MqttClientProviderContract? = null

    fun provideMqttClientProviderContract(): MqttClientProviderContract {
        if (mqttClientProvider == null) {
            mqttClientProvider = MqttProvider()
        }
        return mqttClientProvider!!
    }

}

fun mqttProvider() = WiliotMqtt.provideMqttClientProviderContract()

private object WiliotMqttClientsModule {

    private var mqttClientProdAws: Lazy<MqttAndroidClient>? = null
    private var mqttClientProdGcp: Lazy<MqttAndroidClient>? = null
    private var mqttClientTestAws: Lazy<MqttAndroidClient>? = null
    private var mqttClientTestGcp: Lazy<MqttAndroidClient>? = null
    private var mqttClientDevAws: Lazy<MqttAndroidClient>? = null
    private var mqttClientDevGcp: Lazy<MqttAndroidClient>? = null
    private var mqttClientCustom: ResettableLazy<MqttAndroidClient>? = null

    private const val logTag = "MQTT"

    interface ExtendedMqttCallback : MqttCallback {
        fun setDebugData(data: String)
        fun setupClient(
            client: MqttAndroidClient?,
            gatewayId: String?,
            currentOwnerId: () -> String?,
        )
    }

    private fun provideMqttCallback(): ExtendedMqttCallback {
        return object : ExtendedMqttCallback {

            private var capabilitiesMessageSent: Boolean = false

            private var debugData: String? = null
            private var weakClient: WeakReference<MqttAndroidClient?>? = null
            private var ownerIdRetriever: (() -> String?)? = null
            private var gwId: String? = null

            override fun setDebugData(data: String) {
                debugData = data
            }

            override fun setupClient(
                client: MqttAndroidClient?,
                gatewayId: String?,
                currentOwnerId: () -> String?,
            ) {
                this.weakClient = client?.weak()
                this.ownerIdRetriever = currentOwnerId
                this.gwId = gatewayId
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
            }

            override fun connectionLost(cause: Throwable?) {
                Reporter.log("MQTT connectivity lost $debugData", logTag)
                capabilitiesMessageSent = false
                cause?.let {
                    Reporter.log(it.localizedMessage ?: "", logTag, highlightError = true)
                    if (BuildConfig.DEBUG) {
                        Log.e(logTag, "connectionLost $debugData", cause)
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Reporter.log(
                    "deliveryComplete(${token?.client?.serverURI}, $debugData) called with: token = ${token?.message?.payload?.decodeToString()}",
                    logTag
                )
                WiliotHealthMonitor.notifyUplinkMessageDelivered()
                sendCapabilitiesMessage()
            }

            private fun sendCapabilitiesMessage() {
                if (capabilitiesMessageSent) return
                Reporter.log("sendCapabilitiesMessage", logTag)

                val client = weakClient?.get()
                val cntOwner = ownerIdRetriever?.invoke()
                if (gwId == null || cntOwner == null || client == null) {
                    Reporter.log(
                        "sendCapabilitiesMessage -> can not send message; not enough data",
                        logTag
                    )
                    return
                }

                val clientConnected = try {
                    client.isConnected
                } catch (ex: Exception) {
                    Reporter.exception("Error in sendCapabilitiesMessage", ex, logTag)
                    false
                }

                if (clientConnected) {
                    val topic =
                        "status${EnvironmentWiliot.mqttSuffix[Wiliot.configuration.environment.value]}/$cntOwner/$gwId"

                    try {
                        val jsonPayload =
                            Gson().toJson(SoftwareGatewayCapabilitiesPayload.create(cloudManaged = Wiliot.configuration.cloudManaged))
                        client.publish(
                            topic,
                            MqttMessage(jsonPayload.encodeToByteArray())
                        )

                        capabilitiesMessageSent = true
                        Reporter.log("sendCapabilitiesMessage -> sent", logTag)
                    } catch (e: Exception) {
                        Reporter.log("sendCapabilitiesMessage -> exception occurred", logTag)
                        e.printStackTrace()
                    }
                } else {
                    Reporter.log(
                        "sendCapabilitiesMessage -> can not send message; client not connected yet",
                        logTag,
                        highlightError = true
                    )
                }
            }

        }
    }

    fun provideProdAwsMQTTClient(
        app: Application = getWithApplicationContext { this.applicationContext as Application }!!,
        gatewayId: String = Wiliot.getFullGWId(),
        callback: ExtendedMqttCallback = provideMqttCallback(),
    ): Lazy<MqttAndroidClient> {
        if (mqttClientProdAws == null) {
            mqttClientProdAws = lazy {
                MqttAndroidClient(
                    app,
                    BuildConfig.PROD_AWS_MQTT_URL,
                    gatewayId
                ).apply {
                    setCallback(
                        callback.also {
                            it.setDebugData("ProdAwsMqttClient")
                            it.setupClient(
                                client = this,
                                gatewayId = gatewayId,
                                currentOwnerId = {
                                    Wiliot.configuration.ownerId
                                }
                            )
                        }
                    )
                }
            }
        }
        return mqttClientProdAws!!
    }

    fun provideProdGcpMQTTClient(
        app: Application = getWithApplicationContext { this.applicationContext as Application }!!,
        gatewayId: String = Wiliot.getFullGWId(),
        callback: ExtendedMqttCallback = provideMqttCallback(),
    ): Lazy<MqttAndroidClient> {
        if (mqttClientProdGcp == null) {
            mqttClientProdGcp = lazy {
                MqttAndroidClient(
                    app,
                    BuildConfig.PROD_GCP_MQTT_URL,
                    gatewayId
                ).apply {
                    setCallback(
                        callback.also {
                            it.setDebugData("ProdGcpMqttClient")
                            it.setupClient(
                                client = this,
                                gatewayId = gatewayId,
                                currentOwnerId = {
                                    Wiliot.configuration.ownerId
                                }
                            )
                        }
                    )
                }
            }
        }
        return mqttClientProdGcp!!
    }

    fun provideTestAwsMQTTClient(
        app: Application = getWithApplicationContext { this.applicationContext as Application }!!,
        gatewayId: String = Wiliot.getFullGWId(),
        callback: ExtendedMqttCallback = provideMqttCallback(),
    ): Lazy<MqttAndroidClient> {
        if (mqttClientTestAws == null) {
            mqttClientTestAws = lazy {
                MqttAndroidClient(
                    app,
                    BuildConfig.TEST_AWS_MQTT_URL,
                    gatewayId
                ).apply {
                    setCallback(
                        callback.also {
                            it.setDebugData("TestAwsMqttClient")
                            it.setupClient(
                                client = this,
                                gatewayId = gatewayId,
                                currentOwnerId = {
                                    Wiliot.configuration.ownerId
                                }
                            )
                        }
                    )
                }
            }
        }
        return mqttClientTestAws!!
    }

    fun provideTestGcpMQTTClient(
        app: Application = getWithApplicationContext { this.applicationContext as Application }!!,
        gatewayId: String = Wiliot.getFullGWId(),
        callback: ExtendedMqttCallback = provideMqttCallback(),
    ): Lazy<MqttAndroidClient> {
        if (mqttClientTestGcp == null) {
            mqttClientTestGcp = lazy {
                MqttAndroidClient(
                    app,
                    BuildConfig.TEST_GCP_MQTT_URL,
                    gatewayId
                ).apply {
                    setCallback(
                        callback.also {
                            it.setDebugData("TestGcpMqttClient")
                            it.setupClient(
                                client = this,
                                gatewayId = gatewayId,
                                currentOwnerId = {
                                    Wiliot.configuration.ownerId
                                }
                            )
                        }
                    )
                }
            }
        }
        return mqttClientTestGcp!!
    }

    fun provideDevAwsMQTTClient(
        app: Application = getWithApplicationContext { this.applicationContext as Application }!!,
        gatewayId: String = Wiliot.getFullGWId(),
        callback: ExtendedMqttCallback = provideMqttCallback(),
    ): Lazy<MqttAndroidClient> {
        if (mqttClientDevAws == null) {
            mqttClientDevAws = lazy {
                MqttAndroidClient(
                    app,
                    BuildConfig.DEV_AWS_MQTT_URL,
                    gatewayId
                ).apply {
                    setCallback(
                        callback.also {
                            it.setDebugData("DevAwsMqttClient")
                            it.setupClient(
                                client = this,
                                gatewayId = gatewayId,
                                currentOwnerId = {
                                    Wiliot.configuration.ownerId
                                }
                            )
                        }
                    )
                }
            }
        }
        return mqttClientDevAws!!
    }

    fun provideDevGcpMQTTClient(
        app: Application = getWithApplicationContext { this.applicationContext as Application }!!,
        gatewayId: String = Wiliot.getFullGWId(),
        callback: ExtendedMqttCallback = provideMqttCallback(),
    ): Lazy<MqttAndroidClient> {
        if (mqttClientDevGcp == null) {
            mqttClientDevGcp = lazy {
                MqttAndroidClient(
                    app,
                    BuildConfig.DEV_GCP_MQTT_URL,
                    gatewayId
                ).apply {
                    setCallback(
                        callback.also {
                            it.setDebugData("DevGcpMqttClient")
                            it.setupClient(
                                client = this,
                                gatewayId = gatewayId,
                                currentOwnerId = {
                                    Wiliot.configuration.ownerId
                                }
                            )
                        }
                    )
                }
            }
        }
        return mqttClientDevGcp!!
    }

    fun provideCustomMQTTClient(
        app: Application = getWithApplicationContext { this.applicationContext as Application }!!,
        gatewayId: String = Wiliot.getFullGWId(),
        callback: ExtendedMqttCallback = provideMqttCallback(),
    ): ResettableLazy<MqttAndroidClient> {
        if (mqttClientCustom == null) {
            mqttClientCustom = resettableLazy {
                MqttAndroidClient(
                    app,
                    Wiliot.brokerConfig.broker,
                    gatewayId
                ).apply {
                    setCallback(
                        callback.also {
                            it.setDebugData("CustomMqttClient")
                            it.setupClient(
                                client = this,
                                gatewayId = gatewayId,
                                currentOwnerId = {
                                    Wiliot.brokerConfig.ownerId
                                }
                            )
                        }
                    )
                }
            }
        }
        return mqttClientCustom!!
    }

    fun resetCustomMqttClient() {
        mqttClientCustom?.reset()
        mqttClientCustom = null
    }

}