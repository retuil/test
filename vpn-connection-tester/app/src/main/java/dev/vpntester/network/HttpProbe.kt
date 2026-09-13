package dev.vpntester.network

import dev.vpntester.diagnostics.DiagnosticRecorder
import dev.vpntester.diagnostics.LogLevel
import dev.vpntester.model.FailureStage
import dev.vpntester.model.ProbeStatus
import dev.vpntester.model.ProbeTimings
import dev.vpntester.model.ServiceId
import dev.vpntester.model.ServiceResult
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

data class ProbeConfig(val service: ServiceId, val url: String, val classify: (Int) -> ProbeStatus)

class HttpProbe(private val baseClient: OkHttpClient, private val recorder: DiagnosticRecorder? = null) {
    suspend fun run(config: ProbeConfig): ServiceResult {
        val trace = ProbeTrace()
        val client = baseClient.newBuilder().eventListener(ProbeEventListener(trace)).connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(7, TimeUnit.SECONDS).build()
        recorder?.log(LogLevel.INFO, config.service.name, "Starting ${config.url}")
        val request = Request.Builder().url(config.url).header("User-Agent", "VpnConnectionTester/0.1 Android").header("Cache-Control", "no-cache").build()
        return try {
            client.newCall(request).executeAsync().use { response ->
                response.body.bytes()
                val timings = trace.toTimings()
                val result = ServiceResult(config.service, config.classify(response.code), response.code, timings = timings)
                recorder?.log(LogLevel.INFO, config.service.name, "HTTP ${response.code} ${result.status} dns=${fmt(timings.dnsMs)} connect=${fmt(timings.connectMs)} tls=${fmt(timings.tlsMs)} total=${fmt(timings.totalMs)}")
                result
            }
        } catch (cancelled: CancellationException) {
            recorder?.log(LogLevel.WARN, config.service.name, "Cancelled"); throw cancelled
        } catch (error: IOException) {
            val stage = classifyFailure(error, trace)
            val timings = trace.toTimings()
            recorder?.log(LogLevel.ERROR, config.service.name, "$stage ${error.javaClass.simpleName}: ${error.message} dns=${fmt(timings.dnsMs)} connect=${fmt(timings.connectMs)} tls=${fmt(timings.tlsMs)} total=${fmt(timings.totalMs)}")
            ServiceResult(config.service, ProbeStatus.UNAVAILABLE, failureStage = stage, timings = timings, message = error.message ?: error.javaClass.simpleName)
        }
    }

    private fun classifyFailure(error: IOException, trace: ProbeTrace): FailureStage = when {
        error is UnknownHostException -> FailureStage.DNS
        error is SSLException -> FailureStage.TLS
        error is ConnectException -> FailureStage.CONNECT
        error is SocketTimeoutException && trace.connectStart != null && trace.connectEnd == null -> FailureStage.CONNECT
        error is SocketTimeoutException && trace.tlsStart != null && trace.tlsEnd == null -> FailureStage.TLS
        error is SocketTimeoutException -> FailureStage.HTTP
        trace.dnsStart != null && trace.dnsEnd == null -> FailureStage.DNS
        trace.connectStart != null && trace.connectEnd == null -> FailureStage.CONNECT
        trace.tlsStart != null && trace.tlsEnd == null -> FailureStage.TLS
        else -> FailureStage.UNKNOWN
    }

    private fun ProbeTrace.toTimings() = ProbeTimings(durationMs(dnsStart, dnsEnd), durationMs(connectStart, connectEnd), durationMs(tlsStart, tlsEnd), durationMs(responseStart, responseEnd), totalMs())
    private fun fmt(value: Long?) = value?.let { "${it}ms" } ?: "n/a"
}

object ProbeConfigs {
    val telegram = ProbeConfig(ServiceId.TELEGRAM, "https://api.telegram.org/") { code -> when (code) { 451 -> ProbeStatus.UNAVAILABLE; in 200..499 -> ProbeStatus.AVAILABLE; else -> ProbeStatus.UNKNOWN } }
    val youtube = ProbeConfig(ServiceId.YOUTUBE, "https://www.youtube.com/generate_204") { code -> if (code == 204) ProbeStatus.AVAILABLE else ProbeStatus.UNKNOWN }
    val chatGpt = ProbeConfig(ServiceId.CHATGPT, "https://chatgpt.com/api/auth/session") { code -> when (code) { in 200..399, 401 -> ProbeStatus.AVAILABLE; 451 -> ProbeStatus.UNAVAILABLE; else -> ProbeStatus.UNKNOWN } }
    val all = listOf(telegram, youtube, chatGpt)
}
