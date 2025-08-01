package com.wiliot.wiliotqueue.di

import android.app.Application
import android.util.Log
import com.google.gson.Gson
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.env.EnvironmentWiliot
import com.wiliot.wiliotcore.env.Environments
import com.wiliot.wiliotcore.getWithApplicationContext
import com.wiliot.wiliotcore.health.WiliotHealthMonitor
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
    private val customDynamicClient: ResettableLazy<MqttAndroidClient> = WiliotMqttClientsModule.provideCustomDynamicMQTTClient(),
    private val customClient: ResettableLazy<MqttAndroidClient> = WiliotMqttClientsModule.provideCustomMQTTClient()
) : MqttClientProviderContract {

    private val logTag = logTag()

    private var customDynamicClientInitialized = false

    private var lastCustomEnvironment: String? = null

    private val predefinedEnvironmentNames = listOf(
        Environments.WILIOT_PROD_AWS
    ).map { it.envName }

    override fun getForEnvironment(environment: String): MqttAndroidClient {
        if (Wiliot.dynamicBrokerConfig.isDynamicCustomBroker) {
            Reporter.log("QUEUE CONFIGURED TO USE CUSTOM BROKER!", logTag, highlightError = true)
            return customDynamicClient.value.also {
                customDynamicClientInitialized = true
            }
        } else {
            // if not using custom dynamic broker anymore, reset the custom dynamic client if it was initialized
            if (customDynamicClientInitialized) {
                WiliotMqttClientsModule.resetCustomDynamicMqttClient()
                customDynamicClient.reset().also {
                    customDynamicClientInitialized = false
                }
            }
        }

        // If requested environment is not one of the predefined environments,
        // need to check if there is a need to reset outdated custom client
        if (environment !in predefinedEnvironmentNames) {
            if (environment != lastCustomEnvironment) {
                WiliotMqttClientsModule.resetCustomEnvironmentMqttClient()
                lastCustomEnvironment = environment
            }
        }

        return Environments.set.first {
            it.envName == environment
        }.let { env ->
            Reporter.log("getForEnvironment -> $env", logTag)
            when (env) {
                Environments.WILIOT_PROD_AWS -> prodAwsClient.value
                else -> customClient.value
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
    private var mqttClientCustomDynamic: ResettableLazy<MqttAndroidClient>? = null
    private var mqttClientCustom: ResettableLazy<MqttAndroidClient>? = null

    private var lastCustomEnvironment: String? = null

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
                        "status/$cntOwner/$gwId"

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

    fun provideCustomDynamicMQTTClient(
        app: Application = getWithApplicationContext { this.applicationContext as Application }!!,
        gatewayId: String = Wiliot.getFullGWId(),
        callback: ExtendedMqttCallback = provideMqttCallback(),
    ): ResettableLazy<MqttAndroidClient> {
        if (mqttClientCustomDynamic == null) {
            mqttClientCustomDynamic = resettableLazy {
                MqttAndroidClient(
                    app,
                    Wiliot.dynamicBrokerConfig.broker,
                    gatewayId
                ).apply {
                    setCallback(
                        callback.also {
                            it.setDebugData("CustomDynamicMqttClient")
                            it.setupClient(
                                client = this,
                                gatewayId = gatewayId,
                                currentOwnerId = {
                                    Wiliot.dynamicBrokerConfig.ownerId
                                }
                            )
                        }
                    )
                }
            }
        }
        return mqttClientCustomDynamic!!
    }

    fun provideCustomMQTTClient(
        app: Application = getWithApplicationContext { this.applicationContext as Application }!!,
        gatewayId: String = Wiliot.getFullGWId(),
        callback: ExtendedMqttCallback = provideMqttCallback(),
        environment: EnvironmentWiliot = Wiliot.configuration.environment
    ): ResettableLazy<MqttAndroidClient> {
        if (mqttClientCustom == null) {
            mqttClientCustom = resettableLazy {
                MqttAndroidClient(
                    app,
                    environment.mqttUrl,
                    gatewayId
                ).apply {
                    setCallback(
                        callback.also {
                            it.setDebugData("CustomMqttClient_${environment.envName}")
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
        } else {
            // If the custom client is already initialized, we need to check integrity
            if (lastCustomEnvironment != environment.envName) {
                throw IllegalStateException(
                    "Custom MQTT client already initialized for environment ${lastCustomEnvironment}. " +
                            "If you seeing this exception, it means domain logic of Wiliot SDK is broken " +
                            "by unauthorized interference (e.g. using reflection). If you think this is a bug, " +
                            "please report it to Wiliot support team " +
                            "support@wiliot.com"
                )
            }
        }
        return mqttClientCustom!!
    }

    fun resetCustomDynamicMqttClient() {
        mqttClientCustomDynamic?.reset()
        mqttClientCustomDynamic = null
    }

    fun resetCustomEnvironmentMqttClient() {
        mqttClientCustom?.reset()
        mqttClientCustom = null
    }

}