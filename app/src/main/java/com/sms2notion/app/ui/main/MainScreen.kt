package com.sms2notion.app.ui.main

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.sms2notion.app.data.prefs.LlmMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    vm: MainViewModel = viewModel()
) {
    val settings by vm.settings.collectAsState()
    val sent by vm.sentCount.collectAsState()
    val pending by vm.pendingCount.collectAsState()
    val recent by vm.recent.collectAsState()
    val ctx = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHost.showSnackbar(it) }
    }

    val permissions = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val permState = rememberMultiplePermissionsState(permissions = permissions)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS → Notion") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // 상태 카드
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val ready = settings.notionToken.isNotBlank() &&
                            settings.notionDatabaseId.isNotBlank() &&
                            permState.allPermissionsGranted
                    Text(
                        text = if (ready) "● 실시간 수신 감시 활성" else "● 설정/권한 확인 필요",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("전송 완료: $sent", style = MaterialTheme.typography.bodyMedium)
                    Text("대기/실패: $pending", style = MaterialTheme.typography.bodyMedium)
                    if (settings.lastSyncTime > 0L) {
                        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
                        Text(
                            "마지막 전체 동기화: ${fmt.format(Date(settings.lastSyncTime))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "AI 모드: " + when (settings.llmMode) {
                            LlmMode.OFF -> "사용 안 함"
                            LlmMode.ON_DEVICE -> "온디바이스 (Gemma 3n E4B)"
                            LlmMode.CLOUD -> "클라우드 (${settings.geminiModel})"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (!permState.allPermissionsGranted) {
                Button(
                    onClick = { permState.launchMultiplePermissionRequest() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("권한 허용하기") }
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = { vm.startFullSync() },
                enabled = permState.allPermissionsGranted &&
                        settings.notionToken.isNotBlank() &&
                        settings.notionDatabaseId.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("전체 문자 동기화 시작 (처음 1회)") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.retryFailed() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("실패/대기 항목 재시도") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("배터리 최적화 예외 설정 (안정적 수신용)") }

            Spacer(Modifier.height(16.dp))
            Text("최근 문자", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(recent, key = { it.uniqueKey }) { m ->
                    MessageRow(m)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MessageRow(m: com.sms2notion.app.data.db.MessageEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val statusColor = when (m.status) {
            "SENT" -> MaterialTheme.colorScheme.primary
            "FAILED" -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        }
        Text(
            text = when (m.status) {
                "SENT" -> "✓"
                "FAILED" -> "✗"
                else -> "…"
            },
            color = statusColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = m.contactName?.let { "$it (${m.address})" } ?: m.address.ifBlank { "(알 수 없음)" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = m.body.take(60).replace('\n', ' '),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            if (!m.llmCategory.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(m.llmCategory, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)
        Text(
            text = fmt.format(Date(m.date)),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
