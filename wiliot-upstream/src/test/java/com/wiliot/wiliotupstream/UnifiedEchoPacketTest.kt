package com.wiliot.wiliotupstream

import com.wiliot.wiliotcore.model.UnifiedEchoPacket
import com.wiliot.wiliotcore.model.Packet
import com.wiliot.wiliotcore.utils.ScanResultInternal
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UnifiedEchoPacketTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    companion object {
        private const val mockDeviceAddress = "B6:42:81:A6:7D:DD"
        private const val mockScanRssi = -42

        private const val combinedSiPayload = "C6FC02003F000000000000000000000000000013F000000000BA0D7557"
        private const val combinedSiPayload2 = "C6FC02003F000000000000000000000000000013F000000000BA0D11AD"
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
    }

    @Test
    fun `SI payload classified as CombinedSiPacket`() {
        assertTrue(Packet.from(combinedSiPayload, scanResult) is UnifiedEchoPacket)
    }

    @Test
    fun `CombinedSiPacket contains correct Bridge ID`() {
        assertTrue((Packet.from(combinedSiPayload, scanResult) as? UnifiedEchoPacket)?.aliasBridgeId == mockDeviceAddress.replace(":", ""))
    }

    @Test
    fun `CombinedSiPacket positive equal check works`() {
        val p1 = Packet.from(combinedSiPayload, scanResult) as? UnifiedEchoPacket
        val p2 = Packet.from(combinedSiPayload, scanResult) as? UnifiedEchoPacket
        assertTrue(p1 == p2)
    }

    @Test
    fun `CombinedSiPacket negative equal check works`() {
        val p1 = Packet.from(combinedSiPayload, scanResult) as? UnifiedEchoPacket
        val p2 = Packet.from(combinedSiPayload2, scanResult) as? UnifiedEchoPacket
        assertFalse(p1 == p2)
    }

    @Test
    fun `CombinedSiPacket contains RSSI from ScanResult`() {
        assertTrue((Packet.from(combinedSiPayload, scanResult) as? UnifiedEchoPacket)?.scanRssi == mockScanRssi)
    }

}