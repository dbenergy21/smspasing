package com.sms2notion.app.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.sms2notion.app.data.db.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    vm: MainViewModel = viewModel(),
) {
    val recent by vm.recent.collectAsState(initial = emptyList())
    val sent by vm.sentCount.collectAsState(initial = 0)
    val pending by vm.pendingCount.collectAsState(initial = 0)
    val working by vm.isSyncing.collectAsState()

    val perms = rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )
    )

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snack.showSnackbar(it) }
    }

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
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.startBulkSync() },
                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                text = { Text(if (working) "동기화 중…" else "전체 동기화") },
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!perms.allPermissionsGranted) {
                ElevatedCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("권한이 필요합니다", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("문자 읽기/수신, 연락처, 알림 권한을 허용해주세요.")
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { perms.launchMultiplePermissionRequest() }) {
                            Text("권한 요청")
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatCard("전송 완료", sent.toString(), Modifier.weight(1f))
                StatCard("대기/실패", pending.toString(), Modifier.weight(1f))
            }

            Text("최근 메시지", style = MaterialTheme.typography.titleSmall)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(recent, key = { it.uniqueKey }) { m -> MessageRow(m) }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun MessageRow(m: MessageEntity) {
    val df = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val color = when (m.status) {
        "SENT" -> Color(0xFF66BB6A)
        "FAILED" -> Color(0xFFEF5350)
        else -> Color(0xFFFFCA28)
    }
    ElevatedCard {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .padding(end = 0.dp)
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (m.contactName ?: m.address).ifBlank { "(번호없음)" } + "  · " + df.format(Date(m.date)),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    m.body.take(80).replace("\n", " "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
                if (!m.llmCategory.isNullOrBlank()) {
                    AssistChip(onClick = {}, label = { Text(m.llmCategory) })
                }
            }
        }
    }
}
