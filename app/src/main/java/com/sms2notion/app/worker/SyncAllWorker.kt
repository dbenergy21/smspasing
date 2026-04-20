package com.sms2notion.app.worker

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sms2notion.app.App
import com.sms2notion.app.data.sms.SmsReader
import timber.log.Timber

/**
 * 단말에 있는 모든 기존 SMS를 읽어 DB에 저장한 뒤,
 * 각 메시지마다 SendMessageWorker를 큐에 쌓는다.
 */
class SyncAllWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo("전체 문자 동기화 준비 중..."))
        val app = App.get()
        val dao = app.database.messages()

        val since = inputData.getLong("since", 0L)
        val all = try {
            SmsReader.readAll(applicationContext, since)
        } catch (e: SecurityException) {
            Timber.e(e, "SMS 권한 없음")
            return Result.failure()
        }
        Timber.i("SMS DB에서 ${all.size}건 읽음")

        var inserted = 0
        all.forEachIndexed { index, entity ->
            val existing = dao.findByKey(entity.uniqueKey)
            if (existing == null) {
                dao.insertIgnore(entity)
                inserted++
            } else if (existing.status == "SENT") {
                return@forEachIndexed
            }

            if (index % 50 == 0) {
                setForeground(createForegroundInfo("읽는 중... ${index + 1}/${all.size}"))
            }
        }

        // 전송 워커 일괄 큐잉 (중복 방지 위해 uniqueName 사용)
        val wm = WorkManager.getInstance(applicationContext)
        val pending = dao.findPending(limit = 10000)
        Timber.i("전송 큐잉: ${pending.size}건")
        pending.forEachIndexed { i, m ->
            val req = OneTimeWorkRequestBuilder<SendMessageWorker>()
                .setInputData(workDataOf("uniqueKey" to m.uniqueKey))
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30_000L, java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .build()
            wm.enqueueUniqueWork("send_${m.uniqueKey}", ExistingWorkPolicy.KEEP, req)
            if (i % 100 == 0) {
                setForeground(createForegroundInfo("전송 큐잉 중... ${i + 1}/${pending.size}"))
            }
        }

        app.settings.update { it.copy(lastSyncTime = System.currentTimeMillis()) }
        return Result.success()
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification: Notification = NotificationCompat.Builder(applicationContext, App.CHANNEL_SYNC)
            .setContentTitle("SMS → Notion 동기화")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 2001
    }
}
