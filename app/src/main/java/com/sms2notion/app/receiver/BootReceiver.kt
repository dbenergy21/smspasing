package com.sms2notion.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sms2notion.app.worker.RetryPendingWorker
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("Boot/PackageReplaced: 재시도 큐 실행")
        val req = OneTimeWorkRequestBuilder<RetryPendingWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("retry_pending", ExistingWorkPolicy.KEEP, req)
    }
}
