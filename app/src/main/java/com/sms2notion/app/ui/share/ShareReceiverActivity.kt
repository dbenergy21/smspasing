package com.sms2notion.app.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sms2notion.app.App
import com.sms2notion.app.data.db.MessageEntity
import com.sms2notion.app.worker.ProcessMessageWorker
import kotlinx.coroutines.launch

/**
 * 타 앱 (예: Vito) 에서 "공유 → SMS→Notion" 으로 텍스트를 보내면
 * 이 Activity가 받아서 Notion으로 전송한다.
 *
 * 사용 예:
 *  - Vito 앱에서 통화 전사본을 "공유" → "SMS→Notion" 선택
 *  - 이 앱이 백그라운드로 즉시 전송 후 토스트 표시
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = extractText(intent) ?: run {
            Toast.makeText(this, "공유된 텍스트가 없습니다", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val app = application as App
        val now = System.currentTimeMillis()
        val source = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "공유된 통화/문자"
        val uniqueKey = "share_${now}_${text.hashCode()}"

        lifecycleScope.launch {
            app.database.messages().insertIgnore(
                MessageEntity(
                    uniqueKey = uniqueKey,
                    smsId = -1L,
                    address = "",
                    contactName = source,
                    body = text,
                    date = now,
                    type = 1,
                    kind = "VITO", // Notion 종류 속성: VITO
                )
            )
            val data = Data.Builder()
                .putString("uniqueKey", uniqueKey)
                .putString("address", "")
                .putString("contactName", source)
                .putString("body", text)
                .putLong("date", now)
                .putInt("type", 1)
                .putString("kind", "VITO")
                .build()
            val req = OneTimeWorkRequestBuilder<ProcessMessageWorker>()
                .setInputData(data)
                .addTag("process_message_share")
                .build()
            WorkManager.getInstance(this@ShareReceiverActivity).enqueue(req)

            Toast.makeText(
                this@ShareReceiverActivity,
                "Notion에 전송 요청했습니다",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    private fun extractText(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("text/") != true) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
    }
}
