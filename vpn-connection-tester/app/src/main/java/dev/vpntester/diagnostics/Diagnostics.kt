package dev.vpntester.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class DiagnosticEvent(val timestamp: Instant, val level: LogLevel, val component: String, val message: String)

class DiagnosticRecorder(context: Context) {
    private val logsDir = File(context.filesDir, "logs").apply { mkdirs() }
    private val lock = Any()
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    private val fileFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault())
    private val _events = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events: StateFlow<List<DiagnosticEvent>> = _events.asStateFlow()

    private var writer: PrintWriter? = null
    private var currentFile: File? = null

    fun startSession(): String = synchronized(lock) {
        closeWriter()
        val now = Instant.now()
        val id = UUID.randomUUID().toString().take(8)
        val file = File(logsDir, "${fileFormatter.format(now)}_$id.log")
        writer = file.printWriter()
        currentFile = file
        _events.value = emptyList()
        rotateLogs()
        logLocked(LogLevel.INFO, "TEST", "Session started id=$id")
        id
    }

    fun log(level: LogLevel, component: String, message: String) = synchronized(lock) { logLocked(level, component, message) }
    fun endSession(message: String) = synchronized(lock) { logLocked(LogLevel.INFO, "TEST", message); writer?.flush() }
    fun currentLogFile(): File? = synchronized(lock) { writer?.flush(); currentFile }
    fun currentLogText(): String = synchronized(lock) { _events.value.joinToString("\n") { format(it) } }
    fun close() = synchronized(lock) { closeWriter() }

    private fun logLocked(level: LogLevel, component: String, message: String) {
        val event = DiagnosticEvent(Instant.now(), level, component, message)
        _events.value = _events.value + event
        writer?.println(format(event))
        writer?.flush()
    }

    private fun format(event: DiagnosticEvent) = "${formatter.format(event.timestamp)} ${event.level.name.padEnd(5)} ${event.component.padEnd(10)} ${event.message}"
    private fun rotateLogs() { logsDir.listFiles()?.filter { it.isFile && it.extension == "log" }?.sortedByDescending { it.lastModified() }?.drop(10)?.forEach { it.delete() } }
    private fun closeWriter() { writer?.flush(); writer?.close(); writer = null }
}
