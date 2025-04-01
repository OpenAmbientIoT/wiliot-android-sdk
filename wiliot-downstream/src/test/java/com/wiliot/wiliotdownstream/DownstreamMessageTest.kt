package com.wiliot.wiliotdownstream

import com.google.gson.Gson
import com.wiliot.wiliotcore.model.DownlinkAction
import com.wiliot.wiliotcore.model.DownlinkActionMessage
import com.wiliot.wiliotcore.model.DownlinkConfigurationMessage
import com.wiliot.wiliotcore.model.DownlinkMessage
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownstreamMessageTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    lateinit var gson: Gson

    @Test
    fun `toDomainMessageOrNull returns DownlinkActionMessage when payload matches`() {
        val payload = """
            {
                "action": 0,
                "gatewayId":"0A0A0A0A0A0A",
                "txPacket":"1E16C6FC000000070900000000000000020000000000000000000000000000",
                "txMaxDurationMs":1000,
                "txMaxRetries":2
            }
        """.trimIndent()

        val downlinkMessage = DownlinkMessage(payload)
        val domainMessage = downlinkMessage.toDomainMessageOrNull() as? DownlinkActionMessage

        assertNotNull(domainMessage)
        assertEquals(DownlinkAction.ADVERTISE, domainMessage?.action)
    }

    @Test
    fun `toDomainMessageOrNull returns DownlinkConfigurationMessage when payload matches`() {
        val payload = """
            {
                "gatewayType":"mdk",
                "gatewayConf": {
                    "apiVersion":202,
                    "gatewayVersion":"3.17.0",
                    "additional": {
                        "upstreamEnabled":true,
                        "dataOutputTrafficFilter":"BRIDGES_ONLY"
                    }
                }
            }
        """.trimIndent()

        val downlinkMessage = DownlinkMessage(payload)
        val domainMessage = downlinkMessage.toDomainMessageOrNull() as? DownlinkConfigurationMessage

        assertNotNull(domainMessage)
    }

    @Test
    fun `toDomainMessageOrNull returns null when payload does not match`() {
        val payload = """
            {
                "random":123
            }
        """.trimIndent()

        val downlinkMessage = DownlinkMessage(payload)
        val domainMessage = downlinkMessage.toDomainMessageOrNull()

        assertEquals(null, domainMessage)
    }

}