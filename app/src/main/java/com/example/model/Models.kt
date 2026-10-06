package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * POS Hub States
 */
enum class PosHubState(val arabicLabel: String, val englishLabel: String) {
    DISCONNECTED("غير متصل", "Disconnected"),
    CONNECTING("جارٍ الاتصال...", "Connecting"),
    CONNECTED("تم فتح قناة النقل", "Connected"),
    AUTHENTICATING("جارٍ المصادقة مع Taloola...", "Authenticating"),
    READY("متصل ومستقر (جاهز)", "Ready"),
    RECONNECTING("جارٍ إعادة الاتصال...", "Reconnecting"),
    STOPPING("جارٍ الإيقاف", "Stopping"),
    FAILED("فشل الاتصال", "Failed");

    val isOnline: Boolean get() = this == READY
}

/**
 * Main Application Connection States
 */
enum class ConnectionState(val arabicLabel: String, val englishLabel: String) {
    UNINITIALIZED("غير مهيأ", "Uninitialized"),
    NEEDS_SERVER_URL("بحاجة لعنوان الخادم", "Needs Server URL"),
    NEEDS_PAIRING("بانتظار الاقتران", "Needs Pairing"),
    DISCOVERING("جارٍ استكشاف الشبكة...", "Discovering"),
    PAIRING("جارٍ الاقتران...", "Pairing"),
    PAIRING_RESPONSE_VALIDATING("جارٍ التحقق من بيانات الخادم...", "Validating Response"),
    CREDENTIAL_SAVED("تم حفظ بيانات الاعتماد", "Credential Saved"),
    SIGNALR_CONNECTING("جارٍ ربط SignalR...", "Connecting SignalR"),
    AUTHENTICATING("جارٍ المصادقة مع Taloola...", "Authenticating"),
    READY_SIGNALR("متصل ومستقر (SignalR)", "Ready (SignalR)"),
    READY_HTTP_FALLBACK("متصل (HTTP احتياطي)", "Ready (HTTP Fallback)"),
    READY("متصل ومستقر", "Ready / Connected"),
    PAIRING_SUCCESS("تم الاقتران بنجاح", "Pairing Success"),
    CONNECTING("جارٍ الاتصال...", "Connecting"),
    NETWORK_UNAVAILABLE("شبكة Wi-Fi غير متاحة", "Network Unavailable"),
    SERVER_UNAVAILABLE("الخادم غير متاح", "Server Unavailable"),
    SERVER_ID_MISMATCH("معرف الخادم مختلف", "Server ID Mismatch"),
    CREDENTIAL_INVALID("بيانات الاعتماد غير صالحة", "Credential Invalid"),
    DEVICE_REVOKED("تم إلغاء ترخيص الجهاز", "Device Revoked"),
    PROTOCOL_MISMATCH("إصدار البروتوكول غير متوافق", "Protocol Mismatch"),
    NEEDS_REPAIR("يتطلب إعادة الاقتران", "Needs Re-pair");

    val isConnected: Boolean
        get() = this == READY_SIGNALR || this == READY_HTTP_FALLBACK || this == READY

    val isConnecting: Boolean
        get() = this == DISCOVERING || this == PAIRING || this == PAIRING_RESPONSE_VALIDATING ||
                this == CREDENTIAL_SAVED || this == SIGNALR_CONNECTING || this == AUTHENTICATING ||
                this == CONNECTING
}

/**
 * QR Code parsed data: taloola-caller://pair
 */
data class QrPairingData(
    val version: String,
    val type: String,
    val serverId: String,
    val name: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val protocol: String,
    val pairingId: String,
    val token: String,
    val expiryEpochSeconds: Long,
    val rawUri: String
)

data class QrValidationResult(
    val isValid: Boolean,
    val data: QrPairingData? = null,
    val errors: List<String> = emptyList(),
    val checkSummary: Map<String, Boolean> = emptyMap()
)

/**
 * Server Info Response: GET /api/server/info
 */
@JsonClass(generateAdapter = true)
data class ServerInfoResponse(
    @Json(name = "serverId") val serverIdCamel: String? = null,
    @Json(name = "ServerId") val serverIdPascal: String? = null,
    @Json(name = "protocolVersion") val protocolVersionCamel: String? = null,
    @Json(name = "ProtocolVersion") val protocolVersionPascal: String? = null,
    @Json(name = "sessionActive") val sessionActiveCamel: Boolean? = null,
    @Json(name = "SessionActive") val sessionActivePascal: Boolean? = null,
    @Json(name = "port") val portCamel: Int? = null,
    @Json(name = "Port") val portPascal: Int? = null,
    @Json(name = "serverName") val serverNameCamel: String? = null,
    @Json(name = "ServerName") val serverNamePascal: String? = null,
    @Json(name = "restaurantName") val restaurantNameCamel: String? = null,
    @Json(name = "RestaurantName") val restaurantNamePascal: String? = null,
    @Json(name = "serverVersion") val serverVersionCamel: String? = null,
    @Json(name = "ServerVersion") val serverVersionPascal: String? = null,
    @Json(name = "callerAssistantEnabled") val callerAssistantEnabledCamel: Boolean? = null,
    @Json(name = "CallerAssistantEnabled") val callerAssistantEnabledPascal: Boolean? = null,
    @Json(name = "callerAssistantPairingMode") val callerAssistantPairingModeCamel: String? = null,
    @Json(name = "CallerAssistantPairingMode") val callerAssistantPairingModePascal: String? = null,
    @Json(name = "callerAssistantPort") val callerAssistantPortCamel: Int? = null,
    @Json(name = "CallerAssistantPort") val callerAssistantPortPascal: Int? = null,
    @Json(name = "callerAssistantUrl") val callerAssistantUrlCamel: String? = null,
    @Json(name = "CallerAssistantUrl") val callerAssistantUrlPascal: String? = null
) {
    val serverId: String? get() = serverIdCamel ?: serverIdPascal
    val protocolVersion: String? get() = protocolVersionCamel ?: protocolVersionPascal
    val sessionActive: Boolean? get() = sessionActiveCamel ?: sessionActivePascal
    val port: Int? get() = portCamel ?: portPascal
    val serverName: String? get() = serverNameCamel ?: serverNamePascal
    val restaurantName: String? get() = restaurantNameCamel ?: restaurantNamePascal
    val serverVersion: String? get() = serverVersionCamel ?: serverVersionPascal
    val callerAssistantEnabled: Boolean? get() = callerAssistantEnabledCamel ?: callerAssistantEnabledPascal
    val callerAssistantPairingMode: String? get() = callerAssistantPairingModeCamel ?: callerAssistantPairingModePascal
    val callerAssistantPort: Int? get() = callerAssistantPortCamel ?: callerAssistantPortPascal
    val callerAssistantUrl: String? get() = callerAssistantUrlCamel ?: callerAssistantUrlPascal

    val displayName: String
        get() = restaurantName ?: serverName ?: "Taloola POS"

    constructor(
        serverId: String? = null,
        protocolVersion: String? = null,
        sessionActive: Boolean? = null,
        port: Int? = null,
        serverName: String? = null,
        restaurantName: String? = null,
        serverVersion: String? = null,
        callerAssistantEnabled: Boolean? = null,
        callerAssistantPairingMode: String? = null,
        callerAssistantPort: Int? = null,
        callerAssistantUrl: String? = null
    ) : this(
        serverIdCamel = serverId,
        serverIdPascal = null,
        protocolVersionCamel = protocolVersion,
        protocolVersionPascal = null,
        sessionActiveCamel = sessionActive,
        sessionActivePascal = null,
        portCamel = port,
        portPascal = null,
        serverNameCamel = serverName,
        serverNamePascal = null,
        restaurantNameCamel = restaurantName,
        restaurantNamePascal = null,
        serverVersionCamel = serverVersion,
        serverVersionPascal = null,
        callerAssistantEnabledCamel = callerAssistantEnabled,
        callerAssistantEnabledPascal = null,
        callerAssistantPairingModeCamel = callerAssistantPairingMode,
        callerAssistantPairingModePascal = null,
        callerAssistantPortCamel = callerAssistantPort,
        callerAssistantPortPascal = null,
        callerAssistantUrlCamel = callerAssistantUrl,
        callerAssistantUrlPascal = null
    )
}

/**
 * Step 3: GET /api/caller-assistant/pair-info?pid=<QR.pid>
 */
@JsonClass(generateAdapter = true)
data class PairInfoResponse(
    @Json(name = "success") val successCamel: Boolean? = null,
    @Json(name = "Success") val successPascal: Boolean? = null,
    @Json(name = "serverId") val serverIdCamel: String? = null,
    @Json(name = "ServerId") val serverIdPascal: String? = null,
    @Json(name = "restaurantName") val restaurantNameCamel: String? = null,
    @Json(name = "RestaurantName") val restaurantNamePascal: String? = null,
    @Json(name = "serverName") val serverNameCamel: String? = null,
    @Json(name = "ServerName") val serverNamePascal: String? = null,
    @Json(name = "serverUrl") val serverUrlCamel: String? = null,
    @Json(name = "ServerUrl") val serverUrlPascal: String? = null,
    @Json(name = "pairingId") val pairingIdCamel: String? = null,
    @Json(name = "PairingId") val pairingIdPascal: String? = null,
    @Json(name = "protocolVersion") val protocolVersionCamel: String? = null,
    @Json(name = "ProtocolVersion") val protocolVersionPascal: String? = null,
    @Json(name = "expiresAtUtc") val expiresAtUtcCamel: String? = null,
    @Json(name = "ExpiresAtUtc") val expiresAtUtcPascal: String? = null,
    @Json(name = "errorMessage") val errorMessageCamel: String? = null,
    @Json(name = "ErrorMessage") val errorMessagePascal: String? = null
) {
    val success: Boolean get() = successCamel ?: successPascal ?: true
    val serverId: String? get() = serverIdCamel ?: serverIdPascal
    val restaurantName: String? get() = restaurantNameCamel ?: restaurantNamePascal
    val serverName: String? get() = serverNameCamel ?: serverNamePascal
    val serverUrl: String? get() = serverUrlCamel ?: serverUrlPascal
    val pairingId: String? get() = pairingIdCamel ?: pairingIdPascal
    val protocolVersion: String? get() = protocolVersionCamel ?: protocolVersionPascal
    val expiresAtUtc: String? get() = expiresAtUtcCamel ?: expiresAtUtcPascal
    val errorMessage: String? get() = errorMessageCamel ?: errorMessagePascal

    val displayName: String
        get() = restaurantName ?: serverName ?: "Taloola"

    constructor(
        success: Boolean = true,
        serverId: String? = null,
        restaurantName: String? = null,
        serverName: String? = null,
        serverUrl: String? = null,
        pairingId: String? = null,
        protocolVersion: String? = null,
        expiresAtUtc: String? = null,
        errorMessage: String? = null
    ) : this(
        successCamel = success,
        successPascal = null,
        serverIdCamel = serverId,
        serverIdPascal = null,
        restaurantNameCamel = restaurantName,
        restaurantNamePascal = null,
        serverNameCamel = serverName,
        serverNamePascal = null,
        serverUrlCamel = serverUrl,
        serverUrlPascal = null,
        pairingIdCamel = pairingId,
        pairingIdPascal = null,
        protocolVersionCamel = protocolVersion,
        protocolVersionPascal = null,
        expiresAtUtcCamel = expiresAtUtc,
        expiresAtUtcPascal = null,
        errorMessageCamel = errorMessage,
        errorMessagePascal = null
    )
}

object AlamerErrors {
    const val SERVER_UNREACHABLE = "SERVER_UNREACHABLE"
    const val SERVER_ID_MISMATCH = "SERVER_ID_MISMATCH"
    const val PROTOCOL_MISMATCH = "PROTOCOL_MISMATCH"
    const val PAIRING_EXPIRED = "PAIRING_EXPIRED"
    const val PAIRING_INVALID = "PAIRING_INVALID"
    const val PAIRING_ID_INVALID = "PAIRING_ID_INVALID"
    const val PAIRING_ALREADY_CONSUMED = "PAIRING_ALREADY_CONSUMED"
    const val LAN_ACCESS_DENIED = "LAN_ACCESS_DENIED"
    const val CREDENTIAL_INVALID = "CREDENTIAL_INVALID"
    const val DEVICE_REVOKED = "DEVICE_REVOKED"
    const val WRONG_NETWORK = "WRONG_NETWORK"

    fun formatServerUnreachable(url: String = ""): String =
        "تعذر الوصول إلى خادم Taloola على الرابط $url. يرجى التحقق من اتصال الشبكة والـ IP."

    fun formatServerIdMismatch(qrSid: String = "", currentSid: String = "", url: String = ""): String =
        "تم اكتشاف خادم مختلف على الرابط: $url\nQR ServerId: ${qrSid.take(12)}\nالحالي: ${currentSid.take(12)}"

    fun formatProtocolMismatch(): String = "إصدار البروتوكول غير متوافق مع نظام TaloolaPos."
    fun formatPairingExpired(): String = "رمز QR منتهي الصلاحية. يرجى إنشاء رمز جديد من شاشة الخادم."
    fun formatPairingInvalid(): String = "رمز الاقتران غير صالح أو تم استهلاكه مسبقاً."
    fun formatPairingIdInvalid(): String = "معرف جلسة الاقتران غير متطابق."
    fun formatPairingAlreadyConsumed(): String = "رمز الاقتران مستخدم بالفعل."
    fun formatLanAccessDenied(): String = "تم رفض الوصول من الشبكة المحلية."
    fun formatCredentialInvalid(): String = "بيانات الاعتماد غير مقبولة من الخادم. يرجى إعادة مسح رمز QR."
    fun formatDeviceRevoked(): String = "تم إلغاء ترخيص هذا الجهاز في نظام TaloolaPos."
    fun formatWrongNetwork(): String = "الجهاز غير متصل بنفس شبكة Wi-Fi المحلية الخاصة بالخادم."
}

data class ServerMismatchDetails(
    val restaurantName: String,
    val serverUrl: String,
    val currentServerId: String,
    val qrServerId: String,
    val pendingQrData: QrPairingData
)

data class UntrustedServerPrompt(
    val restaurantName: String,
    val serverUrl: String,
    val serverId: String
)

data class VerifiedQrSession(
    val qrData: QrPairingData,
    val restaurantName: String,
    val serverUrl: String,
    val serverId: String,
    val isDifferentFromSavedServer: Boolean
)

/**
 * Direct LAN Auto-Pair Request: POST /api/caller-assistant/auto-pair
 */
@JsonClass(generateAdapter = true)
data class AutoPairRequest(
    @Json(name = "version") val version: Int = 1,
    @Json(name = "deviceType") val deviceType: String = "CallerAssistant",
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "deviceName") val deviceName: String = "Alamer Caller",
    @Json(name = "installationBinding") val installationBinding: String,
    @Json(name = "protocolVersion") val protocolVersion: String = "1.0"
)

/**
 * POST /api/caller-assistant/pair Request
 */
@JsonClass(generateAdapter = true)
data class PairRequest(
    @Json(name = "version") val version: Int = 1,
    @Json(name = "deviceType") val deviceType: String = "CallerAssistant",
    @Json(name = "serverId") val serverId: String? = null,
    @Json(name = "protocolVersion") val protocolVersion: String = "1.0",
    @Json(name = "pairingId") val pairingId: String? = null,
    @Json(name = "token") val token: String? = null,
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "deviceName") val deviceName: String = "Alamer Caller",
    @Json(name = "installationBinding") val installationBinding: String,
    @Json(name = "autoPair") val autoPair: Boolean? = null
)

/**
 * POST /api/caller-assistant/auto-pair & /pair Response
 */
@JsonClass(generateAdapter = true)
data class PairResponse(
    @Json(name = "success") val successCamel: Boolean? = null,
    @Json(name = "Success") val successPascal: Boolean? = null,
    @Json(name = "serverId") val serverIdCamel: String? = null,
    @Json(name = "ServerId") val serverIdPascal: String? = null,
    @Json(name = "serverUrl") val serverUrlCamel: String? = null,
    @Json(name = "ServerUrl") val serverUrlPascal: String? = null,
    @Json(name = "protocolVersion") val protocolVersionCamel: String? = null,
    @Json(name = "ProtocolVersion") val protocolVersionPascal: String? = null,
    @Json(name = "deviceId") val deviceIdCamel: String? = null,
    @Json(name = "DeviceId") val deviceIdPascal: String? = null,
    @Json(name = "deviceName") val deviceNameCamel: String? = null,
    @Json(name = "DeviceName") val deviceNamePascal: String? = null,
    @Json(name = "installationBinding") val installationBindingCamel: String? = null,
    @Json(name = "InstallationBinding") val installationBindingPascal: String? = null,
    @Json(name = "callerCredential") val callerCredentialCamel: String? = null,
    @Json(name = "CallerCredential") val callerCredentialPascal: String? = null,
    @Json(name = "deviceStatus") val deviceStatusCamel: String? = null,
    @Json(name = "DeviceStatus") val deviceStatusPascal: String? = null,
    @Json(name = "capabilities") val capabilitiesCamel: List<String>? = null,
    @Json(name = "Capabilities") val capabilitiesPascal: List<String>? = null,
    @Json(name = "errorMessage") val errorMessageCamel: String? = null,
    @Json(name = "ErrorMessage") val errorMessagePascal: String? = null
) {
    val success: Boolean get() = successCamel ?: successPascal ?: false
    val serverId: String? get() = serverIdCamel ?: serverIdPascal
    val serverUrl: String? get() = serverUrlCamel ?: serverUrlPascal
    val protocolVersion: String? get() = protocolVersionCamel ?: protocolVersionPascal
    val deviceId: String? get() = deviceIdCamel ?: deviceIdPascal
    val deviceName: String? get() = deviceNameCamel ?: deviceNamePascal
    val installationBinding: String? get() = installationBindingCamel ?: installationBindingPascal
    val callerCredential: String? get() = callerCredentialCamel ?: callerCredentialPascal
    val deviceStatus: String? get() = deviceStatusCamel ?: deviceStatusPascal
    val capabilities: List<String>? get() = capabilitiesCamel ?: capabilitiesPascal
    val errorMessage: String? get() = errorMessageCamel ?: errorMessagePascal

    constructor(
        success: Boolean = false,
        serverId: String? = null,
        serverUrl: String? = null,
        protocolVersion: String? = null,
        deviceId: String? = null,
        deviceName: String? = null,
        installationBinding: String? = null,
        callerCredential: String? = null,
        deviceStatus: String? = null,
        capabilities: List<String>? = null,
        errorMessage: String? = null
    ) : this(
        successCamel = success,
        successPascal = null,
        serverIdCamel = serverId,
        serverIdPascal = null,
        serverUrlCamel = serverUrl,
        serverUrlPascal = null,
        protocolVersionCamel = protocolVersion,
        protocolVersionPascal = null,
        deviceIdCamel = deviceId,
        deviceIdPascal = null,
        deviceNameCamel = deviceName,
        deviceNamePascal = null,
        installationBindingCamel = installationBinding,
        installationBindingPascal = null,
        callerCredentialCamel = callerCredential,
        callerCredentialPascal = null,
        deviceStatusCamel = deviceStatus,
        deviceStatusPascal = null,
        capabilitiesCamel = capabilities,
        capabilitiesPascal = null,
        errorMessageCamel = errorMessage,
        errorMessagePascal = null
    )
}

/**
 * Pairing Result Model: capabilities is List<String>
 */
data class PairingResult(
    val success: Boolean = false,
    val errorCode: String? = null,
    val message: String? = null,
    val serverId: String = "",
    val serverUrl: String = "",
    val protocolVersion: String = "1.0",
    val deviceId: String = "",
    val deviceName: String = "Alamer Caller",
    val installationBinding: String = "",
    val callerCredential: String = "",
    val deviceStatus: String? = null,
    val capabilities: List<String> = emptyList()
)

/**
 * HTTP Fallback Heartbeat Request
 */
@JsonClass(generateAdapter = true)
data class HeartbeatRequest(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "installationBinding") val installationBinding: String,
    @Json(name = "callerCredential") val callerCredential: String,
    @Json(name = "protocolVersion") val protocolVersion: String = "1.0"
)

@JsonClass(generateAdapter = true)
data class HeartbeatResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: String? = null,
    @Json(name = "message") val message: String? = null
)

/**
 * Call Dispatch Lifecycle States as requested:
 * CREATED -> SIGNALR_SENT -> SIGNALR_ACCEPTED / SERVER_REJECTED -> DELIVERED / QUEUED
 * or fallback: HTTP_FALLBACK_SENT -> DELIVERED / QUEUED or FAILED
 */
enum class CallDispatchState {
    CREATED,
    SIGNALR_SENT,
    SIGNALR_ACCEPTED,
    HTTP_FALLBACK_SENT,
    SERVER_REJECTED,
    DELIVERED,
    QUEUED,
    FAILED
}

/**
 * Result of dispatching a caller call either via SignalR invocation or HTTP fallback.
 */
data class CallDispatchResult(
    val success: Boolean,
    val callId: String,
    val state: CallDispatchState,
    val transport: String, // "SignalR" or "HTTP"
    val deliveredToCashiers: Int = 0,
    val queuedForCashier: Boolean = false,
    val customerContext: CallerCustomerContext? = null,
    val errorMessage: String? = null
)

/**
 * HTTP Fallback Call Reporting Request
 */
@JsonClass(generateAdapter = true)
data class HttpCallRequest(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "installationBinding") val installationBinding: String,
    @Json(name = "callerCredential") val callerCredential: String,
    @Json(name = "callId") val callId: String,
    @Json(name = "phone") val phone: String,
    @Json(name = "normalizedPhone") val normalizedPhone: String,
    @Json(name = "direction") val direction: String = "incoming",
    @Json(name = "startedAtUtc") val startedAtUtc: String
)

/**
 * Response from POST /api/caller-assistant/call (matches Taloola CallerAssistantCallResult)
 */
@JsonClass(generateAdapter = true)
data class HttpCallResponse(
    @Json(name = "success") val successCamel: Boolean? = null,
    @Json(name = "Success") val successPascal: Boolean? = null,
    @Json(name = "errorCode") val errorCodeCamel: String? = null,
    @Json(name = "ErrorCode") val errorCodePascal: String? = null,
    @Json(name = "message") val messageCamel: String? = null,
    @Json(name = "Message") val messagePascal: String? = null,
    @Json(name = "deliveredToCashiers") val deliveredToCashiersCamel: Int? = null,
    @Json(name = "DeliveredToCashiers") val deliveredToCashiersPascal: Int? = null,
    @Json(name = "queuedForCashier") val queuedForCashierCamel: Boolean? = null,
    @Json(name = "QueuedForCashier") val queuedForCashierPascal: Boolean? = null,
    @Json(name = "context") val contextCamel: CallerCustomerContext? = null,
    @Json(name = "Context") val contextPascal: CallerCustomerContext? = null,
    @Json(name = "customerContext") val customerContextCamel: CallerCustomerContext? = null,
    @Json(name = "CustomerContext") val customerContextPascal: CallerCustomerContext? = null
) {
    val success: Boolean get() = successCamel ?: successPascal ?: false
    val errorCode: String? get() = errorCodeCamel ?: errorCodePascal
    val message: String? get() = messageCamel ?: messagePascal
    val deliveredToCashiers: Int get() = deliveredToCashiersCamel ?: deliveredToCashiersPascal ?: 0
    val queuedForCashier: Boolean get() = queuedForCashierCamel ?: queuedForCashierPascal ?: false
    val customerContext: CallerCustomerContext? get() = contextCamel ?: contextPascal ?: customerContextCamel ?: customerContextPascal

    constructor(
        success: Boolean = false,
        errorCode: String? = null,
        message: String? = null,
        deliveredToCashiers: Int = 0,
        queuedForCashier: Boolean = false,
        customerContext: CallerCustomerContext? = null
    ) : this(
        successCamel = success,
        successPascal = null,
        errorCodeCamel = errorCode,
        errorCodePascal = null,
        messageCamel = message,
        messagePascal = null,
        deliveredToCashiersCamel = deliveredToCashiers,
        deliveredToCashiersPascal = null,
        queuedForCashierCamel = queuedForCashier,
        queuedForCashierPascal = null,
        contextCamel = customerContext,
        contextPascal = null,
        customerContextCamel = customerContext,
        customerContextPascal = null
    )
}

/**
 * POST /api/caller-assistant/reconnect Request
 */
@JsonClass(generateAdapter = true)
data class ReconnectRequest(
    @Json(name = "DeviceId") val deviceId: String,
    @Json(name = "InstallationBinding") val installationBinding: String,
    @Json(name = "ServerId") val serverId: String,
    @Json(name = "CallerCredential") val callerCredential: String,
    @Json(name = "ProtocolVersion") val protocolVersion: String = "1.0"
)

@JsonClass(generateAdapter = true)
data class ReconnectResponse(
    @Json(name = "Success") val success: Boolean = false,
    @Json(name = "ServerId") val serverId: String? = null,
    @Json(name = "ServerUrl") val serverUrl: String? = null,
    @Json(name = "DeviceStatus") val deviceStatus: String? = null,
    @Json(name = "ErrorMessage") val errorMessage: String? = null
)

/**
 * SignalR AuthenticateCallerAssistant Request
 */
@JsonClass(generateAdapter = true)
data class SignalRAuthRequest(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "deviceName") val deviceName: String,
    @Json(name = "installationBinding") val installationBinding: String,
    @Json(name = "callerCredential") val callerCredential: String,
    @Json(name = "protocolVersion") val protocolVersion: String = "1.0",
    @Json(name = "platform") val platform: String = "Android"
)

/**
 * SignalR RecordCallerCall Request
 */
@JsonClass(generateAdapter = true)
data class CallRecordRequest(
    @Json(name = "CallId") val callId: String,
    @Json(name = "Phone") val phone: String,
    @Json(name = "NormalizedPhone") val normalizedPhone: String,
    @Json(name = "Direction") val direction: String = "incoming",
    @Json(name = "StartedAtUtc") val startedAtUtc: String
)

/**
 * SignalR CallerCustomerContext - Fully aligned with Taloola Pos models
 */
@JsonClass(generateAdapter = true)
data class CallerCustomerContext(
    @Json(name = "CallId") val callId: String? = null,
    @Json(name = "CustomerId") val customerId: String? = null,
    @Json(name = "Phone") val phone: String? = null,
    @Json(name = "NormalizedPhone") val normalizedPhone: String? = null,
    @Json(name = "Name") val name: String? = null,
    @Json(name = "Status") val status: String? = null,
    @Json(name = "CurrentSessionId") val currentSessionId: String? = null,
    @Json(name = "LastCall") val lastCall: Any? = null,
    @Json(name = "MinutesSinceLastCall") val minutesSinceLastCall: Int? = null,
    @Json(name = "LastOrder") val lastOrder: Any? = null,
    @Json(name = "RecentOrders") val recentOrders: List<Any>? = null,
    @Json(name = "OrderCount") val orderCount: Int? = null,
    @Json(name = "Area") val area: String? = null,
    @Json(name = "Address") val address: String? = null,
    @Json(name = "Latitude") val latitude: Double? = null,
    @Json(name = "Longitude") val longitude: Double? = null,
    @Json(name = "HasLocation") val hasLocation: Boolean? = null,
    @Json(name = "Notes") val notes: String? = null,
    @Json(name = "RetrievedAtUtc") val retrievedAtUtc: String? = null,
    @Json(name = "LastCallAtUtc") val lastCallAtUtc: String? = null,
    @Json(name = "TimeSinceLastCallText") val timeSinceLastCallText: String? = null,
    @Json(name = "HasPendingOrder") val hasPendingOrder: Boolean? = null,
    @Json(name = "LastOrderTypeText") val lastOrderTypeText: String? = null,
    @Json(name = "LastOrderAppName") val lastOrderAppName: String? = null,
    @Json(name = "LastOrderStatus") val lastOrderStatus: String? = null
)

/**
 * Persistent Trust stored securely
 */
data class TrustCredentials(
    val serverId: String,
    val serverName: String,
    val serverUrl: String,
    val host: String,
    val port: Int,
    val tlsRequired: Boolean,
    val protocolVersion: String,
    val deviceId: String,
    val deviceName: String,
    val installationBinding: String,
    val callerCredential: String,
    val hubPath: String = "/posHub",
    val pairedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * Call entry in local call history / buffer with full lifecycle status
 */
data class CallHistoryItem(
    val callId: String,
    val phone: String,
    val normalizedPhone: String,
    val direction: String,
    val startedAtUtc: String,
    val timestampMs: Long,
    val isDispatchedToTaloola: Boolean,
    val dispatchState: CallDispatchState = if (isDispatchedToTaloola) CallDispatchState.DELIVERED else CallDispatchState.CREATED,
    val transport: String? = null,
    val deliveredToCashiers: Int = 0,
    val queuedForCashier: Boolean = false,
    val customerContext: CallerCustomerContext? = null,
    val error: String? = null
)

/**
 * Diagnostics information
 */
data class DiagnosticsReport(
    val wifiConnected: Boolean = false,
    val wifiSsid: String = "غير متصل",
    val localIp: String = "127.0.0.1",
    val savedHost: String = "-",
    val savedPort: Int = 0,
    val serverReachability: Boolean = false,
    val serverInfoStatus: String = "-",
    val serverIdMatch: Boolean = false,
    val credentialStatus: String = "غير مخزنة",
    val signalRStatus: String = "غير متصل",
    val lastError: String? = null,
    val retryCount: Int = 0,
    val lastSyncTime: String = "-",
    val hubUrl: String = "-",
    val serverUrl: String = "-",
    val lastConnectAttempt: String = "-",
    val lastSuccessfulConnect: String = "-",
    val lastDisconnectReason: String = "-",
    val lastAuthResult: String = "-",
    val activeClientId: String = "-",
    val heartbeatCount: Int = 0,
    val isHeartbeatRunning: Boolean = false
)
