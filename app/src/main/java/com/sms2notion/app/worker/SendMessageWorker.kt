package com.sms2notion.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sms2notion.app.App
import timber.log.Timber

/**
 * 단일 메시지를 LLM 처리 후 Notion으로 전송.
 * 실패 시 재시도 (WorkManager 백오프).
 */
class SendMessageWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val key = inputData.getString("uniqueKey") ?: return Result.failure()
        val app = App.get()
        val dao = app.database.messages()
        val msg = dao.findByKey(key) ?: return Result.failure()
        if (msg.status == "SENT") return Result.success()

        return try {
            // 1) LLM 분석 (설정되어 있으면)
            val analysis = try {
                app.llmManager.analyze(msg.body, msg.contactName ?: msg.address)
            } catch (e: Exception) {
                Timber.w(e, "LLM 분석 건너뜀")
                null
            }
            val enriched = if (analysis != null) {
                dao.updateLlm(key, analysis.category, analysis.extractedJson)
                msg.copy(
                    llmCategory = analysis.category,
                    llmExtractedJson = analysis.extractedJson
                )
            } else msg

            // 2) Notion 전송
            val pageId = app.notionRepo.createPage(enriched)
            dao.updateStatus(key, "SENT", pageId, null)
            Timber.i("Notion 전송 성공: $key → $pageId")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Notion 전송 실패 (재시도): $key")
            dao.updateStatus(key, "FAILED", null, e.message?.take(500))
            if (runAttemptCount >= 8) Result.failure() else Result.retry()
        }
    }
}
