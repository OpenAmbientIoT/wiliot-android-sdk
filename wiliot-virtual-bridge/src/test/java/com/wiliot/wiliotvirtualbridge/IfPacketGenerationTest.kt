package com.wiliot.wiliotvirtualbridge

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.model.MelModulePacket
import com.wiliot.wiliotcore.model.Packet
import com.wiliot.wiliotcore.utils.ScanResultInternal
import com.wiliot.wiliotvirtualbridge.utils.InterfacePacketGenerator
import com.wiliot.wiliotvirtualbridge.utils.generateMacFromString
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class IfPacketGenerationTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    companion object {
        private const val mockDeviceAddress = "E76A825D4088"
        private const val mockScanRssi = -42
        private val cfgHash = "SOME_CONFIGURATION".hashCode()
        private const val libVer = "0.1.2"
        private const val seqId = 14
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
        mockkObject(InterfacePacketGenerator.BuildConfigWrapper)
        every { InterfacePacketGenerator.BuildConfigWrapper.libVersion } returns libVer
    }

    @Test
    fun `Interface Packet signature correct`() {
        val pkt = InterfacePacketGenerator.generateInterfacePacket(sequenceId = seqId, configHash = cfgHash)
        println("IF PACKET: ${pkt.value}")
        assertEquals(58, pkt.value.length)
        assertTrue(pkt.value.startsWith("C6FC0000EE"))
        assertTrue("Output packet classified as MEL Packet", Packet.from(pkt.value, scanResult) is MelModulePacket)
    }

    @Test
    fun `Interface packet generated correctly`() {
        val pkt = InterfacePacketGenerator.generateInterfacePacket(sequenceId = seqId, configHash = cfgHash)

        val expectedSeqIdHex = String.format("%02X", seqId)
        assertEquals(expectedSeqIdHex, pkt.value.substring(14, 16))

        val expectedMac = generateMacFromString(Wiliot.getFullGWId()).replace(":", "").uppercase()
        assertEquals(expectedMac, pkt.value.substring(16, 28))

        val capabilitiesByte = pkt.value.substring(38, 40).toInt(16)
        assertEquals(0b01000000, capabilitiesByte) // based on the boolean flags

        val expectedCfgHashHex = String.format("%08X", cfgHash).substring(0, 8) // truncate to 6 hex chars
        assertEquals(expectedCfgHashHex, pkt.value.substring(40, 48))

        assertEquals("0000000000", pkt.value.substring(48, 58))
    }

}