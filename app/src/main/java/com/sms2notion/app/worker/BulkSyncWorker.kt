package com.sms2notion.app.worker

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.sms2notion.app.App
import com.sms2notion.app.data.sms.SmsReader
import com.sms2notion.app.ui.MainActivity
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * 기존에 쌓여 있는 모든 SMS를 읽어서 Notion으로 일괄 전송.
 * Notion API는 초당 3 req 제한이 있어 요청 사이 400ms 지연.
 */
class BulkSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as App
        setForeground(createForegroundInfo("전체 문자 동기화 중…"))

        val dao = app.database.messages()
        val afterDate = inputData.getLong("afterDate", 0L)
        val all = SmsReader.readAll(applicationContext, afterDate)
        Timber.i("BulkSync: %d messages to process (after=%d)", all.size, afterDate)

        // 먼저 DB에 모두 삽입 (IGNORE → 이미 있는 건 건너뜀)
        all.forEach { dao.insertIgnore(it) }

        // PENDING 상태인 항목들을 순회 처리
        var total = all.size
        var done = 0
        for (m in all) {
            val row = dao.findByKey(m.uniqueKey) ?: continue
            if (row.status == "SENT" && !row.notionPageId.isNullOrBlank()) {
                done++; continue
            }

            // LLM 분석
            val analysis = try { app.llmManager.analyze(row.body) } catch (_: Throwable) { null }
            if (analysis != null) {
                dao.updateLlm(row.uniqueKey, analysis.category, analysis.extractedJson)
            }
            val current = dao.findByKey(row.uniqueKey) ?: row

            // Notion 전송
            val result = app.notionRepo.createPage(current)
            if (result.isSuccess) {
                dao.updateStatus(row.uniqueKey, "SENT", result.getOrNull(), null)
            } else {
                val err = result.exceptionOrNull()?.message ?: "unknown"
                dao.updateStatus(row.uniqueKey, "FAILED", null, err)
                Timber.w("BulkSync fail %s: %s", row.uniqueKey, err)
            }

            done++
            // Progress 갱신
            if (done % 5 == 0 || done == total) {
                setForeground(createForegroundInfo("전체 문자 동기화 중… ($done/$total)"))
            }
            delay(400L) // Notion rate limit 여유
        }

        // LLM 리소스 해제
        app.llmManager.shutdown()

        app.settings.update { it.copy(lastSyncTime = System.currentTimeMillis()) }
        return Result.success()
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif: Notification = NotificationCompat.Builder(applicationContext, App.CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("SMS → Notion")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notif)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
