package com.example.network

import com.example.model.PairResponse
import com.example.model.PairingResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Parsing robustness for TaloolaPos payloads.
 * Solves: Expected BEGIN_ARRAY but was NUMBER at path $.capabilities
 * Handles:
 * - capabilities: ["CallerAssistant"] (Array contract)
 * - capabilities: 2048 (Legacy bitmask contract)
 * - capabilities: [] or null
 * Redacts secrets from logs.
 */
object PairingResultParser {
    private const val TAG = "ALAMER_PARSER"
    private val moshi: Moshi = Moshi.Builder().build()
    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

    fun parse(rawJson: String): PairingResult {
        try {
            val map = mapAdapter.fromJson(rawJson) ?: emptyMap()

            fun getBool(vararg keys: String): Boolean {
                for (k in keys) {
                    val v = map[k]
                    if (v is Boolean) return v
                    if (v is String) return v.toBoolean()
                }
                return false
            }

            fun getString(vararg keys: String): String? {
                for (k in keys) {
                    val v = map[k]
                    if (v != null) return v.toString()
                }
                return null
            }

            val success = getBool("success", "Success")
            val errorCode = getString("errorCode", "ErrorCode")
            val message = getString("message", "Message", "errorMessage", "ErrorMessage")
            val serverId = getString("serverId", "ServerId") ?: ""
            val serverUrl = getString("serverUrl", "ServerUrl") ?: ""
            val protocolVersion = getString("protocolVersion", "ProtocolVersion") ?: "1.0"
            val deviceId = getString("deviceId", "DeviceId") ?: ""
            val deviceName = getString("deviceName", "DeviceName") ?: "Alamer Caller"
            val installationBinding = getString("installationBinding", "InstallationBinding") ?: ""
            val callerCredential = getString("callerCredential", "CallerCredential") ?: ""
            val deviceStatus = getString("deviceStatus", "DeviceStatus")

            val capabilitiesList = parseCapabilitiesFromMap(map)

            return PairingResult(
                success = success,
                errorCode = errorCode,
                message = message,
                serverId = serverId,
                serverUrl = serverUrl,
                protocolVersion = protocolVersion,
                deviceId = deviceId,
                deviceName = deviceName,
                installationBinding = installationBinding,
                callerCredential = callerCredential,
                deviceStatus = deviceStatus,
                capabilities = capabilitiesList
            )
        } catch (e: Exception) {
            logError("Failed parsing raw response: ${redactSensitiveJson(rawJson)}", e)
            return PairingResult(
                success = false,
                message = "خطأ في تحليل استجابة الخادم: ${e.message}"
            )
        }
    }

    fun toPairResponse(result: PairingResult): PairResponse {
        return PairResponse(
            success = result.success,
            serverId = result.serverId,
            serverUrl = result.serverUrl,
            protocolVersion = result.protocolVersion,
            deviceId = result.deviceId,
            deviceName = result.deviceName,
            installationBinding = result.installationBinding,
            callerCredential = result.callerCredential,
            deviceStatus = result.deviceStatus,
            capabilities = result.capabilities,
            errorMessage = result.message
        )
    }

    fun parseCapabilitiesFromMap(map: Map<String, Any?>): List<String> {
        val capRaw = map["capabilities"] ?: map["Capabilities"] ?: return emptyList()
        val result = mutableListOf<String>()
        when (capRaw) {
            is List<*> -> {
                for (item in capRaw) {
                    when (item) {
                        is String -> if (item.isNotBlank()) result.add(item)
                        is Number -> {
                            val num = item.toInt()
                            if (num == 2048 || (num and 2048) != 0) {
                                result.add("CallerAssistant")
                            } else if (num != 0) {
                                result.add("Capability_$num")
                            }
                        }
                    }
                }
            }
            is Number -> {
                val num = capRaw.toInt()
                if (num == 2048 || (num and 2048) != 0) {
                    result.add("CallerAssistant")
                } else if (num != 0) {
                    result.add("Capability_$num")
                }
            }
            is String -> {
                if (capRaw.isNotBlank()) {
                    result.add(capRaw)
                }
            }
        }
        return result
    }

    fun redactSensitiveJson(raw: String): String {
        return raw
            .replace(Regex("(?i)\"(callerCredential|token|installationBinding)\"\\s*:\\s*\"[^\"]*\""), "\"$1\": \"[REDACTED]\"")
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        try {
            android.util.Log.e(TAG, message, throwable)
        } catch (_: Throwable) {
            System.err.println("$TAG: $message")
            throwable?.printStackTrace()
        }
    }
}
