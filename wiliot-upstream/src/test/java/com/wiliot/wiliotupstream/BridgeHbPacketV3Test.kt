package com.wiliot.wiliotupstream

import com.wiliot.wiliotcore.model.BridgeConfigPacketV2
import com.wiliot.wiliotcore.model.BridgeConfigPacketV3
import com.wiliot.wiliotcore.model.BridgeHbPacket
import com.wiliot.wiliotcore.model.Packet
import com.wiliot.wiliotcore.utils.ScanResultInternal
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BridgeHbPacketV3Test {

    @get:Rule
    val mockkRule = MockKRule(this)

    companion object K {
        private const val rawPayload = "AFFD0000EE02015DDEEDCD9D8A76AEEF895320C1001E04460008000000"
        private const val SRC_MAC = "AEEF895320C1"
    }


    @MockK
    private lateinit var btDevice: ScanResultInternal.Device
    @MockK
    private lateinit var scanResult: ScanResultInternal


    private lateinit var bridgeHbPacketV3: BridgeHbPacket


    @Before
    fun setUp() {
        every { btDevice.name } returns "NAME"
        every { btDevice.address } returns "ADDRESS"
        every { scanResult.device } returns btDevice
        every { scanResult.rssi } returns -42
        every { scanResult.isConnectable } returns false
        bridgeHbPacketV3 = BridgeHbPacket(rawPayload, scanResult)
    }

    @After
    fun tearDown() {
    }

    @Test
    fun `is not v2 config`() {
        Assert.assertFalse(Packet.from(rawPayload, scanResult) is BridgeConfigPacketV2)
    }

    @Test
    fun `is not v3 config`() {
        Assert.assertFalse(Packet.from(rawPayload, scanResult) is BridgeConfigPacketV3)
    }

    @Test
    fun `is HB packet`() {
        assert(Packet.from(rawPayload, scanResult) is BridgeHbPacket)
    }

    @Test
    fun pktsCount(){
        Assert.assertEquals(30, bridgeHbPacketV3.sentPktsCtr.toInt())
    }

    @Test
    fun nonWiliotPktsCount(){
        Assert.assertEquals(1094, bridgeHbPacketV3.nonWiliotPktsCtr.toInt())
    }

    @Test
    fun connectedTags(){
        Assert.assertEquals(8, bridgeHbPacketV3.tagsCtr.toInt())
    }

    @Test
    fun srcMac() {
        Assert.assertEquals(SRC_MAC, bridgeHbPacketV3.srcMac)
    }
}
