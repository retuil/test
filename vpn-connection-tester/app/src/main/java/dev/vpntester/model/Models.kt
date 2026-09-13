package dev.vpntester.model

enum class NetworkTransport { NONE, WIFI, CELLULAR, ETHERNET, OTHER }

data class NetworkSnapshot(
    val transport: NetworkTransport = NetworkTransport.NONE,
    val vpn: Boolean = false,
    val hasInternetCapability: Boolean = false,
    val validated: Boolean = false,
    val metered: Boolean = false
)

enum class ServiceId(val title: String) { TELEGRAM("Telegram"), YOUTUBE("YouTube"), CHATGPT("ChatGPT") }
enum class ProbeStatus { NOT_TESTED, RUNNING, AVAILABLE, UNAVAILABLE, UNKNOWN }
enum class FailureStage { DNS, CONNECT, TLS, HTTP, CANCELLED, UNKNOWN }

data class ProbeTimings(
    val dnsMs: Long? = null,
    val connectMs: Long? = null,
    val tlsMs: Long? = null,
    val responseHeadersMs: Long? = null,
    val totalMs: Long? = null
)

data class ServiceResult(
    val service: ServiceId,
    val status: ProbeStatus,
    val httpCode: Int? = null,
    val failureStage: FailureStage? = null,
    val timings: ProbeTimings = ProbeTimings(),
    val message: String? = null
)

enum class SpeedStatus { NOT_TESTED, RUNNING, SUCCESS, FAILED, CANCELLED }

data class SpeedResult(
    val status: SpeedStatus = SpeedStatus.NOT_TESTED,
    val mbps: Double? = null,
    val bytesTransferred: Long = 0,
    val durationMs: Long = 0,
    val message: String? = null
)

data class TestUiState(
    val network: NetworkSnapshot = NetworkSnapshot(),
    val services: Map<ServiceId, ServiceResult> = ServiceId.entries.associateWith { ServiceResult(it, ProbeStatus.NOT_TESTED) },
    val download: SpeedResult = SpeedResult(),
    val upload: SpeedResult = SpeedResult(),
    val isRunning: Boolean = false,
    val sessionId: String? = null
)
