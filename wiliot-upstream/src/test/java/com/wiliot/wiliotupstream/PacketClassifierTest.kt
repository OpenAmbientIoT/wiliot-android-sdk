package com.wiliot.wiliotupstream

import android.util.SparseArray
import com.wiliot.wiliotcore.model.BridgeConfigPacketV5
import com.wiliot.wiliotcore.model.BridgeHbPacket
import com.wiliot.wiliotcore.model.BridgeHbPacketV5
import com.wiliot.wiliotcore.model.UnifiedEchoPacket
import com.wiliot.wiliotcore.model.DataPacket
import com.wiliot.wiliotcore.model.ExternalSensorPacket
import com.wiliot.wiliotcore.model.MelModulePacket
import com.wiliot.wiliotcore.model.MetaPacket
import com.wiliot.wiliotcore.model.Packet
import com.wiliot.wiliotcore.utils.ScanResultInternal
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PacketClassifierTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    companion object {
        private const val mockDeviceAddress = "E76A825D4088"
        private const val mockScanRssi = -42

        private const val dataPayload = "affd0200005ef94aa9f15878aa1a776c7e906ae707158b06966ab6f5b9"
        private const val sideSensorMetaPayload = "C6FC0000EBE76A825D40880000460000000000000000000000A87B15EC"
        private const val sideSensorMetaPayloadFc90 = "90FC0000EBE76A825D40880000460000000000000000000000A87B15EC"
        private const val sideSensorDataPayloadFc90 = "90FC0200005ef94aa9f15878aa1a776c7e906ae707158b06966ab6f5b9"
        private const val metaPayload = "C6FC0000ECE76A825D40880000460000000000000000000000A87B15EC"
        private const val combinedSiPayload = "C6FC02003F000000000000000000000000000013F000000000BA0D7557"
        private const val brgHbV5payload = "AFFD0000EE02058CD66B9C0AA8DB0052EA00472C0090F030C600110000"
        private const val brgHbV1payload = "AFFD0000EE02015DDEEDCD9D8A76AEEF895320C1001E04460008000000"
        private const val brgConfigV5plusPayload = "C6FC0000EE010859000600120200E76A825D40880310600F061903000A"
        private const val brgMelPayload = "C6FC0000EE11084E1D42A11259F60212031060EEC50DF2C00000000000"
        private const val pixelBle5DataPayload = "2616AFFD050080F509B668AFB4CA42A3681D3CC104228C54981B00D2DEDE9662BB33BC7510DC3C"
    }

    @MockK
    private lateinit var btDevice: ScanResultInternal.Device
    @MockK
    private lateinit var scanResult: ScanResultInternal

    @Before
    fun setUp() {
        mockkStatic(android.os.ParcelUuid::class)
        val mockedUuid = mockk<android.os.ParcelUuid>()
        every { android.os.ParcelUuid.fromString(any()) } returns mockedUuid

        every { btDevice.name } returns "NAME"
        every { btDevice.address } returns mockDeviceAddress
        every { scanResult.device } returns btDevice
        every { scanResult.isConnectable } returns false
        every { scanResult.rssi } returns mockScanRssi

        val scanRecord = ScanResultInternal.ScanRecord(
            serviceData = mapOf(),
            manufacturerSpecificData = SparseArray<ByteArray>(),
            deviceName = "NAME"
        )
        scanRecord.raw = pixelBle5DataPayload

        every { scanResult.scanRecord } returns scanRecord
    }

    @Test
    fun `Pixel BLE5 Data payload classified as DataPacket`() {
        assertTrue(Packet.from(pixelBle5DataPayload, scanResult) is DataPacket)
    }

    @Test
    fun `SI payload classified as CombinedSiPacket`() {
        assertTrue(Packet.from(combinedSiPayload, scanResult) is UnifiedEchoPacket)
    }

    @Test
    fun `V5HB payload classified as BridgeHbPacketV5`() {
        assertTrue(Packet.from(brgHbV5payload, scanResult) is BridgeHbPacketV5)
    }

    @Test
    fun `V1HB payload classified as BridgeHbPacket`() {
        assertTrue(Packet.from(brgHbV1payload, scanResult) is BridgeHbPacket)
    }

    @Test
    fun `Meta payload classified as MetaPacket`() {
        assertTrue(Packet.from(metaPayload, scanResult) is MetaPacket)
    }

    @Test
    fun `SideSensor payload classified as ExternalSensorPacket`() {
        assertTrue(Packet.from(sideSensorMetaPayload, scanResult) is ExternalSensorPacket)
    }

    @Test
    fun `SideSensor Meta fc90 payload classified as ExternalSensorPacket`() {
        assertTrue(Packet.from(sideSensorMetaPayloadFc90, scanResult) is ExternalSensorPacket)
    }

    @Test
    fun `SideSensor Data fc90 payload classified as DataPacket`() {
        assertTrue(Packet.from(sideSensorDataPayloadFc90, scanResult) is DataPacket)
    }

    @Test
    fun `Data payload classified as DataPacket`() {
        assertTrue(Packet.from(dataPayload, scanResult) is DataPacket)
    }

    @Test
    fun `Mel payload classified as MelModulePacket`() {
        assertTrue(Packet.from(brgMelPayload, scanResult) is MelModulePacket)
    }

    @Test
    fun `BrgConfigV5+ payload classified as BridgeConfigPacketV5`() {
        assertTrue(Packet.from(brgConfigV5plusPayload, scanResult) is BridgeConfigPacketV5)
    }

}
