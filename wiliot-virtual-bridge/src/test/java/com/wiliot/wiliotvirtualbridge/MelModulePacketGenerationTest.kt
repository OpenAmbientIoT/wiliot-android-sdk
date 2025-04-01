package com.wiliot.wiliotvirtualbridge

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.config.VirtualBridgeConfig
import com.wiliot.wiliotcore.model.MelModulePacket
import com.wiliot.wiliotcore.model.Packet
import com.wiliot.wiliotcore.utils.ScanResultInternal
import com.wiliot.wiliotvirtualbridge.utils.MelModulePacketGenerator
import com.wiliot.wiliotvirtualbridge.utils.generateMacFromString
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import junit.framework.TestCase
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class MelModulePacketGenerationTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    companion object {
        private const val mockDeviceAddress = "E76A825D4088"
        private const val mockScanRssi = -42
        private const val seqId = 14
        private val config = VirtualBridgeConfig(
            pacingRate = 13_000
        )
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
    fun `MelModule Packet signature correct`() {
        val pkt = MelModulePacketGenerator.generateMelPacket(
            sequenceId = seqId,
            config = config
        )
        println("MEL MODULE PACKET: ${pkt.value}")
        TestCase.assertEquals(58, pkt.value.length)
        TestCase.assertTrue(pkt.value.startsWith("C6FC0000EE"))
        TestCase.assertTrue(
            "Output packet classified as MEL Packet",
            Packet.from(pkt.value, scanResult) is MelModulePacket
        )
    }

    @Test
    fun `MelModule Packet generated correctly`() {
        val pkt = MelModulePacketGenerator.generateMelPacket(
            sequenceId = seqId,
            config = config
        )

        // Print out the packet value for debugging
        println("Generated MEL Packet: ${pkt.value}")

        // Verify the length of the generated packet (it should be 58 characters in hex, i.e., 29 bytes)
        TestCase.assertEquals(58, pkt.value.length)

        // Check that it starts with the correct static header "C6FC0000EE"
        TestCase.assertTrue(pkt.value.startsWith("C6FC0000EE"))

        // Extract portions of the packet and verify their values
        // Header is "C6FC0000EE"
        val payload = pkt.value.substring(10)

        // ModuleType and MsgType (4 bits each)
        val moduleTypeAndMsgType = payload.substring(0, 2).toInt(16)
        val extractedModuleType = (moduleTypeAndMsgType shr 4) and 0x0F
        val extractedMsgType = moduleTypeAndMsgType and 0x0F
        TestCase.assertEquals(2, extractedModuleType)
        TestCase.assertEquals(5, extractedMsgType)

        // ApiVersion and SeqId (8 bits each)
        val apiVersion = payload.substring(2, 4).toInt(16)
        val seqIdExtracted = payload.substring(4, 6).toInt(16)
        TestCase.assertEquals(10, apiVersion)
        TestCase.assertEquals(seqId, seqIdExtracted)

        // BrgMac (48 bits = 12 hex characters)
        val expectedMac = generateMacFromString(Wiliot.getFullGWId()).replace(":", "").uppercase()
        val macString = payload.substring(6, 18)
        TestCase.assertEquals(expectedMac, macString)

        // GlobalPacingGroup (4 bits), AdaptivePacer (1 bit), UnifiedEchoPkt (1 bit)
        val pacingByte = payload.substring(18, 20).toInt(16)
        val globalPacingGroup = (pacingByte shr 4) and 0x0F
        val adaptivePacer = (pacingByte shr 1) and 0x01
        val unifiedEchoPkt = pacingByte and 0x01
        TestCase.assertEquals(0, globalPacingGroup)
        TestCase.assertEquals(0, adaptivePacer)
        TestCase.assertEquals(1, unifiedEchoPkt)

        // PacerInterval (16 bits)
        val pacerInterval = payload.substring(20, 24).toInt(16)
        TestCase.assertEquals((TimeUnit.MILLISECONDS.toSeconds(config.pacingRate)).toInt(), pacerInterval)

        // PktFilter and TxRepetition (5 bits and 3 bits)
        val filterAndRepetition = payload.substring(24, 26).toInt(16)
        val pktFilter = (filterAndRepetition shr 3) and 0x1F
        val txRepetition = filterAndRepetition and 0x07
        TestCase.assertEquals(0x00, pktFilter)
        TestCase.assertEquals(0, txRepetition)

        // CommOutputPower (8 bits)
        val commOutputPower = payload.substring(26, 28).toInt(16)
        TestCase.assertEquals(0, commOutputPower)

        // CommPattern (4 bits) and padding (4 bits)
        val patternAndPadding = payload.substring(28, 30).toInt(16)
        val commPattern = (patternAndPadding shr 4) and 0x0F
        TestCase.assertEquals(0, commPattern)

        // Ensure unused/padding bits at the end are all zeroes
        val padding = payload.substring(28)
        TestCase.assertEquals("00000000000000000000", padding)

        println("All checks passed for generated MEL Module Packet.")
    }

}