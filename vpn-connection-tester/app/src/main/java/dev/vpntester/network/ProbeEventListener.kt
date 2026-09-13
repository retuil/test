package dev.vpntester.network

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Response

internal class ProbeTrace {
    private val startedAt = System.nanoTime()
    var dnsStart: Long? = null; var dnsEnd: Long? = null
    var connectStart: Long? = null; var connectEnd: Long? = null
    var tlsStart: Long? = null; var tlsEnd: Long? = null
    var responseStart: Long? = null; var responseEnd: Long? = null
    fun durationMs(start: Long?, end: Long?): Long? = if (start == null || end == null || end < start) null else (end - start) / 1_000_000
    fun totalMs(): Long = (System.nanoTime() - startedAt) / 1_000_000
}

internal class ProbeEventListener(private val trace: ProbeTrace) : EventListener() {
    override fun dnsStart(call: Call, domainName: String) { trace.dnsStart = System.nanoTime() }
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) { trace.dnsEnd = System.nanoTime() }
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) { if (trace.connectStart == null) trace.connectStart = System.nanoTime() }
    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) { trace.connectEnd = System.nanoTime() }
    override fun secureConnectStart(call: Call) { trace.tlsStart = System.nanoTime() }
    override fun secureConnectEnd(call: Call, handshake: Handshake?) { trace.tlsEnd = System.nanoTime() }
    override fun responseHeadersStart(call: Call) { trace.responseStart = System.nanoTime() }
    override fun responseHeadersEnd(call: Call, response: Response) { trace.responseEnd = System.nanoTime() }
}
