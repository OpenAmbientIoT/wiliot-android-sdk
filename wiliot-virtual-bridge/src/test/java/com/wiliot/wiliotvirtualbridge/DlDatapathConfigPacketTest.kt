package com.wiliot.wiliotvirtualbridge

import com.wiliot.wiliotvirtualbridge.config.model.DatapathConfigPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DatapathConfigPacketTest {

    @Test
    fun `parsePayload should correctly parse the payload into DatapathConfigPacket`() {
        // Test payload
        val payload = "1E16C6FC0000ED250A7CC249348419AD000020000000000000000000000000"

        // Call parsePayload
        val parsedPacket = DatapathConfigPacket.parsePayload(payload)

        // Ensure parsing was successful
        assertNotNull("Parsing failed, returned null", parsedPacket)
        parsedPacket?.let {
            // Validate each parsed field
            assertEquals("Incorrect moduleType", 2, it.moduleType)
            assertEquals("Incorrect msgType", 5, it.msgType)
            assertEquals("Incorrect apiVersion", 10, it.apiVersion)
            assertEquals("Incorrect seqId", 124, it.seqId)
            assertEquals("Incorrect brgMac", "C249348419AD", it.brgMac.uppercase())
            assertEquals("Incorrect globalPacing", 0, it.globalPacing)
            assertEquals("Incorrect adaptivePacer", false, it.adaptivePacer)
            assertEquals("Incorrect unifiedEchoPkt", false, it.unifiedEchoPkt)
            assertEquals("Incorrect pacerInterval", 32, it.pacerInterval)
            assertEquals("Incorrect pktFilter", 0, it.pktFilter)
            assertEquals("Incorrect txRepetition", 0, it.txRepetition)
            assertEquals("Incorrect commOutputPower", 0, it.commOutputPower)
            assertEquals("Incorrect commPattern", 0, it.commPattern)
        }
    }
}