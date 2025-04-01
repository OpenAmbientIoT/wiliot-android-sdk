package com.wiliot.wiliotvirtualbridge.config.model

data class DatapathConfigPacket(
    val moduleType: Int,
    val msgType: Int,
    val apiVersion: Int,
    val seqId: Int,
    val brgMac: String,
    val globalPacing: Int,
    val adaptivePacer: Boolean,
    val unifiedEchoPkt: Boolean,
    val pacerInterval: Int,
    val pktFilter: Int,
    val txRepetition: Int,
    val commOutputPower: Int,
    val commPattern: Int
) {

    companion object {
        fun parsePayload(payload: String): DatapathConfigPacket? {
            // Find the start of the useful payload, after "C6FC0000ED"
            val startIndex = payload.indexOf("C6FC0000ED")
            if (startIndex == -1) return null // "C6FC0000ED" not found

            // Extract the useful part after "C6FC0000ED"
            val usefulPayload = payload.substring(startIndex + 10)

            // Convert the useful part to binary
            val binaryString = usefulPayload.chunked(2)
                .joinToString("") { it.toInt(16).toString(2).padStart(8, '0') }

            // Helper function to read bits from the binary string
            var currentIndex = 0
            fun readBits(length: Int): String {
                val bits = binaryString.substring(currentIndex, currentIndex + length)
                currentIndex += length
                return bits
            }

            // Parse each field
            val moduleType = readBits(4).toInt(2)
            val msgType = readBits(4).toInt(2)
            val apiVersion = readBits(8).toInt(2)
            val seqId = readBits(8).toInt(2)
            val brgMac = (0 until 6).joinToString("") { readBits(8).toInt(2).toString(16).padStart(2, '0') }
            val globalPacing = readBits(4).toInt(2)
            readBits(2) // Skip unused bits
            val adaptivePacer = readBits(1) == "1"
            val unifiedEchoPkt = readBits(1) == "1"
            val pacerInterval = readBits(16).toInt(2)
            val pktFilter = readBits(5).toInt(2)
            val txRepetition = readBits(3).toInt(2)
            val commOutputPower = readBits(8).toInt(2)
            val commPattern = readBits(4).toInt(2)

            // Return the parsed payload
            return DatapathConfigPacket(
                moduleType,
                msgType,
                apiVersion,
                seqId,
                brgMac,
                globalPacing,
                adaptivePacer,
                unifiedEchoPkt,
                pacerInterval,
                pktFilter,
                txRepetition,
                commOutputPower,
                commPattern
            )
        }
    }

}

