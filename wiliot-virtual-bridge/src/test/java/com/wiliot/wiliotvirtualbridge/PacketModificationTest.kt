package com.wiliot.wiliotvirtualbridge

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.model.Ble5EchoPacket
import com.wiliot.wiliotcore.model.DataPacketType
import com.wiliot.wiliotcore.model.Packet
import com.wiliot.wiliotcore.model.UnifiedEchoPacket
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

        private const val pixelBle4DataPayload = "AFFD050000EDB664D20344475A78AF83CEB5F35B4087BBD5ECC2A448EA"
        private const val pixelBle5DataPayload = "2616AFFD050080F509B668AFB4CA42A3681D3CC104228C54981B00D2DEDE9662BB33BC7510DC3C"
        private const val gwId = "A0A0B0C0C0D0D0"
        private val expectedAliasBridgeId = generateMacFromString(gwId).replace(":", "")
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
        every { Wiliot.getFullGWId() } returns gwId
    }

    private fun generatePacketBle4(payload: String, millisDecrement: Long): Packet {
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

    private fun generatePacketBle5(payload: String, millisDecrement: Long): Packet {
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

    private fun generatePacketGroupBle4(): PixelPacketGroup {
        return PixelPacketGroup(
            lastPaceTime = System.currentTimeMillis() - 1000,
            lastPacket = generatePacketBle4(payload = pixelBle4DataPayload, millisDecrement = System.currentTimeMillis() - 10),
            counter = 3
        )
    }

    private fun generatePacketGroupBle5(): PixelPacketGroup {
        return PixelPacketGroup(
            lastPaceTime = System.currentTimeMillis() - 1000,
            lastPacket = generatePacketBle5(payload = pixelBle5DataPayload, millisDecrement = System.currentTimeMillis() - 10),
            counter = 3
        )
    }

    @Test
    fun `Direct Pixel BLE4 Packet to UnifiedEchoPacket packet signature correct`() {
        val packets = generatePacketGroupBle4()
        val echo = packets.toEchoPacket()

        println("Echo: ${echo.value}")

        assertTrue(echo is UnifiedEchoPacket)
        assertEquals(expectedAliasBridgeId, (echo as UnifiedEchoPacket).aliasBridgeId)
        assertEquals(58, echo.value.length) // required packet length
        assertTrue("Output packet classified as UnifiedEchoPacket Packet", Packet.from(echo.value, scanResult) is UnifiedEchoPacket)
    }

    @Test
    fun `Direct Pixel BLE4 Packet to UnifiedEchoPacket packet adjustments applied correctly`() {

        val packets = generatePacketGroupBle4()
        val echo = packets.toEchoPacket()

        // 1. Validate the prefix was replaced correctly at bytes 0-1
        assertEquals(DataPacketType.RETRANSMITTED.prefix.uppercase(), echo.value.take(4)) // Check prefix replacement

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

    @Test
    fun `Direct Pixel BLE5 Packet to Ble5EchoPacket packet signature correct`() {
        val packets = generatePacketGroupBle5()
        val echo = packets.toEchoPacket()

        println("Echo: ${echo.value}")

        assertTrue(echo is Ble5EchoPacket)
        assertEquals(expectedAliasBridgeId, (echo as Ble5EchoPacket).aliasBridgeId)
        assertTrue(echo.value.length > pixelBle5DataPayload.length) // Length increased due to metadata
        assertTrue(echo.value.substringBytes(Pair(2, 3)).equals(DataPacketType.RETRANSMITTED.prefix, ignoreCase = true))
    }

    @Test
    fun `Direct Pixel BLE5 Packet to Ble5EchoPacket packet adjustments applied correctly`() {
        val packets = generatePacketGroupBle5()
        val echo = packets.toEchoPacket()

        // 1. Validate the length byte was incremented by 3
        val originalLength = pixelBle5DataPayload.substring(0, 2).toInt(16)
        val expectedLength = (originalLength + 3).toString(16).padStart(2, '0')
        assertEquals(expectedLength, echo.value.substring(0, 2))

        // 2. Validate the original payload is preserved (except for length byte and UUID)
        assertEquals(
            pixelBle5DataPayload.substring(2).replaceFirst(
                DataPacketType.DIRECT.prefix,
                DataPacketType.RETRANSMITTED.prefix.uppercase(),
                ignoreCase = true
            ),
            echo.value.substring(2, pixelBle5DataPayload.length)
        )

        // 3. Validate metadata was appended at the end
        val metadataLength = 6 // 6 hex chars
        val appendedMetadata = echo.value.substring(pixelBle5DataPayload.length)
        assertEquals(metadataLength, appendedMetadata.length)

        // 4. Validate computed metadata values
        val nfpkt = packets.counter
        val rssi = packets.lastPacket!!.scanRssi.twoComplement()
        val brgLatency = 0
        val globalPacing = 0
        val expectedMetadata = ((nfpkt and 0xFF) shl 16) or
                ((rssi and 0x3F) shl 10) or
                ((brgLatency and 0x3F) shl 4) or
                (globalPacing and 0x0F)
        val formattedMetadata = String.format("%06X", expectedMetadata)
        assertEquals(formattedMetadata, appendedMetadata)
    }

}
