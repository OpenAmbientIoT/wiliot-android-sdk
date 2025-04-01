package com.wiliot.wiliotcore.utils.jwt

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.wiliot.wiliotcore.BuildConfig
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import java.lang.reflect.Type
import java.util.Date
import kotlin.math.floor

object JwtUtils {

    private val logTag = logTag()

    private val gson = GsonBuilder()
        .registerTypeAdapter(
            JWTPayload::class.java,
            JWTDeserializer()
        ).create()

    @Throws(JwtDecodeException::class)
    fun parseJwt(jwtString: String): JWT {
        val parts = try {
            jwtString.split(".")
        } catch (ex: Exception) {
            throw JwtDecodeException(
                "Unable to parse JWT string. Unable to split string",
                ex
            )
        }
        val header = try {
            parts[0]
        } catch (ex: Exception) {
            throw JwtDecodeException(
                "Unable to parse JWT string. Unable to get header [0]",
                ex
            )
        }
        val body = try {
            parts[1]
        } catch (ex: Exception) {
            throw JwtDecodeException(
                "Unable to parse JWT string. Unable to get body [1]",
                ex
            )
        }
        val decodedHeader = try {
            Base64.decode(header, Base64.URL_SAFE).decodeToString().also {
                if (BuildConfig.DEBUG) Reporter.log("decodedHeader: $it", logTag)
            }
        } catch (ex: Exception) {
            throw JwtDecodeException(
                "Unable to parse JWT string. Unable to decode header (Base64)",
                ex
            )
        }
        val decodedBody = try {
            Base64.decode(body, Base64.URL_SAFE).decodeToString().also {
                if (BuildConfig.DEBUG) Reporter.log("decodedBody: $it", logTag)
            }
        } catch (ex: Exception) {
            throw JwtDecodeException(
                "Unable to parse JWT string. Unable to decode body (Base64)",
                ex
            )
        }
        return try {
            val mapType = object : TypeToken<Map<String?, Any?>?>() {}.type
            JWT(
                tkn = jwtString,
                header = parseJson(decodedHeader, mapType),
                body = parseJson(decodedBody, JWTPayload::class.java)
            )
        } catch (ex: Exception) {
            throw JwtDecodeException(
                "Unable to parse JWT string. Unable to decode JSON",
                ex
            )
        }
    }

    private fun <T> parseJson(json: String, typeOfT: Type): T {
        return try {
            gson.fromJson(json, typeOfT)
        } catch (ex: Exception) {
            val err = if (BuildConfig.DEBUG) json else ""
            throw JwtDecodeException(
                "The token's payload had an invalid JSON format: $err",
                ex
            )
        }
    }

}

interface JwtClaim {
    fun asBoolean(): Boolean?
    fun asInt(): Int?
    fun asLong(): Long?
    fun asDouble(): Double?
    fun asString(): String?
    fun asDate(): Date?
    @Throws(JwtDecodeException::class)
    fun <T> asArray(v: Class<T>): Array<out T>?
    @Throws(JwtDecodeException::class)
    fun <T> asList(v: Class<T>): List<T>?
    @Throws(JwtDecodeException::class)
    fun <T> asObject(v: Class<T>): T?
}

internal class DummyJwtClaimImp : JwtClaim {
    override fun asBoolean(): Boolean? = null
    override fun asInt(): Int? = null
    override fun asLong(): Long? = null
    override fun asDouble(): Double? = null
    override fun asString(): String? = null
    override fun asDate(): Date? = null
    override fun <T> asArray(v: Class<T>): Array<out T>? = null
    override fun <T> asList(v: Class<T>): List<T>? = null
    override fun <T> asObject(v: Class<T>): T? = null
}

internal class JwtClaimImpl(
    val value: JsonElement
): JwtClaim {

    override fun asBoolean(): Boolean? {
        return if (!value.isJsonPrimitive) null else value.asBoolean
    }

    override fun asInt(): Int? {
        return if (!value.isJsonPrimitive) null else value.asInt
    }

    override fun asLong(): Long? {
        return if (!value.isJsonPrimitive) null else value.asLong
    }

    override fun asDouble(): Double? {
        return if (!value.isJsonPrimitive) null else value.asDouble
    }

    override fun asString(): String? {
        return if (!value.isJsonPrimitive) null else value.asString
    }

    override fun asDate(): Date? {
        return if (!value.isJsonPrimitive) {
            null
        } else {
            val ms = value.asString.toLong() * 1000L
            Date(ms)
        }
    }

    @Throws(JwtDecodeException::class)
    override fun <T> asArray(v: Class<T>): Array<out T> {
        return try {
            if (value.isJsonArray && !value.isJsonNull) {
                val gson = Gson()
                val jsonArr = value.asJsonArray
                val arr: Array<T> =
                    java.lang.reflect.Array.newInstance(v, jsonArr.size()) as Array<T>
                for (i in 0 until jsonArr.size()) {
                    arr[i] = gson.fromJson(jsonArr[i], v)
                }
                arr
            } else {
                java.lang.reflect.Array.newInstance(v, 0) as Array<T>
            }
        } catch (ex: JsonSyntaxException) {
            throw JwtDecodeException("Failed to decode claim as array", ex)
        }
    }

    @Throws(JwtDecodeException::class)
    override fun <T> asList(v: Class<T>): List<T> {
        return try {
            if (value.isJsonArray && !value.isJsonNull) {
                val gson = Gson()
                val jsonArr = value.asJsonArray
                val list: MutableList<T> = ArrayList()
                for (i in 0 until jsonArr.size()) {
                    list.add(gson.fromJson(jsonArr[i], v))
                }
                list
            } else {
                ArrayList()
            }
        } catch (ex: JsonSyntaxException) {
            throw JwtDecodeException("Failed to decode claim as list", ex)
        }
    }

    @Throws(JwtDecodeException::class)
    override fun <T> asObject(v: Class<T>): T? {
        return try {
            if (value.isJsonNull) null else Gson()
                .fromJson(value, v)!!
        } catch (ex: JsonSyntaxException) {
            throw JwtDecodeException("Failed to decode claim as " + v.simpleName, ex)
        }
    }

}

data class JWT(
    val tkn: String,
    val header: Map<String, Any>,
    val body: JWTPayload
) {
    override fun toString(): String {
        return tkn
    }
}

data class JWTPayload(
    val iss: String?,
    val sub: String,
    val exp: Date?,
    val nbf: Date?,
    val iat: Date,
    val jti: String?,
    val aud: List<String>,
    val tree: Map<String, JwtClaim>
) {
    fun claimForName(name: String): JwtClaim {
        return try {
            tree.getOrDefault(name, DummyJwtClaimImp())
        } catch (ex: Exception) {
            throw JwtDecodeException(
                "JWT: Can not get claim for $name",
                ex
            )
        }
    }
}

class JwtDecodeException(
    override val message: String?,
    override val cause: Throwable?
) : Exception(message, cause)

internal class JWTDeserializer : JsonDeserializer<JWTPayload> {

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): JWTPayload {
        return if (!json!!.isJsonNull && json.isJsonObject) {
            val `object` = json.asJsonObject
            val iss = getString(`object`, "iss")
            val sub = getString(`object`, "sub")
            val exp = getDate(`object`, "exp")
            val nbf = getDate(`object`, "nbf")
            val iat = getDate(`object`, "iat")
            val jti = getString(`object`, "jti")
            val aud = getStringOrArray(`object`, "aud")
            val extra: HashMap<String, JwtClaim> = HashMap()
            val iterator: Iterator<*> = `object`.entrySet().iterator()
            while (iterator.hasNext()) {
                val (key, value) = iterator.next() as Map.Entry<String, *>
                extra[key] = JwtClaimImpl((value as JsonElement))
            }
            JWTPayload(iss, sub!!, exp, nbf, iat!!, jti, aud ?: emptyList(), extra)
        } else {
            throw JwtDecodeException("The token's payload had an invalid JSON format.", null)
        }
    }

    private fun getStringOrArray(obj: JsonObject, claimName: String): List<String>? {
        var list: List<String>? = emptyList<String>()
        if (obj.has(claimName)) {
            val arrElement = obj[claimName]
            if (arrElement.isJsonArray) {
                val jsonArr = arrElement.asJsonArray
                list = java.util.ArrayList<String>(jsonArr.size())
                for (i in 0 until jsonArr.size()) {
                    list.add(jsonArr[i].asString)
                }
            } else {
                list = listOf(arrElement.asString)
            }
        }
        return list
    }

    private fun getDate(obj: JsonObject, claimName: String): Date? {
        return if (!obj.has(claimName)) {
            null
        } else {
            val ms = obj[claimName].asLong * 1000L
            Date(ms)
        }
    }

    private fun getString(obj: JsonObject, claimName: String): String? {
        return if (!obj.has(claimName)) null else obj[claimName].asString
    }

}

fun JWT.getClaim(fieldName: String): JwtClaim {
    return body.claimForName(fieldName)
}

fun JWT.getIssuer() = this.body.iss

fun JWT.getSubject() = this.body.sub

fun JWT.getAudience() = this.body.aud

fun JWT.getExpiresAt() = this.body.exp

fun JWT.getNotBefore() = this.body.nbf

fun JWT.getIssuedAt() = this.body.iat

fun JWT.getId() = this.body.jti

fun JWT.getClaims() = this.body.tree

fun JWT.isExpired(leeway: Long): Boolean {
    require(leeway >= 0) { "The leeway must be a positive value." }
    val todayTime = (floor((Date().time / 1000).toDouble()) * 1000).toLong() //truncate millis
    val futureToday = Date(todayTime + leeway * 1000)
    val pastToday = Date(todayTime - leeway * 1000)
    val expValid = body.exp == null || !pastToday.after(body.exp)
    val iatValid = !futureToday.before(body.iat)
    return !expValid || !iatValid
}

