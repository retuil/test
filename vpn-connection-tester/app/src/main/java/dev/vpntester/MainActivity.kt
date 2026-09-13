package dev.vpntester

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vpntester.ui.DiagnosticsScreen
import dev.vpntester.ui.MainScreen
import dev.vpntester.ui.VpnTesterTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VpnTesterTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val events by viewModel.diagnosticEvents.collectAsStateWithLifecycle()
                var diagnostics by rememberSaveable { mutableStateOf(false) }

                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/plain")
                ) { uri ->
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                            it.write(viewModel.currentLogText())
                        }
                    }
                }

                if (diagnostics) {
                    DiagnosticsScreen(
                        events = events,
                        onBack = { diagnostics = false },
                        onCopy = { copyLog(viewModel.currentLogText()) },
                        onExport = { exportLauncher.launch("vpn-test-${state.sessionId ?: "log"}.log") },
                        onShare = { shareCurrentLog() }
                    )
                } else {
                    MainScreen(
                        state = state,
                        onRunOrCancel = viewModel::toggleTest,
                        onDiagnostics = { diagnostics = true }
                    )
                }
            }
        }
    }

    private fun copyLog(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("VPN test log", text))
    }

    private fun shareCurrentLog() {
        val file = viewModel.currentLogFile() ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("VPN test log", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(sendIntent, "Поделиться логом"))
    }
}
