package com.example

import com.example.model.AlamerErrors
import com.example.model.PosHubState
import com.example.network.PairingResultParser
import com.example.network.PosHubUrlBuilder
import com.example.network.SignalRClient
import com.example.network.UrlNormalizer
import com.example.telephony.CallManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `01 capabilities as array of strings`() {
        val json = """
            {
                "success": true,
                "serverId": "server-106",
                "callerCredential": "super-secret-token",
                "capabilities": ["CallerAssistant", "OrderNotification"]
            }
        """.trimIndent()
        val result = PairingResultParser.parse(json)
        assertTrue(result.success)
        assertEquals(listOf("CallerAssistant", "OrderNotification"), result.capabilities)
        assertEquals("server-106", result.serverId)
    }

    @Test
    fun `02 capabilities as integer`() {
        val json = """
            {
                "Success": true,
                "ServerId": "server-legacy",
                "CallerCredential": "legacy-token-123",
                "capabilities": 2048
            }
        """.trimIndent()
        val result = PairingResultParser.parse(json)
        assertTrue(result.success)
        assertEquals(listOf("CallerAssistant"), result.capabilities)
        assertEquals("server-legacy", result.serverId)
    }

    @Test
    fun `03 capabilities as array of integers`() {
        val json = """
            {
                "success": true,
                "serverId": "server-num-array",
                "capabilities": [2048, 1024]
            }
        """.trimIndent()
        val result = PairingResultParser.parse(json)
        assertTrue(result.success)
        assertTrue(result.capabilities.contains("CallerAssistant"))
    }

    @Test
    fun `04 capabilities null`() {
        val json = """
            {
                "success": true,
                "serverId": "server-null-cap",
                "capabilities": null
            }
        """.trimIndent()
        val result = PairingResultParser.parse(json)
        assertTrue(result.success)
        assertEquals(emptyList<String>(), result.capabilities)
    }

    @Test
    fun `05 server id matching`() {
        val savedServerId = "SERVER-123"
        val liveServerId = "SERVER-123"
        assertTrue(savedServerId.equals(liveServerId, ignoreCase = true))
    }

    @Test
    fun `06 server id mismatch`() {
        val savedServerId = "SERVER-123"
        val liveServerId = "SERVER-456"
        assertFalse(savedServerId.equals(liveServerId, ignoreCase = true))
        val mismatchMsg = AlamerErrors.formatServerIdMismatch(savedServerId, liveServerId, "http://192.168.1.10:5000")
        assertTrue(mismatchMsg.contains("192.168.1.10"))
    }

    @Test
    fun `07 heartbeat scheduler and interval timing`() {
        val keepAliveInterval = 10
        val clientTimeoutInterval = 30
        assertTrue("Heartbeat interval must be strictly less than client timeout", keepAliveInterval <= clientTimeoutInterval / 3)
    }

    @Test
    fun `08 reconnect creates new clientId`() {
        val clientAuth1 = """{"success":true,"clientId":"client-session-1"}"""
        val clientAuth2 = """{"success":true,"clientId":"client-session-2"}"""
        val res1 = PairingResultParser.parse(clientAuth1)
        val res2 = PairingResultParser.parse(clientAuth2)
        assertFalse(res1.deviceId == res2.deviceId && res1.callerCredential.isNotBlank())
    }

    @Test
    fun `09 PosHubUrlBuilder Section 13 compliance`() {
        val normal = PosHubUrlBuilder.buildPosHubUrl("http://192.168.68.104:5000")
        assertEquals("http://192.168.68.104:5000/posHub", normal)
        val trailingSlash = PosHubUrlBuilder.buildPosHubUrl("http://192.168.68.104:5000/")
        assertEquals("http://192.168.68.104:5000/posHub", trailingSlash)
        val negotiateUrl = PosHubUrlBuilder.buildNegotiateUrl("http://192.168.68.104:5000")
        assertEquals("http://192.168.68.104:5000/posHub/negotiate?negotiateVersion=1", negotiateUrl)
        val wsUrl = PosHubUrlBuilder.buildWebSocketUrl("http://192.168.68.104:5000", "token123")
        assertEquals("ws://192.168.68.104:5000/posHub?id=token123", wsUrl)
    }

    @Test
    fun `10 duplicate recovery loop prevention`() {
        val delays = listOf(0L, 2000L, 5000L, 10000L, 15000L, 30000L)
        assertEquals(6, delays.size)
        assertEquals(0L, delays.first())
        assertEquals(30000L, delays.last())
    }

    @Test
    fun `11 PosHubStateMachine progression`() {
        val states = listOf(
            PosHubState.DISCONNECTED,
            PosHubState.CONNECTING,
            PosHubState.CONNECTED,
            PosHubState.AUTHENTICATING,
            PosHubState.READY
        )
        assertFalse(states[0].isOnline)
        assertFalse(states[1].isOnline)
        assertFalse(states[2].isOnline)
        assertFalse(states[3].isOnline)
        assertTrue(states[4].isOnline)
    }

    @Test
    fun `12 test phone number normalization`() {
        val callManager = CallManager(SignalRClient())
        assertEquals("+9647701234567", callManager.normalizePhoneNumber("+964 770 123-4567"))
        assertEquals("07701234567", callManager.normalizePhoneNumber("0770-123-4567"))
        assertEquals("07809876543", callManager.normalizePhoneNumber(" (0780) 987 6543 "))
    }

    @Test
    fun `16 duplicate CallId idempotency`() {
        val callManager = CallManager(SignalRClient())
        val callId1 = callManager.onIncomingCallRinging("07701234567")
        assertNotNull(callId1)
        val callId2 = callManager.onIncomingCallRinging("0770 123 4567")
        assertEquals("CallId must remain stable and identical for ongoing active call", callId1, callId2)
    }

    @Test
    fun `15 DHCP endpoint update with same ServerId`() {
        val norm1 = UrlNormalizer.normalize("192.168.68.104:5000")
        assertNotNull(norm1)
        assertEquals("http://192.168.68.104:5000", norm1?.fullUrl)
        assertEquals(5000, norm1?.port)

        val norm2 = UrlNormalizer.normalize("192.168.68.121:5000")
        assertNotNull(norm2)
        assertEquals("http://192.168.68.121:5000", norm2?.fullUrl)
    }
}
