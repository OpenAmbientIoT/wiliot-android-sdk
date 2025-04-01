package com.wiliot.wiliotvirtualbridge.utils

import java.nio.ByteBuffer
import java.security.MessageDigest

internal fun String.replaceBytes(range: Pair<Int, Int>, replacement: String.() -> String): String {
    return StringBuilder().apply {
        if (range.first > 0) append(this@replaceBytes.substringBytes(Pair(0, range.first - 1)))
        append(
            replacement.invoke(
                this@replaceBytes.substringBytes(range)
            ).also {
                if (it.isHex().not()) throw IllegalArgumentException(
                    "replacement part is not a hex number"
                )
                if (it.length != ((range.second - range.first) + 1) * 2) throw IllegalArgumentException(
                    "replacement part invalid, it does not match the range length"
                )
            }
        )
        if (range.second < this@replaceBytes.length) append(this@replaceBytes.substring((range.second + 1) * 2, this@replaceBytes.length))
    }.toString()
}

internal fun String.replaceByte(byte: Int, replacement: String.() -> String): String = replaceBytes(Pair(byte, byte), replacement)

internal fun String.takeBytes(range: Pair<Int, Int>): String = substringBytes(range)

internal fun String.substringBytes(range: Pair<Int, Int>): String {
    val startIndex = range.first * 2 // Each byte is 2 hex chars
    val endIndex = (range.second + 1) * 2
    return this.substring(startIndex, endIndex)
}

private fun String.isHex(): Boolean {
    val hexRegex = Regex("-?[0-9a-fA-F]+")
    return matches(hexRegex)
}

/**
 * Generates MAC address based on String.
 * This function is experimental and couldn't be used in production.
 */
internal fun generateMacFromString(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    val macBytes = digest.take(6).toByteArray()
    macBytes[0] = (macBytes[0].toInt() and 0xFE or 0x02).toByte()
    return macBytes.joinToString(":") { String.format("%02X", it) }
}

internal fun String.hexStringToByteArray(): ByteArray {
    val hex = replace(":", "")
    return ByteArray(hex.length / 2) {
        hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}

internal fun ByteBuffer.put24BitValue(value: Int) {
    this.put(((value shr 16) and 0xFF).toByte())
    this.put(((value shr 8) and 0xFF).toByte())
    this.put((value and 0xFF).toByte())
}
