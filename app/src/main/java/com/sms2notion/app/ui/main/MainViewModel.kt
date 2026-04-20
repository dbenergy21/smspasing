package com.sms2notion.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sms2notion.app.App
import com.sms2notion.app.worker.BulkSyncWorker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val dao = app.database.messages()

    val recent = dao.observeRecent()
    val sentCount = dao.countSent()
    val pendingCount = dao.countPending()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun startBulkSync() {
        viewModelScope.launch {
            val s = app.settings.current()
            if (s.notionToken.isBlank() || s.notionDatabaseId.isBlank()) {
                _messages.send("먼저 설정에서 Notion 토큰과 DB ID를 입력하세요.")
                return@launch
            }
            _isSyncing.value = true
            val data = Data.Builder().putLong("afterDate", 0L).build()
            val req = OneTimeWorkRequestBuilder<BulkSyncWorker>()
                .setInputData(data)
                .addTag("bulk_sync")
                .build()
            WorkManager.getInstance(getApplication())
                .enqueueUniqueWork("bulk_sync", ExistingWorkPolicy.KEEP, req)
            _messages.send("전체 동기화를 시작했습니다.")
            _isSyncing.value = false
        }
    }
}
