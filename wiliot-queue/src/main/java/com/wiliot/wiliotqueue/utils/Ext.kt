package com.wiliot.wiliotqueue.utils

import com.google.gson.*
import com.wiliot.wiliotcore.env.EnvironmentWiliot
import com.wiliot.wiliotqueue.mqtt.model.*
import okhttp3.Request
import java.lang.reflect.Type

internal class NullableUIntJson : JsonSerializer<UInt?>, JsonDeserializer<UInt?> {
    override fun serialize(
        src: UInt?,
        typeOfSrc: Type,
        context: JsonSerializationContext,
    ): JsonElement {
        if (src == null)
            return JsonNull.INSTANCE

        return JsonPrimitive(src.toLong())
    }

    @Throws(JsonParseException::class)
    override fun deserialize(
        json: JsonElement,
        type: Type,
        context: JsonDeserializationContext,
    ): UInt? {
        if (json.isJsonNull)
            return null

        return json.asLong.toUInt()
    }
}

internal class MQTTDataSerializerJson : JsonSerializer<MQTTBaseData>, JsonDeserializer<MQTTBaseData> {

    override fun serialize(
        src: MQTTBaseData?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement {
        val jsonObject = JsonObject()
        jsonObject.addProperty("payload", src?.payload?.uppercase())
        jsonObject.addProperty("sequenceId", src?.sequenceId)
        jsonObject.addProperty("rssi", src?.rssi)
        jsonObject.addProperty("timestamp", src?.timestamp)

        when (src) {
            is PackedEdgeDataMQTT -> {
                if (src.aliasBridgeId != null) {
                    jsonObject.addProperty("aliasBridgeId", src.aliasBridgeId)
                }
            }

            is PackedDataMetaMQTT -> {
                if (src.aliasBridgeId != null) {
                    jsonObject.addProperty("aliasBridgeId", src.aliasBridgeId)
                }
            }

            is PackedDataInternalSensorMQTT -> {
                jsonObject.addProperty("nfpkt", src.nfpkt)
                jsonObject.addProperty("isSensor", src.isSensor)
                jsonObject.addProperty("isEmbedded", src.isEmbedded)
                jsonObject.addProperty("isScrambled", src.isScrambled)
                jsonObject.addProperty("sensorServiceId", src.sensorServiceId)
                jsonObject.addProperty("sensorId", src.sensorId)
            }
        }
        return jsonObject
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): MQTTBaseData? {
        val jsonObject = json?.asJsonObject
        val payload = jsonObject?.get("payload")?.asString
        val rssi = jsonObject?.get("rssi")?.asInt
        val timestamp = jsonObject?.get("timestamp")?.asLong ?: 0
        val aliasBridgeId = jsonObject?.get("aliasBridgeId")?.asString

        return when (typeOfT) {
            PackedEdgeDataMQTT::class.java -> PackedEdgeDataMQTT(
                payload,
                rssi,
                timestamp,
                aliasBridgeId
            )

            PackedDataMetaMQTT::class.java -> {
                PackedDataMetaMQTT(payload, rssi, timestamp, aliasBridgeId, false)
            }

            else -> null
        }
    }
}

internal fun Request.signedRequest(token: String?): Request {
    return newBuilder()
        .apply {
            token?.let {
                header("Authorization", "Bearer $it")
            }
        }
        .build()
}

internal fun EnvironmentWiliot.coreApiBase(): String {
    return apiBaseUrl
}
