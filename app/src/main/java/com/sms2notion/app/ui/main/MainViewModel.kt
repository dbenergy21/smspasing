package com.sms2notion.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sms2notion.app.App
import com.sms2notion.app.data.prefs.Settings
import com.sms2notion.app.worker.BulkSyncWorker
import com.sms2notion.app.worker.RetryPendingWorker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val dao = app.database.messages()

    val settings: StateFlow<Settings> = app.settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    val recent = dao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sentCount = dao.countSent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingCount = dao.countPending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun startFullSync() {
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

    fun retryFailed() {
        val req = OneTimeWorkRequestBuilder<RetryPendingWorker>()
            .addTag("retry_pending")
            .build()
        WorkManager.getInstance(getApplication())
            .enqueueUniqueWork("retry_pending", ExistingWorkPolicy.REPLACE, req)
    }
}
