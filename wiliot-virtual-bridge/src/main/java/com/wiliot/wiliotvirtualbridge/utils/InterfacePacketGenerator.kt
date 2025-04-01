package com.wiliot.wiliotvirtualbridge.utils

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.model.MelModulePacket
import com.wiliot.wiliotcore.utils.ScanResultInternal
import com.wiliot.wiliotcore.utils.toHexString
import com.wiliot.wiliotvirtualbridge.BuildConfig
import java.nio.ByteBuffer

internal object InterfacePacketGenerator {

    internal fun generateInterfacePacket(
        sequenceId: Int,
        configHash: Int
    ): MelModulePacket {
        return MelModulePacket(
            value = generatePayload(sequenceId, configHash),
            scanResult = ScanResultInternal(
                rssi = 0,
                device = ScanResultInternal.Device(
                    name = null,
                    address = generateMacFromString(Wiliot.getFullGWId())
                ),
                isConnectable = false
            ),
            timestamp = System.currentTimeMillis()
        )
    }

    object BuildConfigWrapper {
        val libVersion: String
            get() = BuildConfig.LIB_VERSION
    }

    private fun generatePayload(
        seqId: Int,
        cfgHash: Int
    ): String {
        val module: Int = 1
        val msgType: Int = 1
        val apiVersion: Int = 10
        val brgMac: String = generateMacFromString(Wiliot.getFullGWId()).replace(":", "")
        val boardType: Int = 17
        val blVersion: Int = 0
        val majorVer: Int = BuildConfigWrapper.libVersion.takeMajor()
        val minorVer: Int = BuildConfigWrapper.libVersion.takeMinor()
        val patchVer: Int = BuildConfigWrapper.libVersion.takePatch()
        val supCapGlob: Boolean = false
        val supCapDatapath: Boolean = true
        val supCapEnergy2400: Boolean = false
        val supCapEnergySub1g: Boolean = false
        val supCapCalibration: Boolean = false
        val supCapPwrMgmt: Boolean = false
        val supCapSensors: Boolean = false
        val supCapCustom: Boolean = false

        val payload = generateUsefulPayload(
            moduleType = module,
            msgType = msgType,
            apiVersion = apiVersion,
            seqId = seqId,
            brgMac = brgMac,
            boardType = boardType,
            blVersion = blVersion,
            majorVer = majorVer,
            minorVer = minorVer,
            patchVer = patchVer,
            supCapGlob = supCapGlob,
            supCapDatapath = supCapDatapath,
            supCapEnergy2400 = supCapEnergy2400,
            supCapEnergySub1g = supCapEnergySub1g,
            supCapCalibration = supCapCalibration,
            supCapPwrMgmt = supCapPwrMgmt,
            supCapSensors = supCapSensors,
            supCapCustom = supCapCustom,
            cfgHash = cfgHash
        )

        return "c6fc0000ee$payload".uppercase()
    }

    private fun generateUsefulPayload(
        moduleType: Int,
        msgType: Int,
        apiVersion: Int,
        seqId: Int,
        brgMac: String,
        boardType: Int,
        blVersion: Int,
        majorVer: Int,
        minorVer: Int,
        patchVer: Int,
        supCapGlob: Boolean,
        supCapDatapath: Boolean,
        supCapEnergy2400: Boolean,
        supCapEnergySub1g: Boolean,
        supCapCalibration: Boolean,
        supCapPwrMgmt: Boolean,
        supCapSensors: Boolean,
        supCapCustom: Boolean,
        cfgHash: Int
    ): String {
        // Allocate 24 bytes for the packet
        val buffer = ByteBuffer.allocate(24)

        // Pack moduleType and msgType into 1 byte (4 bits each)
        buffer.put(((moduleType and 0x0F) shl 4 or (msgType and 0x0F)).toByte())

        // Pack apiVersion and seqId into 1 byte each
        buffer.put(apiVersion.toByte())
        buffer.put(seqId.toByte())

        // Pack brgMac (48 bits = 6 bytes)
        val macBytes = brgMac.hexStringToByteArray()
        buffer.put(macBytes)

        // Pack boardType, blVersion, majorVer, minorVer, patchVer (1 byte each)
        buffer.put(boardType.toByte())
        buffer.put(blVersion.toByte())
        buffer.put(majorVer.toByte())
        buffer.put(minorVer.toByte())
        buffer.put(patchVer.toByte())

        // Pack capabilities into 1 byte
        var capabilities = 0
        capabilities = capabilities or ((if (supCapGlob) 1 else 0) shl 7)
        capabilities = capabilities or ((if (supCapDatapath) 1 else 0) shl 6)
        capabilities = capabilities or ((if (supCapEnergy2400) 1 else 0) shl 5)
        capabilities = capabilities or ((if (supCapEnergySub1g) 1 else 0) shl 4)
        capabilities = capabilities or ((if (supCapCalibration) 1 else 0) shl 3)
        capabilities = capabilities or ((if (supCapPwrMgmt) 1 else 0) shl 2)
        capabilities = capabilities or ((if (supCapSensors) 1 else 0) shl 1)
        capabilities = capabilities or (if (supCapCustom) 1 else 0)
        buffer.put(capabilities.toByte())

        // Pack cfgHash (4 bytes)
        buffer.putInt(cfgHash)

        // Fill remaining 40 bits (5 bytes) with 0s
        buffer.put(ByteArray(5) { 0.toByte() })

        // Convert buffer to hex string
        return buffer.array().toHexString()
    }

    private fun String.takeMajor(): Int = this.split(".")[0].toInt()

    private fun String.takeMinor(): Int = this.split(".")[1].toInt()

    private fun String.takePatch(): Int = this.split(".")[2].toInt()


}