package com.wiliot.wiliotupstream

import com.wiliot.wiliotcore.model.BridgeConfigPacketV2
import com.wiliot.wiliotcore.model.BridgeConfigPacketV3
import com.wiliot.wiliotcore.model.BridgeHbPacket
import com.wiliot.wiliotcore.model.BridgeHbPacketV5
import com.wiliot.wiliotcore.model.Packet
import com.wiliot.wiliotcore.utils.ScanResultInternal
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BridgeHbPacketV5Test {

    @get:Rule
    val mockkRule = MockKRule(this)

    companion object K {
        private const val rawPayload = "AFFD0000EE02058CD66B9C0AA8DB0052EA00472C0090F030C600110000"
        private const val SRC_MAC = "D66B9C0AA8DB"
    }


    @MockK
    private lateinit var btDevice: ScanResultInternal.Device
    @MockK
    private lateinit var scanResult: ScanResultInternal


    private lateinit var bridgeHbPacketV5: BridgeHbPacketV5


    @Before
    fun setUp() {
        every { btDevice.name } returns "NAME"
        every { btDevice.address } returns "ADDRESS"
        every { scanResult.device } returns btDevice
        every { scanResult.rssi } returns -42
        every { scanResult.isConnectable } returns false
        bridgeHbPacketV5 = BridgeHbPacketV5(rawPayload, scanResult)
    }

    @After
    fun tearDown() {
    }

    @Test
    fun `is not v2 config`() {
        assertFalse(Packet.from(rawPayload, scanResult) is BridgeConfigPacketV2)
    }

    @Test
    fun `is not v3 config`() {
        assertFalse(Packet.from(rawPayload, scanResult) is BridgeConfigPacketV3)
    }

    @Test
    fun `is not HB v1 packet`() {
        assertFalse(Packet.from(rawPayload, scanResult) is BridgeHbPacket)
    }

    @Test
    fun `is HB v5 packet`() {
        assert(Packet.from(rawPayload, scanResult) is BridgeHbPacketV5)
    }

    @Test
    fun non_wlt_rx_pkts(){
        assertEquals(21226, bridgeHbPacketV5.nonWltRxPktsCtr.toInt())
    }

    @Test
    fun bad_crc(){
        assertEquals(18220, bridgeHbPacketV5.badCrcPktsCtr.toInt())
    }

    @Test
    fun connectedTags(){
        assertEquals(17, bridgeHbPacketV5.tagsCtr.toInt())
    }

    @Test
    fun receivedPkts(){
        assertEquals(37104, bridgeHbPacketV5.wltRxPktsCtr.toInt())
    }

    @Test
    fun srcMac() {
        assertEquals(SRC_MAC, bridgeHbPacketV5.brgMac)
    }
}
