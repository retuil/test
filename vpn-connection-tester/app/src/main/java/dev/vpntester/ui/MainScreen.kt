package dev.vpntester.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vpntester.model.NetworkSnapshot
import dev.vpntester.model.NetworkTransport
import dev.vpntester.model.ProbeStatus
import dev.vpntester.model.ServiceResult
import dev.vpntester.model.SpeedResult
import dev.vpntester.model.SpeedStatus
import dev.vpntester.model.TestUiState
import java.util.Locale

@Composable
fun MainScreen(
    state: TestUiState,
    onRunOrCancel: () -> Unit,
    onDiagnostics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("VPN Connection Tester", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Проверка текущего маршрута Android", style = MaterialTheme.typography.bodyMedium)

        SectionCard("Сеть") {
            NetworkRow("Подключение", state.network.transportLabel())
            NetworkRow("VPN", yesNo(state.network.vpn))
            NetworkRow(
                "Интернет",
                if (state.network.validated) "Доступен"
                else if (state.network.hasInternetCapability) "Не подтверждён"
                else "Нет"
            )
            NetworkRow("Тарифицируемая сеть", yesNo(state.network.metered))
        }

        SectionCard("Сервисы") {
            state.services.values.forEachIndexed { index, result ->
                ServiceRow(result)
                if (index < state.services.size - 1) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        SectionCard("Скорость") {
            SpeedRow("Download", state.download)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SpeedRow("Upload", state.upload)
        }

        Button(modifier = Modifier.fillMaxWidth(), onClick = onRunOrCancel) {
            Text(if (state.isRunning) "ОТМЕНИТЬ" else "ЗАПУСТИТЬ ТЕСТ")
        }

        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onDiagnostics) {
            Text("Диагностика")
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Speed test использует Cloudflare и передаёт максимум около 41 МБ на скачивание и 16 МБ на отправку за запуск.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun NetworkRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ServiceRow(result: ServiceResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(result.service.title, fontWeight = FontWeight.Medium)
            result.failureStage?.let {
                Text("Ошибка: $it", style = MaterialTheme.typography.bodySmall)
            }
            if (result.status == ProbeStatus.UNKNOWN && result.httpCode != null) {
                Text("HTTP ${result.httpCode}: ответ получен, результат неоднозначен", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(serviceValue(result), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SpeedRow(label: String, result: SpeedResult) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(speedValue(result), fontWeight = FontWeight.SemiBold)
    }
}

private fun serviceValue(result: ServiceResult): String = when (result.status) {
    ProbeStatus.NOT_TESTED -> "—"
    ProbeStatus.RUNNING -> "Проверка…"
    ProbeStatus.AVAILABLE -> "✓ ${result.timings.totalMs?.let { "$it ms" } ?: ""}".trim()
    ProbeStatus.UNAVAILABLE -> "✕"
    ProbeStatus.UNKNOWN -> "? ${result.httpCode?.let { "HTTP $it" } ?: ""}".trim()
}

private fun speedValue(result: SpeedResult): String = when (result.status) {
    SpeedStatus.NOT_TESTED -> "—"
    SpeedStatus.RUNNING -> "Проверка…"
    SpeedStatus.SUCCESS -> String.format(Locale.US, "%.1f Mbps", result.mbps ?: 0.0)
    SpeedStatus.FAILED -> "Ошибка"
    SpeedStatus.CANCELLED -> "Отменено"
}

private fun yesNo(value: Boolean) = if (value) "Да" else "Нет"

private fun NetworkSnapshot.transportLabel(): String = when (transport) {
    NetworkTransport.NONE -> "Нет сети"
    NetworkTransport.WIFI -> "Wi‑Fi"
    NetworkTransport.CELLULAR -> "Мобильная сеть"
    NetworkTransport.ETHERNET -> "Ethernet"
    NetworkTransport.OTHER -> "Другая"
}
