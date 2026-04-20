package com.sms2notion.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sms2notion.app.App
import com.sms2notion.app.data.prefs.LlmMode
import com.sms2notion.app.data.prefs.Settings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            app.settings.flow.collect { _state.value = it }
        }
    }

    fun onTokenChange(v: String) { _state.value = _state.value.copy(notionToken = v.trim()) }
    fun onDbIdChange(v: String) { _state.value = _state.value.copy(notionDatabaseId = v.trim()) }
    fun onLlmModeChange(m: LlmMode) { _state.value = _state.value.copy(llmMode = m) }
    fun onGemmaPathChange(v: String) { _state.value = _state.value.copy(gemmaModelPath = v.trim()) }
    fun onGeminiKeyChange(v: String) { _state.value = _state.value.copy(geminiApiKey = v.trim()) }
    fun onGeminiModelChange(v: String) { _state.value = _state.value.copy(geminiModel = v.trim()) }
    fun onAutoSyncChange(v: Boolean) { _state.value = _state.value.copy(autoSyncEnabled = v) }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            app.settings.update { s }
            _messages.send("저장되었습니다.")
        }
    }

    fun verifyNotion() {
        viewModelScope.launch {
            // 저장 후 검증
            app.settings.update { _state.value }
            val result = app.notionRepo.verify()
            _messages.send(
                if (result.isSuccess) "✅ Notion 연결 OK"
                else "❌ 연결 실패: ${result.exceptionOrNull()?.message}"
            )
        }
    }

    /**
     * Google AI Edge Gallery가 모델을 저장하는 일반 폴더를 자동 탐색.
     * (기본 위치는 Android/data/com.google.aiedge.gallery 내부 또는 /Download 하위)
     */
    fun pickAiEdgeGalleryDefault() {
        viewModelScope.launch {
            val candidates = listOf(
                "/storage/emulated/0/Download/gemma-3n-E4B-it-int4.task",
                "/storage/emulated/0/Download/gemma-3n-E4B-it.task",
                "/storage/emulated/0/Android/data/com.google.aiedge.gallery/files/models/gemma-3n-E4B-it-int4.task",
                "/sdcard/Download/gemma-3n-E4B-it-int4.task",
            )
            val found = candidates.firstOrNull { File(it).exists() }
            if (found != null) {
                _state.value = _state.value.copy(gemmaModelPath = found)
                _messages.send("발견: $found")
            } else {
                _messages.send("기본 위치에서 모델을 찾지 못했습니다. 직접 경로를 입력하세요.")
            }
        }
    }
}
