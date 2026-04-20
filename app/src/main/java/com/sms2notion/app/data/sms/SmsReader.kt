package com.sms2notion.app.data.sms

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import com.sms2notion.app.data.db.MessageEntity

object SmsReader {

    /**
     * 단말의 SMS DB에서 전체 문자를 가져온다.
     * @param since 이 시각(ms) 이후만 가져오기. 0이면 전체.
     */
    fun readAll(context: Context, since: Long = 0L): List<MessageEntity> {
        val result = mutableListOf<MessageEntity>()
        val uri: Uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
        )
        val selection = if (since > 0) "${Telephony.Sms.DATE} >= ?" else null
        val selectionArgs = if (since > 0) arrayOf(since.toString()) else null

        context.contentResolver.query(
            uri, projection, selection, selectionArgs,
            "${Telephony.Sms.DATE} ASC"
        )?.use { c: Cursor ->
            val idIdx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIdx = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val addr = c.getString(addrIdx) ?: ""
                val body = c.getString(bodyIdx) ?: ""
                val date = c.getLong(dateIdx)
                val type = c.getInt(typeIdx)
                val name = lookupContactName(context, addr)
                result.add(
                    MessageEntity(
                        uniqueKey = "${id}_${date}_${addr}",
                        smsId = id,
                        address = addr,
                        contactName = name,
                        body = body,
                        date = date,
                        type = type,
                        kind = "SMS"
                    )
                )
            }
        }
        return result
    }

    fun lookupContactName(context: Context, phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
