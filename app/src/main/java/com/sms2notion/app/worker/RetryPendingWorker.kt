package com.sms2notion.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sms2notion.app.App
import timber.log.Timber

/**
 * FAILED / PENDING 상태의 메시지를 재큐잉.
 * BootReceiver 및 수동 버튼에서 호출.
 */
class RetryPendingWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = App.get()
        val pending = app.database.messages().findPending(limit = 10000)
        Timber.i("재시도 대상: ${pending.size}건")
        val wm = WorkManager.getInstance(applicationContext)
        pending.forEach { m ->
            val req = OneTimeWorkRequestBuilder<SendMessageWorker>()
                .setInputData(workDataOf("uniqueKey" to m.uniqueKey))
                .build()
            wm.enqueueUniqueWork("send_${m.uniqueKey}", ExistingWorkPolicy.KEEP, req)
        }
        return Result.success()
    }
}
