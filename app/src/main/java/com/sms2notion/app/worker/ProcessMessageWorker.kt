package com.sms2notion.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sms2notion.app.App
import com.sms2notion.app.data.db.MessageEntity
import timber.log.Timber

/**
 * SmsReceiver에서 수신한 단일 메시지를 처리:
 *  1) DB 에 upsert
 *  2) LLM 분석 (설정된 경우)
 *  3) Notion 페이지 생성
 *  4) 결과 상태 업데이트
 *
 * 실패 시 WorkManager의 기본 재시도 정책(BackoffPolicy.EXPONENTIAL)이 동작하며,
 * Result.retry() 를 반환한다.
 */
class ProcessMessageWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as App
        val dao = app.database.messages()

        val uniqueKey = inputData.getString("uniqueKey") ?: return Result.failure()
        val address = inputData.getString("address") ?: ""
        val contactName = inputData.getString("contactName")
        val body = inputData.getString("body") ?: ""
        val date = inputData.getLong("date", System.currentTimeMillis())
        val type = inputData.getInt("type", 1)
        val kind = inputData.getString("kind") ?: "SMS"
        val smsId = inputData.getLong("smsId", 0L)

        val existing = dao.findByKey(uniqueKey)
        val entity = existing?.copy(
            address = address,
            contactName = contactName ?: existing.contactName,
            body = body,
            date = date,
            type = type,
            kind = kind,
        ) ?: MessageEntity(
            uniqueKey = uniqueKey,
            smsId = smsId,
            address = address,
            contactName = contactName,
            body = body,
            date = date,
            type = type,
            kind = kind,
        )
        dao.upsert(entity)

        // 이미 SENT 면 스킵
        if (existing?.status == "SENT" && !existing.notionPageId.isNullOrBlank()) {
            return Result.success()
        }

        // 1) LLM 분석
        val analysis = try {
            app.llmManager.analyze(body)
        } catch (e: Throwable) {
            Timber.w(e, "LLM skipped: %s", e.message)
            null
        }
        if (analysis != null) {
            dao.updateLlm(uniqueKey, analysis.category, analysis.extractedJson)
        }
        val withLlm = dao.findByKey(uniqueKey) ?: entity

        // 2) Notion 전송
        val result = app.notionRepo.createPage(withLlm)
        return if (result.isSuccess) {
            dao.updateStatus(uniqueKey, "SENT", result.getOrNull(), null)
            Result.success()
        } else {
            val err = result.exceptionOrNull()?.message ?: "unknown"
            dao.updateStatus(uniqueKey, "FAILED", null, err)
            Timber.w("Notion send failed: %s", err)
            if (runAttemptCount >= 5) Result.failure() else Result.retry()
        }
    }
}
