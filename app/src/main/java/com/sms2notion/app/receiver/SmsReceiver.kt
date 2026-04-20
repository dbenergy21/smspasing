package com.sms2notion.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sms2notion.app.App
import com.sms2notion.app.data.db.MessageEntity
import com.sms2notion.app.data.sms.SmsReader
import com.sms2notion.app.worker.ProcessMessageWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 새로 수신된 SMS를 감지 → Room DB에 저장 → ProcessMessageWorker 큐잉.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // 같은 시점에 도착한 멀티파트 SMS를 발신자별로 합친다
        val grouped = messages.groupBy { it.originatingAddress ?: "" }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = App.get()
                grouped.forEach { (addr, parts) ->
                    val body = parts.joinToString(separator = "") { it.messageBody ?: "" }
                    val date = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                    val uniqueKey = "sms_${date}_${addr}_${body.hashCode()}"
                    val name = SmsReader.lookupContactName(context, addr)

                    app.database.messages().insertIgnore(
                        MessageEntity(
                            uniqueKey = uniqueKey,
                            smsId = -1L,
                            address = addr,
                            contactName = name,
                            body = body,
                            date = date,
                            type = 1, // inbox
                            kind = "SMS",
                        )
                    )

                    val data = Data.Builder()
                        .putString("uniqueKey", uniqueKey)
                        .putString("address", addr)
                        .putString("contactName", name)
                        .putString("body", body)
                        .putLong("date", date)
                        .putInt("type", 1)
                        .putString("kind", "SMS")
                        .build()

                    val req = OneTimeWorkRequestBuilder<ProcessMessageWorker>()
                        .setInputData(data)
                        .addTag("process_message")
                        .build()
                    WorkManager.getInstance(context)
                        .enqueueUniqueWork("process_$uniqueKey", ExistingWorkPolicy.KEEP, req)

                    Timber.i("SMS 수신 enqueue: %s / %s", addr, body.take(20))
                }
            } catch (e: Exception) {
                Timber.e(e, "SMS 수신 처리 실패")
            } finally {
                pending.finish()
            }
        }
    }
}
