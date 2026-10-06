package com.example.network

/**
 * Helper to build safe, normalized POS Hub URLs
 * Guarantees correct handling of /posHub, negotiation, and websocket URLs.
 */
object PosHubUrlBuilder {
    fun buildPosHubUrl(serverUrl: String): String {
        val trimmed = serverUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/posHub", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed/posHub"
        }
    }

    fun buildNegotiateUrl(serverUrl: String): String {
        val base = buildPosHubUrl(serverUrl)
        return "$base/negotiate?negotiateVersion=1"
    }

    fun buildWebSocketUrl(serverUrl: String, connectionToken: String? = null): String {
        val base = buildPosHubUrl(serverUrl)
        val wsBase = when {
            base.startsWith("https://", ignoreCase = true) -> base.replaceFirst("https://", "wss://", ignoreCase = true)
            base.startsWith("http://", ignoreCase = true) -> base.replaceFirst("http://", "ws://", ignoreCase = true)
            else -> "ws://$base"
        }
        return if (!connectionToken.isNullOrBlank()) {
            if (wsBase.contains("?")) "$wsBase&id=$connectionToken" else "$wsBase?id=$connectionToken"
        } else {
            wsBase
        }
    }
}
