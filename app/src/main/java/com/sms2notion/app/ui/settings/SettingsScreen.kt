package com.sms2notion.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sms2notion.app.data.prefs.LlmMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> snack.showSnackbar(msg) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionTitle("Notion 연결")

            OutlinedTextField(
                value = state.notionToken,
                onValueChange = vm::onTokenChange,
                label = { Text("Integration Token (secret_...)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.notionDatabaseId,
                onValueChange = vm::onDbIdChange,
                label = { Text("Database ID") },
                singleLine = true,
                supportingText = { Text("예: 32자리 UUID (하이픈 있어도 OK)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.verifyNotion() }) { Text("연결 테스트") }
                Button(onClick = { vm.save() }) { Text("저장") }
            }

            Divider()
            SectionTitle("AI 분석 (카테고리/정보 추출)")

            LlmModeSelector(
                selected = state.llmMode,
                onSelect = vm::onLlmModeChange
            )

            if (state.llmMode == LlmMode.ON_DEVICE) {
                OutlinedTextField(
                    value = state.gemmaModelPath,
                    onValueChange = vm::onGemmaPathChange,
                    label = { Text("Gemma .task 모델 파일 경로") },
                    supportingText = {
                        Text("예: /storage/emulated/0/Download/gemma-3n-E4B-it-int4.task")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { vm.pickAiEdgeGalleryDefault() }) {
                    Text("AI Edge Gallery 기본 폴더 사용")
                }
            }

            if (state.llmMode == LlmMode.CLOUD) {
                OutlinedTextField(
                    value = state.geminiApiKey,
                    onValueChange = vm::onGeminiKeyChange,
                    label = { Text("Gemini API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = { Text("Google AI Studio에서 발급 (무료 쿼터 제공)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.geminiModel,
                    onValueChange = vm::onGeminiModelChange,
                    label = { Text("모델명") },
                    singleLine = true,
                    supportingText = { Text("예: gemma-3-27b-it, gemini-2.5-flash") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Divider()
            SectionTitle("기타")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = state.autoSyncEnabled,
                    onCheckedChange = vm::onAutoSyncChange
                )
                Spacer(Modifier.width(8.dp))
                Text("새 문자 자동 전송")
            }

            Text(
                "⚠️ 주의: 이 앱은 개인용 사이드로드 용도로 설계되었습니다. 토큰은 기기 내부에만 저장됩니다.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun LlmModeSelector(selected: LlmMode, onSelect: (LlmMode) -> Unit) {
    Column {
        LlmMode.entries.forEach { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
                Spacer(Modifier.width(4.dp))
                Text(
                    when (mode) {
                        LlmMode.OFF -> "사용 안 함 (Notion 전송만)"
                        LlmMode.ON_DEVICE -> "온디바이스 (Gemma 3n E4B .task)"
                        LlmMode.CLOUD -> "클라우드 (Gemini API)"
                    }
                )
            }
        }
    }
}
