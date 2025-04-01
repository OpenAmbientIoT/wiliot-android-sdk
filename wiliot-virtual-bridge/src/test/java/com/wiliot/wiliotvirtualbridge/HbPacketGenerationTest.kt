package com.wiliot.wiliotvirtualbridge

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.model.BridgeHbPacketAbstract
import com.wiliot.wiliotcore.model.Packet
import com.wiliot.wiliotcore.utils.ScanResultInternal
import com.wiliot.wiliotvirtualbridge.utils.HeartBeatPacketGenerator
import com.wiliot.wiliotvirtualbridge.utils.generateMacFromString
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import junit.framework.TestCase
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HbPacketGenerationTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    companion object {
        private const val mockDeviceAddress = "E76A825D4088"
        private const val mockScanRssi = -42
        private const val seqId = 14
        private const val tx = 88
        private const val rx = 99
        private const val unique = 4
    }

    @MockK
    private lateinit var btDevice: ScanResultInternal.Device
    @MockK
    private lateinit var scanResult: ScanResultInternal

    @Before
    fun setUp() {
        every { btDevice.name } returns "NAME"
        every { btDevice.address } returns mockDeviceAddress
        every { scanResult.device } returns btDevice
        every { scanResult.isConnectable } returns false
        every { scanResult.rssi } returns mockScanRssi
        mockkObject(Wiliot)
        every { Wiliot.getFullGWId() } returns "A0A0B0B0C0C0D0D0"
    }

    @Test
    fun `HB Packet signature correct`() {
        val pkt = HeartBeatPacketGenerator.generateHeartbeatPacket(sequenceId = seqId, receivedPackets = rx, sentPackets = tx, uniqueTagsMacAddresses = unique)
        println("HB PACKET: ${pkt.value}")
        TestCase.assertEquals(58, pkt.value.length)
        TestCase.assertTrue(pkt.value.startsWith("C6FC0000EE"))
        TestCase.assertTrue(
            "Output packet classified as HB Packet",
            Packet.from(pkt.value, scanResult) is BridgeHbPacketAbstract
        )
    }

    @Test
    fun `HB Packet generated correctly`() {
        val pkt = HeartBeatPacketGenerator.generateHeartbeatPacket(
            sequenceId = seqId,
            receivedPackets = rx,
            sentPackets = tx,
            uniqueTagsMacAddresses = unique
        )

        // Extract the generated packet value
        val payload = pkt.value

        // Validate fixed header fields
        TestCase.assertEquals("C6FC0000EE", payload.substring(0, 10))  // Fixed header

        // Extract each part of the payload for validation
        val msgType = payload.substring(10, 12).toInt(16)
        val apiVersion = payload.substring(12, 14).toInt(16)
        val seqIdExtracted = payload.substring(14, 16).toInt(16)
        val brgMac = payload.substring(16, 28)
        val nonWltRxPktsCtr = payload.substring(28, 34).toInt(16)
        val badCrcPktsCtr = payload.substring(34, 40).toInt(16)
        val wltRxPktsCtr = payload.substring(40, 46).toInt(16)
        val wltTxPktsCtr = payload.substring(46, 50).toInt(16)
        val tagsCtr = payload.substring(50, 54).toInt(16)
        val txQueueWatermark = payload.substring(54, 56).toInt(16)
        val capabilitiesByte = payload.substring(56, 58).toInt(16)

        val expectedMac = generateMacFromString(Wiliot.getFullGWId()).replace(":", "").uppercase()

        // Assertions
        TestCase.assertEquals(2, msgType)
        TestCase.assertEquals(10, apiVersion)
        TestCase.assertEquals(seqId, seqIdExtracted)
        TestCase.assertEquals(expectedMac, brgMac)  // Assuming brgMac is formatted similarly
        TestCase.assertEquals(0, nonWltRxPktsCtr)  // Fixed value in generator
        TestCase.assertEquals(0, badCrcPktsCtr)  // Fixed value in generator
        TestCase.assertEquals(rx, wltRxPktsCtr)
        TestCase.assertEquals(tx, wltTxPktsCtr)
        TestCase.assertEquals(unique, tagsCtr)
        TestCase.assertEquals(0, txQueueWatermark)  // Fixed value in generator

        // Decode capabilities byte
        val dynamic = (capabilitiesByte shr 7) and 0x1 == 1
        val effectivePacerIncrement = capabilitiesByte and 0x7F

        // Assertions for capabilities
        TestCase.assertFalse(dynamic)  // Fixed as false in generator
        TestCase.assertEquals(0, effectivePacerIncrement)  // Fixed value in generator

        println("HB Packet generated correctly: ${pkt.value}")
    }

}