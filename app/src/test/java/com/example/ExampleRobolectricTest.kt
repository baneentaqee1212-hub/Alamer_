package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.TrustCredentials
import com.example.network.QrParserAndValidator
import com.example.network.UrlNormalizer
import com.example.security.SecureStorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ALAMER Caller", appName)
    }

    @Test
    fun `qr validator accepts compliant taloola-caller URI with port 5000`() {
        val validQr = QrParserAndValidator.buildTestQrString(
            host = "192.168.68.104",
            port = 5000,
            serverId = "SERVER_MAIN_1",
            name = "مطعم التجربة",
            ttlSeconds = 3600
        )
        val result = QrParserAndValidator.validate(validQr)
        assertTrue(result.isValid)
        assertNotNull(result.data)
        assertEquals("192.168.68.104", result.data?.host)
        assertEquals(5000, result.data?.port)
        assertEquals("SERVER_MAIN_1", result.data?.serverId)
        assertEquals("CallerAssistant", result.data?.type)
        assertEquals("1.0", result.data?.protocol)
    }

    @Test
    fun `qr validator rejects expired URI`() {
        val expPast = (System.currentTimeMillis() / 1000) - 100
        val expiredQr = "taloola-caller://pair?v=1&type=CallerAssistant&sid=SRV1&name=مطعم&host=192.168.68.104&port=5000&tls=0&proto=1.0&pid=p1&token=tok1&exp=$expPast"
        val result = QrParserAndValidator.validate(expiredQr)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("منتهي الصلاحية") })
    }

    @Test
    fun `qr validator rejects wrong device type`() {
        val wrongTypeQr = "taloola-caller://pair?v=1&type=CashierTerminal&sid=SRV1&name=مطعم&host=192.168.68.104&port=5000&tls=0&proto=1.0&pid=p1&token=tok1&exp=${(System.currentTimeMillis() / 1000) + 1000}"
        val result = QrParserAndValidator.validate(wrongTypeQr)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("نوع الجهاز") })
    }

    @Test
    fun `acceptance - old saved ServerId does not block fresh QR from another server`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = SecureStorageManager(context)

        val serverATrust = TrustCredentials(
            serverId = "SERVER_A_ID",
            serverName = "مطعم قديم",
            serverUrl = "http://192.168.1.10:5000",
            host = "192.168.1.10",
            port = 5000,
            tlsRequired = false,
            protocolVersion = "1.0",
            deviceId = "ALAMER-TEST-1",
            deviceName = "Alamer Caller",
            installationBinding = "inst-1",
            callerCredential = "cred-secret-A"
        )
        storage.saveTrust(serverATrust)

        val qrServerB = QrParserAndValidator.buildTestQrString(
            host = "192.168.68.104",
            port = 5000,
            serverId = "SERVER_B_NEW",
            name = "مطعم جديد",
            ttlSeconds = 3600
        )
        val parseResult = QrParserAndValidator.validate(qrServerB)
        assertTrue("QR parser must accept Server B QR independently of local storage", parseResult.isValid)
        assertNotNull(parseResult.data)

        val saved = storage.loadTrust()
        assertEquals("SERVER_A_ID", saved?.serverId)
        assertEquals("SERVER_B_NEW", parseResult.data?.serverId)
    }

    @Test
    fun `acceptance - endpoint normalization`() {
        val normalized = UrlNormalizer.normalize("192.168.68.104:5000")
        assertNotNull(normalized)
        assertEquals("http://192.168.68.104:5000", normalized?.fullUrl)
        assertEquals("192.168.68.104", normalized?.host)
        assertEquals(5000, normalized?.port)
        assertFalse(normalized?.tls ?: true)
    }
}
