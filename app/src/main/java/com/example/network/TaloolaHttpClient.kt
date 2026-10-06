package com.example.network

import com.example.model.AlamerErrors
import com.example.model.PairInfoResponse
import com.example.model.PairRequest
import com.example.model.PairResponse
import com.example.model.ReconnectRequest
import com.example.model.ReconnectResponse
import com.example.model.ServerInfoResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Transport HTTP Client communicating with TaloolaPos Core Server (port 5000).
 * Handles:
 * - GET /api/server/info
 * - GET /api/caller-assistant/pair-info
 * - POST /api/caller-assistant/auto-pair
 * - POST /api/caller-assistant/pair
 * - POST /api/caller-assistant/reconnect
 * - POST /api/caller-assistant/heartbeat (fallback)
 * - POST /api/caller-assistant/call (fallback)
 * - GET /health
 */
class TaloolaHttpClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val serverInfoAdapter = moshi.adapter(ServerInfoResponse::class.java)
    private val pairInfoAdapter = moshi.adapter(PairInfoResponse::class.java)
    private val pairRequestAdapter = moshi.adapter(PairRequest::class.java)
    private val pairResponseAdapter = moshi.adapter(PairResponse::class.java)
    private val autoPairRequestAdapter = moshi.adapter(com.example.model.AutoPairRequest::class.java)
    private val reconnectRequestAdapter = moshi.adapter(ReconnectRequest::class.java)
    private val reconnectResponseAdapter = moshi.adapter(ReconnectResponse::class.java)
    private val heartbeatRequestAdapter = moshi.adapter(com.example.model.HeartbeatRequest::class.java)
    private val heartbeatResponseAdapter = moshi.adapter(com.example.model.HeartbeatResponse::class.java)
    private val httpCallRequestAdapter = moshi.adapter(com.example.model.HttpCallRequest::class.java)
    private val httpCallResponseAdapter = moshi.adapter(com.example.model.HttpCallResponse::class.java)

    /**
     * GET /api/server/info
     */
    suspend fun getServerInfo(host: String, port: Int, tls: Boolean = false): Result<ServerInfoResponse> =
        withContext(Dispatchers.IO) {
            val scheme = if (tls) "https" else "http"
            val url = "$scheme://$host:$port"
            getServerInfoByUrl(url)
        }

    /**
     * Manual URL & Server Info fetcher (GET /api/server/info)
     */
    suspend fun getServerInfoByUrl(baseUrl: String): Result<ServerInfoResponse> =
        withContext(Dispatchers.IO) {
            val cleanUrl = baseUrl.trim().trimEnd('/')
            val endpoint = "$cleanUrl/api/server/info"
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("User-Agent", "ALAMER-Caller-Assistant/1.0")
                .get()
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IOException(AlamerErrors.formatServerUnreachable(cleanUrl))
                        )
                    }
                    val bodyString = response.body?.string()
                        ?: return@withContext Result.failure(IOException("استجابة فارغة من الخادم"))
                    val info = serverInfoAdapter.fromJson(bodyString)
                        ?: return@withContext Result.failure(IOException("فشل تحليل بيانات الخادم"))
                    Result.success(info)
                }
            } catch (e: Exception) {
                Result.failure(IOException(AlamerErrors.formatServerUnreachable(cleanUrl), e))
            }
        }

    /**
     * POST /api/caller-assistant/auto-pair
     */
    suspend fun autoPair(baseUrl: String, autoPairRequest: com.example.model.AutoPairRequest): Result<PairResponse> =
        withContext(Dispatchers.IO) {
            val cleanUrl = baseUrl.trim().trimEnd('/')
            val endpoint = "$cleanUrl/api/caller-assistant/auto-pair"
            val jsonBody = autoPairRequestAdapter.toJson(autoPairRequest)

            val request = Request.Builder()
                .url(endpoint)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "ALAMER-Caller-Assistant/1.0")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (response.code == 404) {
                        return@withContext pairAutoFallback(cleanUrl, autoPairRequest)
                    }
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IOException(AlamerErrors.formatServerUnreachable(cleanUrl))
                        )
                    }
                    val pairingResult = PairingResultParser.parse(bodyString)
                    val pairResponse = PairingResultParser.toPairResponse(pairingResult)
                    if (!pairResponse.success) {
                        return@withContext Result.failure(
                            IOException(pairResponse.errorMessage ?: "فشل طلب الاقتران التلقائي")
                        )
                    }
                    Result.success(pairResponse)
                }
            } catch (e: Exception) {
                Result.failure(IOException(AlamerErrors.formatServerUnreachable(cleanUrl), e))
            }
        }

    private suspend fun pairAutoFallback(
        cleanUrl: String,
        autoPairRequest: com.example.model.AutoPairRequest
    ): Result<PairResponse> = withContext(Dispatchers.IO) {
        val endpoint = "$cleanUrl/api/caller-assistant/pair"
        val pairReq = PairRequest(
            version = autoPairRequest.version,
            deviceType = autoPairRequest.deviceType,
            deviceId = autoPairRequest.deviceId,
            deviceName = autoPairRequest.deviceName,
            installationBinding = autoPairRequest.installationBinding,
            protocolVersion = autoPairRequest.protocolVersion,
            autoPair = true
        )
        val jsonBody = pairRequestAdapter.toJson(pairReq)
        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "ALAMER-Caller-Assistant/1.0")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException(AlamerErrors.formatServerUnreachable(cleanUrl))
                    )
                }
                val pairingResult = PairingResultParser.parse(bodyString)
                val pairResponse = PairingResultParser.toPairResponse(pairingResult)
                if (!pairResponse.success) {
                    return@withContext Result.failure(
                        IOException(pairResponse.errorMessage ?: "فشل طلب الاقتران البديل")
                    )
                }
                Result.success(pairResponse)
            }
        } catch (e: Exception) {
            Result.failure(IOException(AlamerErrors.formatServerUnreachable(cleanUrl), e))
        }
    }

    /**
     * GET /api/caller-assistant/pair-info?pid=<QR.pid>
     */
    suspend fun getPairInfo(host: String, port: Int, tls: Boolean = false, pairingId: String): Result<PairInfoResponse> =
        withContext(Dispatchers.IO) {
            val scheme = if (tls) "https" else "http"
            val cleanUrl = "$scheme://$host:$port"
            val endpoint = "$cleanUrl/api/caller-assistant/pair-info?pid=$pairingId"
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("User-Agent", "ALAMER-Caller-Assistant/1.0")
                .get()
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.code == 404) {
                        val serverInfoRes = getServerInfo(host, port, tls)
                        if (serverInfoRes.isSuccess) {
                            val info = serverInfoRes.getOrThrow()
                            return@withContext Result.success(
                                PairInfoResponse(
                                    success = true,
                                    serverId = info.serverId,
                                    restaurantName = info.displayName,
                                    serverName = info.serverName,
                                    serverUrl = cleanUrl,
                                    pairingId = pairingId,
                                    protocolVersion = info.protocolVersion ?: "1.0"
                                )
                            )
                        }
                    }
                    if (!response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (response.code == 410) {
                            return@withContext Result.failure(IOException(AlamerErrors.formatPairingExpired()))
                        }
                        if (response.code == 409) {
                            return@withContext Result.failure(IOException(AlamerErrors.formatPairingAlreadyConsumed()))
                        }
                        return@withContext Result.failure(
                            IOException("فشل التحقق من الجلسة (HTTP ${response.code}): $body")
                        )
                    }
                    val bodyString = response.body?.string()
                        ?: return@withContext Result.failure(IOException("استجابة فارغة"))
                    val pairInfo = pairInfoAdapter.fromJson(bodyString)
                        ?: return@withContext Result.failure(IOException("فشل تحليل pair-info"))
                    if (!pairInfo.success) {
                        return@withContext Result.failure(
                            IOException(pairInfo.errorMessage ?: AlamerErrors.formatPairingInvalid())
                        )
                    }
                    Result.success(pairInfo)
                }
            } catch (e: Exception) {
                Result.failure(IOException(AlamerErrors.formatServerUnreachable(cleanUrl), e))
            }
        }

    /**
     * POST /api/caller-assistant/pair
     */
    suspend fun pair(host: String, port: Int, tls: Boolean = false, pairRequest: PairRequest): Result<PairResponse> =
        withContext(Dispatchers.IO) {
            val scheme = if (tls) "https" else "http"
            val url = "$scheme://$host:$port/api/caller-assistant/pair"
            val jsonBody = pairRequestAdapter.toJson(pairRequest)

            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "ALAMER-Caller-Assistant/1.0")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IOException("خطأ في الخادم (HTTP ${response.code}): $bodyString")
                        )
                    }
                    val pairingResult = PairingResultParser.parse(bodyString)
                    val pairResponse = PairingResultParser.toPairResponse(pairingResult)
                    if (!pairResponse.success) {
                        return@withContext Result.failure(
                            IOException(pairResponse.errorMessage ?: "فشل إتمام الاقتران")
                        )
                    }
                    Result.success(pairResponse)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Fallback Heartbeat: POST /api/caller-assistant/heartbeat
     */
    suspend fun heartbeat(
        baseUrl: String,
        heartbeatRequest: com.example.model.HeartbeatRequest
    ): Result<com.example.model.HeartbeatResponse> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val endpoint = "$cleanUrl/api/caller-assistant/heartbeat"
        val jsonBody = heartbeatRequestAdapter.toJson(heartbeatRequest)

        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "ALAMER-Caller-Assistant/1.0")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("فشل النبض (HTTP ${response.code})")
                    )
                }
                val bodyString = response.body?.string() ?: ""
                val res = try {
                    heartbeatResponseAdapter.fromJson(bodyString)
                } catch (e: Exception) {
                    null
                } ?: com.example.model.HeartbeatResponse(success = true)
                Result.success(res)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fallback Call Dispatch: POST /api/caller-assistant/call
     */
    suspend fun sendCallViaHttp(
        baseUrl: String,
        callRequest: com.example.model.HttpCallRequest
    ): Result<com.example.model.HttpCallResponse> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val endpoint = "$cleanUrl/api/caller-assistant/call"
        val jsonBody = httpCallRequestAdapter.toJson(callRequest)

        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "ALAMER-Caller-Assistant/1.0")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("فشل إرسال المكالمة عبر HTTP (HTTP ${response.code})")
                    )
                }
                val bodyString = response.body?.string() ?: ""
                val res = try {
                    httpCallResponseAdapter.fromJson(bodyString)
                } catch (e: Exception) {
                    null
                } ?: com.example.model.HttpCallResponse(success = true)
                Result.success(res)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * POST /api/caller-assistant/reconnect
     */
    suspend fun reconnect(host: String, port: Int, tls: Boolean = false, reconnectRequest: ReconnectRequest): Result<ReconnectResponse> =
        withContext(Dispatchers.IO) {
            val scheme = if (tls) "https" else "http"
            val url = "$scheme://$host:$port/api/caller-assistant/reconnect"
            val jsonBody = reconnectRequestAdapter.toJson(reconnectRequest)

            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "ALAMER-Caller-Assistant/1.0")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (response.code == 409) {
                        return@withContext Result.failure(
                            IllegalStateException("SERVER_CONFLICT_409")
                        )
                    }
                    if (response.code == 401 || response.code == 403) {
                        return@withContext Result.failure(
                            SecurityException("CREDENTIAL_INVALID")
                        )
                    }
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IOException(AlamerErrors.formatServerUnreachable(url))
                        )
                    }
                    val reconnectResponse = reconnectResponseAdapter.fromJson(bodyString)
                        ?: return@withContext Result.failure(IOException("استجابة فارغة في إعادة الاتصال"))
                    if (!reconnectResponse.success) {
                        if (reconnectResponse.deviceStatus.equals("REVOKED", ignoreCase = true)) {
                            return@withContext Result.failure(SecurityException("DEVICE_REVOKED"))
                        }
                        return@withContext Result.failure(
                            IOException(reconnectResponse.errorMessage ?: "فشلت إعادة الاتصال")
                        )
                    }
                    Result.success(reconnectResponse)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * GET /health
     */
    suspend fun checkHealth(host: String, port: Int, tls: Boolean = false): Result<Int> =
        withContext(Dispatchers.IO) {
            val scheme = if (tls) "https" else "http"
            val url = "$scheme://$host:$port/health"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    Result.success(response.code)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
