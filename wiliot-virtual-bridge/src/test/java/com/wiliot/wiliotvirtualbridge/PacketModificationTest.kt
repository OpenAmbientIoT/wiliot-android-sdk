package com.wiliot.wiliotvirtualbridge

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.model.CombinedSiPacket
import com.wiliot.wiliotcore.model.DataPacketType
import com.wiliot.wiliotcore.model.Packet
import com.wiliot.wiliotcore.utils.ScanResultInternal
import com.wiliot.wiliotcore.utils.twoComplement
import com.wiliot.wiliotvirtualbridge.repository.PixelPacketGroup
import com.wiliot.wiliotvirtualbridge.utils.DirectPixelPacketPacerTransformer.toEchoPacket
import com.wiliot.wiliotvirtualbridge.utils.generateMacFromString
import com.wiliot.wiliotvirtualbridge.utils.substringBytes
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PacketModificationTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    companion object {
        private const val mockDeviceAddress = "E76A825D4088"
        private const val mockScanRssi = -42

        private const val pixelDataPayload = "AFFD050000EDB664D20344475A78AF83CEB5F35B4087BBD5ECC2A448EA"
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

    private fun generatePacket(payload: String, millisDecrement: Long): Packet {
        return object : Packet {
            override val value: String
                get() = payload
            override val timestamp: Long
                get() = System.currentTimeMillis() - millisDecrement
            override val deviceMac: String
                get() = "AA:AA:AA:AA:AA:AA"
            override val scanRssi: Int
                get() = -3
        }
    }

    private fun generatePacketGroup(): PixelPacketGroup {
        return PixelPacketGroup(
            lastPaceTime = System.currentTimeMillis() - 1000,
            lastPacket = generatePacket(payload = pixelDataPayload, millisDecrement = System.currentTimeMillis() - 10),
            counter = 3
        )
    }

    @Test
    fun `Direct Pixel Packet to CombinedSI packet signature correct`() {
        val packets = generatePacketGroup()
        val echo = packets.toEchoPacket()

        println("Echo: ${echo.value}")

        assertEquals(generateMacFromString("A0A0B0B0C0C0D0D0").replace(":", ""), echo.aliasBridgeId)
        assertEquals(58, echo.value.length) // required packet length
        assertTrue("Output packet classified as CombinedSI Packet", Packet.from(echo.value, scanResult) is CombinedSiPacket)
    }

    @Test
    fun `Direct Pixel Packet to CombinedSI packet adjustments applied correctly`() {

        val packets = generatePacketGroup()
        val echo = packets.toEchoPacket()

        // 1. Validate the prefix was replaced correctly at bytes 0-1
        assertEquals(DataPacketType.RETRANSMITTED.prefix, echo.value.take(4)) // Check prefix replacement

        // 2. Validate GroupIdMajor adjustment (Byte 4) - adjusting with 0x3F
        val originalGroupIdMajor = packets.lastPacket!!.value.substringBytes(Pair(4, 4)).toUInt(16)
        val expectedGroupIdMajor = originalGroupIdMajor.or(0x3F.toUInt())
        val actualGroupIdMajor = echo.value.substringBytes(Pair(4, 4)).toUInt(16)
        assertEquals(expectedGroupIdMajor, actualGroupIdMajor)

        // 3. Validate computed value for bytes 15-17 (nfpkt, rssi, brgLatency, globalPacing)
        val nfpkt = packets.counter
        val rssi = packets.lastPacket.scanRssi.twoComplement()
        val brgLatency = 0
        val globalPacing = 0
        val expectedResult = ((nfpkt and 0xFF) shl 16) or
                ((rssi and 0x3F) shl 10) or
                ((brgLatency and 0x3F) shl 4) or
                (globalPacing and 0x0F)
        val formattedResult = String.format("%06X", expectedResult)
        assertEquals(formattedResult, echo.value.substringBytes(Pair(15, 17)))
    }


}
