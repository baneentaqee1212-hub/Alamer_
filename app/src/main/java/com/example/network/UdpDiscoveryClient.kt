package com.example.network

import com.example.model.ServerInfoResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class DiscoveredServer(
    val serverId: String,
    val host: String,
    val port: Int,
    val serverUrl: String,
    val serverName: String
)

/**
 * DHCP Recovery:
 * Recovers changed IP of Taloola server via UDP broadcast or subnet scan.
 */
class UdpDiscoveryClient(
    private val httpClient: TaloolaHttpClient = TaloolaHttpClient()
) {
    suspend fun discoverServer(expectedServerId: String, timeoutMs: Int = 3000): DiscoveredServer? =
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = timeoutMs
                }
                val payload = "{\"Type\":\"Discover\",\"Client\":\"CallerAssistant\",\"Protocol\":\"1.0\"}".toByteArray()
                val targetPorts = listOf(5051, 5000)
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                for (p in targetPorts) {
                    val packet = DatagramPacket(payload, payload.size, broadcastAddress, p)
                    socket.send(packet)
                }

                val buffer = ByteArray(2048)
                val responsePacket = DatagramPacket(buffer, buffer.size)
                val endTime = System.currentTimeMillis() + timeoutMs

                while (System.currentTimeMillis() < endTime) {
                    try {
                        socket.receive(responsePacket)
                        val receivedText = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8)
                        val senderIp = responsePacket.address.hostAddress ?: ""

                        val json = try { JSONObject(receivedText) } catch (e: Exception) { null }
                        if (json != null) {
                            val sid = json.optString("ServerId", "")
                            if (sid.equals(expectedServerId, ignoreCase = true)) {
                                val bridgePort = json.optInt("CallerAssistantBridgePort", json.optInt("Port", 5000))
                                val serverUrl = json.optString("CallerAssistantServerUrl", "http://$senderIp:$bridgePort")
                                val name = json.optString("ServerName", "Taloola")
                                return@withContext DiscoveredServer(
                                    serverId = sid,
                                    host = senderIp,
                                    port = bridgePort,
                                    serverUrl = serverUrl,
                                    serverName = name
                                )
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                }
            } catch (e: Exception) {
                // Ignore UDP errors and proceed to active subnet probe
            } finally {
                socket?.close()
            }
            null
        }

    suspend fun probeSubnetForServer(localIp: String, expectedServerId: String): DiscoveredServer? =
        withContext(Dispatchers.IO) {
            val parts = localIp.split(".")
            if (parts.size != 4) return@withContext null
            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"

            val candidateHosts = mutableListOf<String>()
            for (i in 1..25) candidateHosts.add("$prefix.$i")
            for (i in 100..115) candidateHosts.add("$prefix.$i")

            for (host in candidateHosts) {
                if (host == localIp) continue
                try {
                    val result = httpClient.getServerInfo(host, 5000)
                    if (result.isSuccess) {
                        val info = result.getOrNull()
                        if (info?.serverId.equals(expectedServerId, ignoreCase = true)) {
                            return@withContext DiscoveredServer(
                                serverId = expectedServerId,
                                host = host,
                                port = info?.port ?: 5000,
                                serverUrl = "http://$host:${info?.port ?: 5000}",
                                serverName = info?.displayName ?: "Taloola"
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Continue probe
                }
            }
            null
        }
}
