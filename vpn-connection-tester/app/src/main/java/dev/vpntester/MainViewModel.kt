package dev.vpntester

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vpntester.diagnostics.DiagnosticRecorder
import dev.vpntester.diagnostics.LogLevel
import dev.vpntester.model.NetworkSnapshot
import dev.vpntester.model.ProbeStatus
import dev.vpntester.model.ServiceId
import dev.vpntester.model.ServiceResult
import dev.vpntester.model.SpeedResult
import dev.vpntester.model.SpeedStatus
import dev.vpntester.model.TestUiState
import dev.vpntester.network.CloudflareSpeedTest
import dev.vpntester.network.HttpProbe
import dev.vpntester.network.NetworkStateProvider
import dev.vpntester.runner.TestRunner
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val recorder = DiagnosticRecorder(application)
    private val networkStateProvider = NetworkStateProvider(application)
    private val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).retryOnConnectionFailure(true).build()
    private val runner = TestRunner(networkStateProvider, HttpProbe(client, recorder), CloudflareSpeedTest(client, recorder), recorder)
    private val _uiState = MutableStateFlow(TestUiState())
    val uiState: StateFlow<TestUiState> = _uiState.asStateFlow()
    val diagnosticEvents = recorder.events
    private var testJob: Job? = null

    init {
        networkStateProvider.start()
        viewModelScope.launch {
            networkStateProvider.snapshot.collectLatest { snapshot ->
                val previous = _uiState.value.network
                val running = _uiState.value.isRunning
                _uiState.update { it.copy(network = snapshot) }
                if (running && routeChanged(previous, snapshot)) {
                    recorder.log(LogLevel.WARN, "NETWORK", "Route changed during test: ${previous.transport}/${previous.vpn} -> ${snapshot.transport}/${snapshot.vpn}. Cancelling session.")
                    cancelTest()
                }
            }
        }
    }

    fun toggleTest() { if (_uiState.value.isRunning) cancelTest() else runTest() }

    fun runTest() {
        if (testJob?.isActive == true) return
        val sessionId = recorder.startSession()
        _uiState.value = TestUiState(network = networkStateProvider.readSnapshot(), isRunning = true, sessionId = sessionId)
        testJob = viewModelScope.launch {
            try {
                runner.run(object : TestRunner.Listener {
                    override fun onNetwork(snapshot: NetworkSnapshot) { _uiState.update { it.copy(network = snapshot) } }
                    override fun onServiceStarted(service: ServiceId) { _uiState.update { state -> state.copy(services = state.services + (service to ServiceResult(service, ProbeStatus.RUNNING))) } }
                    override fun onServiceFinished(result: ServiceResult) { _uiState.update { state -> state.copy(services = state.services + (result.service to result)) } }
                    override fun onDownloadStarted() { _uiState.update { it.copy(download = SpeedResult(status = SpeedStatus.RUNNING)) } }
                    override fun onDownloadFinished(result: SpeedResult) { _uiState.update { it.copy(download = result) } }
                    override fun onUploadStarted() { _uiState.update { it.copy(upload = SpeedResult(status = SpeedStatus.RUNNING)) } }
                    override fun onUploadFinished(result: SpeedResult) { _uiState.update { it.copy(upload = result) } }
                })
                recorder.endSession("Session completed")
            } catch (_: CancellationException) {
                recorder.endSession("Session cancelled")
                _uiState.update { state ->
                    state.copy(
                        services = state.services.mapValues { (_, result) -> if (result.status == ProbeStatus.RUNNING) result.copy(status = ProbeStatus.NOT_TESTED) else result },
                        download = if (state.download.status == SpeedStatus.RUNNING) SpeedResult(status = SpeedStatus.CANCELLED) else state.download,
                        upload = if (state.upload.status == SpeedStatus.RUNNING) SpeedResult(status = SpeedStatus.CANCELLED) else state.upload
                    )
                }
            } catch (error: Throwable) {
                recorder.log(LogLevel.ERROR, "TEST", "Unhandled ${error.javaClass.simpleName}: ${error.message}")
                recorder.endSession("Session failed")
            } finally { _uiState.update { it.copy(isRunning = false) } }
        }
    }

    fun cancelTest() { testJob?.cancel() }
    fun currentLogText(): String = recorder.currentLogText()
    fun currentLogFile(): File? = recorder.currentLogFile()
    private fun routeChanged(previous: NetworkSnapshot, current: NetworkSnapshot) = previous.transport != current.transport || previous.vpn != current.vpn

    override fun onCleared() {
        testJob?.cancel(); networkStateProvider.stop(); recorder.close(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); super.onCleared()
    }
}
