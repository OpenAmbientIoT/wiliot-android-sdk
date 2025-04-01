package com.wiliot.wiliotcore.utils

/**
 * Old format: (01)00850027865010(21)015rT0406
 * New format: 010085002786501021015rT0406
 * Short format: 15rT0406 (incompatible with other formats)
 *
 *  • (01) - delimiter (fixed)
 *  • 00850027865 - Wiliot vendor ID (fixed)
 *  • 010 - SKU (could be changed, but decided to be fixed for now)
 *  • (21) - delimiter (fixed)
 *  • 015r - Wiliot reel Id (variable content, four symbols)
 *  • T - delimiter(fixed)
 *  • 0406 - Wiliot pixel Id (variable content, four symbols)
 *
 */
@Suppress("unused")
object PixelTypeUtil {

    fun extractUsefulDataFromBarcode(qrData: String): String {
        return if (isPixel(qrData)) {
            qrData.asWltPixelOrNull()?.extractUsefulDataFromBarcode(qrData) ?: ""
        } else ""
    }

    fun extractPixelIdOrNull(s: String): String? {
        if (isPixel(s).not()) return null
        return s.asWltPixelOrNull()?.extractPixelIdOrNull(s)
    }

    fun extractPixelIdReelOrNull(s: String): String? {
        if (isPixel(s).not()) return null
        return s.asWltPixelOrNull()?.extractPixelIdReelOrNull(s)
    }

    fun extractPixelIdWithoutReel(s: String): String? {
        if (isPixel(s).not()) return null
        return s.asWltPixelOrNull()?.extractPixelIdWithoutReel(s)
    }

    fun extractSerialSkuOrNull(s: String): String? {
        if (isPixel(s).not()) return null
        return s.asWltPixelOrNull()?.extractSerialSkuOrNull(s)
    }

    fun extractSerialOrNull(s: String): String? {
        if (isPixel(s).not()) return null
        return s.asWltPixelOrNull()?.extractSerialOrNull(s)
    }

    fun isPixel(s: String?): Boolean {
        if (s.isNullOrBlank()) return false
        if (s.matchesShortPixelRegex()) return true
        val prefixMatches = s.startsWithPixelPrefix()
        val serialMatches = s.containsSerial()
        val containsDelimiter = s.containsSerialDelimiter()
        val hasValidLength = s.hasValidLength()
        return prefixMatches && serialMatches && containsDelimiter && hasValidLength
    }

    private fun String.asWltPixelOrNull(): WltPixel? {
        if (isPixel(this).not()) return null
        if (this.matchesShortPixelRegex()) return WltShortPixel
        if (this.startsWithOldPrefix()) return WltOldFashionPixel
        if (this.startsWithNewPrefix()) return WltModernPixel
        return null
    }

    private fun String.startsWithPixelPrefix(): Boolean {
        return this.startsWithOldPrefix() || this.startsWithNewPrefix()
    }

    private fun String.startsWithOldPrefix(): Boolean {
        return this.startsWith(WltPixel.TAG_PREFIX_OLD)
    }

    private fun String.startsWithNewPrefix(): Boolean {
        return this.startsWith(WltPixel.TAG_PREFIX_NEW)
    }

    private fun String.containsSerial(): Boolean {
        val prefix = if (startsWithOldPrefix()) WltPixel.TAG_PREFIX_OLD else WltPixel.TAG_PREFIX_NEW
        return try {
            this.substring(prefix.length, prefix.length + WltPixel.TAG_SERIAL.length) == WltPixel.TAG_SERIAL
        } catch (ex: Exception) {
            false
        }
    }

    private fun String.containsSerialDelimiter(): Boolean {
        val prefix = if (startsWithOldPrefix()) WltPixel.TAG_PREFIX_OLD else WltPixel.TAG_PREFIX_NEW
        val delimiter =
            if (startsWithOldPrefix()) WltPixel.TAG_SERIAL_DELIMITER_OLD else WltPixel.TAG_SERIAL_DELIMITER_NEW
        return try {
            this.substring(
                prefix.length + WltPixel.TAG_SERIAL.length + 3,
                prefix.length + WltPixel.TAG_SERIAL.length + 3 + delimiter.length
            ) == delimiter
        } catch (ex: Exception) {
            false
        }
    }

    private fun String.hasValidLength(): Boolean {
        val prefix = if (startsWithOldPrefix()) WltPixel.TAG_PREFIX_OLD else WltPixel.TAG_PREFIX_NEW
        val delimiter =
            if (startsWithOldPrefix()) WltPixel.TAG_SERIAL_DELIMITER_OLD else WltPixel.TAG_SERIAL_DELIMITER_NEW
        val length = prefix.length + delimiter.length + WltPixel.TAG_SERIAL.length + 3 + WltPixel.TAG_ID_LENGTH_CLASSIC
        return this.length == length
    }

    private fun String.matchesShortPixelRegex(): Boolean {
        return WltPixel.shortPixelRegex.matches(this)
    }

}

internal interface WltPixel {

    companion object {
        const val TAG_PREFIX_OLD = "(01)"
        const val TAG_PREFIX_NEW = "01"
        const val TAG_SERIAL = "00850027865"
        const val TAG_SERIAL_DELIMITER_OLD = "(21)"
        const val TAG_SERIAL_DELIMITER_NEW = "21"
        const val TAG_ID_LENGTH_CLASSIC = 9
        @Suppress("RegExpSimplifiable")
        val shortPixelRegex = Regex("[a-zA-Z0-9]{3}T\\d{4}")
    }

    fun extractUsefulDataFromBarcode(qrData: String): String
    fun extractPixelIdOrNull(s: String): String?
    fun extractPixelIdReelOrNull(s: String): String?
    fun extractPixelIdWithoutReel(s: String): String?
    fun extractSerialSkuOrNull(s: String): String?
    fun extractSerialOrNull(s: String): String?
}

private object WltOldFashionPixel : WltPixel {

    override fun extractUsefulDataFromBarcode(qrData: String): String {
        return try {
            extractPixelIdOrNull(qrData)
        } catch (ex: Exception) {
            ""
        }
    }

    override fun extractPixelIdOrNull(s: String): String {
        return s.takeLast(WltPixel.TAG_ID_LENGTH_CLASSIC)
    }

    override fun extractPixelIdReelOrNull(s: String): String {
        return s.takeLast(WltPixel.TAG_ID_LENGTH_CLASSIC).substring(0, 3)
    }

    override fun extractPixelIdWithoutReel(s: String): String {
        return s.takeLast(WltPixel.TAG_ID_LENGTH_CLASSIC).takeLast(4)
    }

    override fun extractSerialSkuOrNull(s: String): String {
        val prefix = WltPixel.TAG_PREFIX_OLD
        return s.substring(prefix.length + WltPixel.TAG_SERIAL.length, prefix.length + WltPixel.TAG_SERIAL.length + 3)
    }

    override fun extractSerialOrNull(s: String): String {
        val prefix = WltPixel.TAG_PREFIX_OLD
        return s.substring(prefix.length, prefix.length + WltPixel.TAG_SERIAL.length + 3)
    }

}

private object WltModernPixel : WltPixel {

    override fun extractUsefulDataFromBarcode(qrData: String): String {
        return try {
            extractPixelIdOrNull(qrData)
        } catch (ex: Exception) {
            ""
        }
    }

    override fun extractPixelIdOrNull(s: String): String {
        return s.takeLast(WltPixel.TAG_ID_LENGTH_CLASSIC)
    }

    override fun extractPixelIdReelOrNull(s: String): String {
        return s.takeLast(WltPixel.TAG_ID_LENGTH_CLASSIC).substring(0, 3)
    }

    override fun extractPixelIdWithoutReel(s: String): String {
        return s.takeLast(WltPixel.TAG_ID_LENGTH_CLASSIC).takeLast(4)
    }

    override fun extractSerialSkuOrNull(s: String): String {
        val prefix = WltPixel.TAG_PREFIX_NEW
        return s.substring(prefix.length + WltPixel.TAG_SERIAL.length, prefix.length + WltPixel.TAG_SERIAL.length + 3)
    }

    override fun extractSerialOrNull(s: String): String {
        val prefix = WltPixel.TAG_PREFIX_NEW
        return s.substring(prefix.length, prefix.length + WltPixel.TAG_SERIAL.length + 3)
    }

}

private object WltShortPixel : WltPixel {
    override fun extractUsefulDataFromBarcode(qrData: String): String {
        return try {
            WltOldFashionPixel.extractPixelIdOrNull(qrData)
        } catch (ex: Exception) {
            ""
        }
    }

    override fun extractPixelIdOrNull(s: String): String {
        return s
    }

    override fun extractPixelIdReelOrNull(s: String): String? {
        return null
    }

    override fun extractPixelIdWithoutReel(s: String): String {
        return s.takeLast(4)
    }

    override fun extractSerialSkuOrNull(s: String): String? {
        return null
    }

    override fun extractSerialOrNull(s: String): String? {
        return null
    }

}