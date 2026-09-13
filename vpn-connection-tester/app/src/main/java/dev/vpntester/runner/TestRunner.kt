package dev.vpntester.runner

import dev.vpntester.diagnostics.DiagnosticRecorder
import dev.vpntester.diagnostics.LogLevel
import dev.vpntester.model.FailureStage
import dev.vpntester.model.NetworkSnapshot
import dev.vpntester.model.NetworkTransport
import dev.vpntester.model.ProbeStatus
import dev.vpntester.model.ServiceId
import dev.vpntester.model.ServiceResult
import dev.vpntester.model.SpeedResult
import dev.vpntester.model.SpeedStatus
import dev.vpntester.network.CloudflareSpeedTest
import dev.vpntester.network.HttpProbe
import dev.vpntester.network.NetworkStateProvider
import dev.vpntester.network.ProbeConfigs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class TestRunner(
    private val networkStateProvider: NetworkStateProvider,
    private val httpProbe: HttpProbe,
    private val speedTest: CloudflareSpeedTest,
    private val recorder: DiagnosticRecorder
) {
    interface Listener {
        fun onNetwork(snapshot: NetworkSnapshot)
        fun onServiceStarted(service: ServiceId)
        fun onServiceFinished(result: ServiceResult)
        fun onDownloadStarted()
        fun onDownloadFinished(result: SpeedResult)
        fun onUploadStarted()
        fun onUploadFinished(result: SpeedResult)
    }

    suspend fun run(listener: Listener) {
        val network = networkStateProvider.readSnapshot()
        listener.onNetwork(network)
        recorder.log(LogLevel.INFO, "NETWORK", "transport=${network.transport} vpn=${network.vpn} internet=${network.hasInternetCapability} validated=${network.validated} metered=${network.metered}")

        if (network.transport == NetworkTransport.NONE || !network.hasInternetCapability) {
            ProbeConfigs.all.forEach { config ->
                listener.onServiceFinished(ServiceResult(config.service, ProbeStatus.UNAVAILABLE, failureStage = FailureStage.CONNECT, message = "No active Internet-capable network"))
            }
            val failed = SpeedResult(status = SpeedStatus.FAILED, message = "No active Internet-capable network")
            listener.onDownloadFinished(failed); listener.onUploadFinished(failed)
            recorder.log(LogLevel.ERROR, "NETWORK", "No active Internet-capable network")
            return
        }

        coroutineScope {
            ProbeConfigs.all.map { config -> async { listener.onServiceStarted(config.service); listener.onServiceFinished(httpProbe.run(config)) } }.awaitAll()
        }
        listener.onDownloadStarted(); listener.onDownloadFinished(speedTest.download())
        listener.onUploadStarted(); listener.onUploadFinished(speedTest.upload())
    }
}
