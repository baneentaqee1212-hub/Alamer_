package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.TrustCredentials
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android Keystore-backed secure storage for ALAMER Caller Assistant.
 * Stores persistent trust credentials safely without leaking to logs or plain storage.
 * Gracefully falls back to JCE AES-GCM when executing in JVM/Robolectric test environments.
 */
class SecureStorageManager(private val context: Context) {
    private val keyStoreAlias = "AlamerCallerAssistantKeyV1"
    private val keyStoreType = "AndroidKeyStore"
    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val prefs: SharedPreferences =
        context.getSharedPreferences("alamer_secure_vault", Context.MODE_PRIVATE)

    private var isUsingHardwareKeyStore = false
    private var fallbackKey: SecretKey? = null

    init {
        ensureKeyStoreKey()
    }

    private fun ensureKeyStoreKey() {
        try {
            val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
            if (!keyStore.containsAlias(keyStoreAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    keyStoreType
                )
                val spec = KeyGenParameterSpec.Builder(
                    keyStoreAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
            isUsingHardwareKeyStore = true
        } catch (e: Throwable) {
            isUsingHardwareKeyStore = false
            fallbackKey = getOrCreateFallbackKey()
        }
    }

    private fun getOrCreateFallbackKey(): SecretKey {
        val existing = prefs.getString("fallback_raw_key", null)
        if (existing != null) {
            val bytes = Base64.decode(existing, Base64.NO_WRAP)
            return SecretKeySpec(bytes, "AES")
        }
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256)
        val key = kg.generateKey()
        prefs.edit().putString("fallback_raw_key", Base64.encodeToString(key.encoded, Base64.NO_WRAP)).apply()
        return key
    }

    private fun getSecretKey(): SecretKey {
        return if (isUsingHardwareKeyStore) {
            val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
            keyStore.getKey(keyStoreAlias, null) as SecretKey
        } else {
            fallbackKey ?: getOrCreateFallbackKey().also { fallbackKey = it }
        }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String): String? {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < 12) return null
            val iv = ByteArray(12)
            val cipherText = ByteArray(combined.size - 12)
            System.arraycopy(combined, 0, iv, 0, 12)
            System.arraycopy(combined, 12, cipherText, 0, cipherText.size)
            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(gcmTagLength, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun getOrCreateInstallationBinding(): String {
        var binding = prefs.getString("inst_binding", null)
        if (binding == null) {
            binding = "inst-" + UUID.randomUUID().toString()
            prefs.edit().putString("inst_binding", binding).apply()
        }
        return binding
    }

    fun getOrCreateDeviceId(): String {
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            val shortRand = UUID.randomUUID().toString().substring(0, 8).uppercase()
            val model = Build.MODEL.replace(" ", "-").take(8)
            deviceId = "ALAMER-$model-$shortRand"
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    fun saveTrust(trust: TrustCredentials) {
        val encryptedCred = encrypt(trust.callerCredential)
        prefs.edit()
            .putString("server_id", trust.serverId)
            .putString("server_name", trust.serverName)
            .putString("server_url", trust.serverUrl)
            .putString("host", trust.host)
            .putInt("port", trust.port)
            .putBoolean("tls", trust.tlsRequired)
            .putString("proto_ver", trust.protocolVersion)
            .putString("device_id", trust.deviceId)
            .putString("device_name", trust.deviceName)
            .putString("inst_binding", trust.installationBinding)
            .putString("caller_cred_enc", encryptedCred)
            .putString("hub_path", trust.hubPath)
            .putLong("paired_at", trust.pairedAtEpochMs)
            .apply()
    }

    fun loadTrust(): TrustCredentials? {
        val serverId = prefs.getString("server_id", null) ?: return null
        val host = prefs.getString("host", null) ?: return null
        val encCred = prefs.getString("caller_cred_enc", null) ?: return null
        val callerCredential = decrypt(encCred) ?: return null
        val serverName = prefs.getString("server_name", "Taloola") ?: "Taloola"
        val port = prefs.getInt("port", 5000)
        val tls = prefs.getBoolean("tls", false)
        val protoVer = prefs.getString("proto_ver", "1.0") ?: "1.0"
        val deviceId = prefs.getString("device_id", getOrCreateDeviceId()) ?: getOrCreateDeviceId()
        val deviceName = prefs.getString("device_name", Build.MODEL) ?: Build.MODEL
        val instBinding = prefs.getString("inst_binding", getOrCreateInstallationBinding()) ?: getOrCreateInstallationBinding()
        val serverUrl = prefs.getString("server_url", "http://$host:$port") ?: "http://$host:$port"
        val hubPath = prefs.getString("hub_path", "/posHub") ?: "/posHub"
        val pairedAt = prefs.getLong("paired_at", System.currentTimeMillis())

        return TrustCredentials(
            serverId = serverId,
            serverName = serverName,
            serverUrl = serverUrl,
            host = host,
            port = port,
            tlsRequired = tls,
            protocolVersion = protoVer,
            deviceId = deviceId,
            deviceName = deviceName,
            installationBinding = instBinding,
            callerCredential = callerCredential,
            hubPath = hubPath,
            pairedAtEpochMs = pairedAt
        )
    }

    fun updateEndpoint(newHost: String, newPort: Int, newServerUrl: String?) {
        val editor = prefs.edit()
            .putString("host", newHost)
            .putInt("port", newPort)
        if (!newServerUrl.isNullOrBlank()) {
            editor.putString("server_url", newServerUrl)
        }
        editor.apply()
    }

    fun hasTrust(): Boolean {
        return prefs.contains("server_id") && prefs.contains("caller_cred_enc")
    }

    fun clearTrust() {
        prefs.edit()
            .remove("server_id")
            .remove("server_name")
            .remove("server_url")
            .remove("host")
            .remove("port")
            .remove("tls")
            .remove("caller_cred_enc")
            .remove("paired_at")
            .apply()
    }
}
