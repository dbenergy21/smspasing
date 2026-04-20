package com.sms2notion.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sms2notion.app.data.prefs.LlmMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHost.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                .verticalScroll(rememberScrollState())
        ) {
            Text("Notion 연동", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.notionToken,
                onValueChange = vm::onTokenChange,
                label = { Text("Integration Token (secret_...)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.notionDatabaseId,
                onValueChange = vm::onDbIdChange,
                label = { Text("Database ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "힌트: Notion 데이터베이스 URL에서 /뒤 32자리 또는 하이픈 포함 UUID 복사",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.verifyNotion() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("연결 테스트") }

            Spacer(Modifier.height(24.dp))
            Text("AI 처리 (Gemma)", style = MaterialTheme.typography.titleMedium)
            Text(
                "카테고리 자동 분류 + 주요정보 추출 (인증번호/금액/날짜/장소 등)",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            ModeRadio("사용 안 함", state.llmMode == LlmMode.OFF) { vm.onLlmModeChange(LlmMode.OFF) }
            ModeRadio("온디바이스 (Gemma 3n E4B)", state.llmMode == LlmMode.ON_DEVICE) { vm.onLlmModeChange(LlmMode.ON_DEVICE) }
            ModeRadio("클라우드 (Gemini API)", state.llmMode == LlmMode.CLOUD) { vm.onLlmModeChange(LlmMode.CLOUD) }

            if (state.llmMode == LlmMode.ON_DEVICE) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.gemmaModelPath,
                    onValueChange = vm::onGemmaPathChange,
                    label = { Text(".task 모델 파일 절대경로") },
                    placeholder = { Text("/storage/emulated/0/Download/gemma-3n-E4B-it-int4.task") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { vm.pickAiEdgeGalleryDefault() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("AI Edge Gallery 기본 위치에서 자동 탐색") }
                Text(
                    "모델을 찾지 못하면 파일 관리자에서 .task 파일을 Download 폴더로 복사한 뒤 경로 입력",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (state.llmMode == LlmMode.CLOUD) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.geminiApiKey,
                    onValueChange = vm::onGeminiKeyChange,
                    label = { Text("Gemini API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.geminiModel,
                    onValueChange = vm::onGeminiModelChange,
                    label = { Text("모델명 (예: gemma-3-27b-it, gemini-2.5-flash)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "aistudio.google.com 에서 무료 API Key 발급 가능",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("자동화", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("새 문자 수신 시 자동 전송", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.autoSyncEnabled,
                    onCheckedChange = vm::onAutoSyncChange
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.save() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("저장") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}
