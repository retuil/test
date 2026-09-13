package dev.vpntester.network

import dev.vpntester.model.ProbeStatus
import dev.vpntester.model.ServiceId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HttpProbeTest {
    private lateinit var server: MockWebServer
    private lateinit var probe: HttpProbe

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        probe = HttpProbe(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun successfulResponseIsAvailable() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        val config = ProbeConfig(ServiceId.YOUTUBE, server.url("/").toString()) {
            if (it == 204) ProbeStatus.AVAILABLE else ProbeStatus.UNKNOWN
        }
        val result = probe.run(config)
        assertEquals(ProbeStatus.AVAILABLE, result.status)
        assertEquals(204, result.httpCode)
    }

    @Test
    fun unexpectedHttpResponseIsUnknown() = runTest {
        server.enqueue(MockResponse.Builder().code(403).build())
        val config = ProbeConfig(ServiceId.CHATGPT, server.url("/").toString()) {
            if (it in 200..399) ProbeStatus.AVAILABLE else ProbeStatus.UNKNOWN
        }
        val result = probe.run(config)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
        assertEquals(403, result.httpCode)
    }

    @Test
    fun readTimeoutIsUnavailable() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .body("slow")
                .headersDelay(6, TimeUnit.SECONDS)
                .build()
        )
        val config = ProbeConfig(ServiceId.TELEGRAM, server.url("/").toString()) {
            ProbeStatus.AVAILABLE
        }
        val result = probe.run(config)
        assertEquals(ProbeStatus.UNAVAILABLE, result.status)
    }
}
