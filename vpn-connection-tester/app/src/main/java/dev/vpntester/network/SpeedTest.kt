package dev.vpntester.network

import dev.vpntester.diagnostics.DiagnosticRecorder
import dev.vpntester.diagnostics.LogLevel
import dev.vpntester.model.SpeedResult
import dev.vpntester.model.SpeedStatus
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.coroutines.executeAsync
import okio.Buffer
import okio.BufferedSink

class CloudflareSpeedTest(baseClient: OkHttpClient, private val recorder: DiagnosticRecorder) {
    private val client = baseClient.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).writeTimeout(12, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()

    suspend fun download(): SpeedResult = measureDirection("DOWNLOAD", listOf(100_000L, 1_000_000L, 5_000_000L, 10_000_000L, 25_000_000L), 41_100_000L, ::downloadSample)
    suspend fun upload(): SpeedResult = measureDirection("UPLOAD", listOf(100_000L, 1_000_000L, 5_000_000L, 10_000_000L), 16_100_000L, ::uploadSample)

    private suspend fun measureDirection(component: String, sizes: List<Long>, budget: Long, measure: suspend (Long) -> Sample): SpeedResult {
        recorder.log(LogLevel.INFO, component, "Starting Cloudflare speed test")
        val samples = mutableListOf<Sample>(); var transferred = 0L
        return try {
            for (size in sizes) {
                if (transferred + size > budget) break
                val sample = measure(size); samples += sample; transferred += sample.bytes
                recorder.log(LogLevel.DEBUG, component, "sample bytes=${sample.bytes} duration=${sample.durationMs}ms speed=${format(sample.mbps)}Mbps")
                if (sample.durationMs >= 1_000 && size >= 1_000_000L) break
            }
            val usable = samples.filter { it.durationMs >= 10 }
            if (usable.isEmpty()) return SpeedResult(status = SpeedStatus.FAILED, bytesTransferred = transferred, message = "No usable measurement")
            val sorted = usable.map { it.mbps }.sorted(); val percentileIndex = max(0, ((sorted.size - 1) * 0.9).toInt()); val mbps = sorted[percentileIndex]
            val duration = usable.sumOf { it.durationMs }
            recorder.log(LogLevel.INFO, component, "Completed ${format(mbps)} Mbps bytes=$transferred")
            SpeedResult(SpeedStatus.SUCCESS, mbps, transferred, duration)
        } catch (cancelled: CancellationException) {
            recorder.log(LogLevel.WARN, component, "Cancelled"); throw cancelled
        } catch (error: IOException) {
            recorder.log(LogLevel.ERROR, component, "${error.javaClass.simpleName}: ${error.message}")
            SpeedResult(status = SpeedStatus.FAILED, bytesTransferred = transferred, message = error.message ?: error.javaClass.simpleName)
        }
    }

    private suspend fun downloadSample(bytes: Long): Sample {
        val request = Request.Builder().url("https://speed.cloudflare.com/__down?bytes=$bytes").header("Cache-Control", "no-cache").header("Accept-Encoding", "identity").build()
        val started = System.nanoTime(); var received = 0L
        client.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) throw IOException("Cloudflare download HTTP ${response.code}")
            val source = response.body?.source() ?: throw IOException("Empty download response"); val buffer = Buffer()
            while (true) { val read = source.read(buffer, 64 * 1024L); if (read == -1L) break; received += read; buffer.clear() }
        }
        return Sample(received, ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1))
    }

    private suspend fun uploadSample(bytes: Long): Sample {
        val request = Request.Builder().url("https://speed.cloudflare.com/__up").post(RepeatingRequestBody(bytes)).header("Cache-Control", "no-cache").build()
        val started = System.nanoTime(); client.newCall(request).executeAsync().use { response -> if (!response.isSuccessful) throw IOException("Cloudflare upload HTTP ${response.code}") }
        return Sample(bytes, ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1))
    }

    private fun format(value: Double) = String.format(Locale.US, "%.2f", value)
    private data class Sample(val bytes: Long, val durationMs: Long) { val mbps: Double = bytes * 8.0 / (durationMs / 1000.0) / 1_000_000.0 }
    private class RepeatingRequestBody(private val byteCount: Long) : RequestBody() {
        private val mediaType = "application/octet-stream".toMediaType(); private val chunk = ByteArray(64 * 1024)
        override fun contentType() = mediaType
        override fun contentLength() = byteCount
        override fun writeTo(sink: BufferedSink) { var remaining = byteCount; while (remaining > 0) { val count = minOf(chunk.size.toLong(), remaining).toInt(); sink.write(chunk, 0, count); remaining -= count } }
    }
}
