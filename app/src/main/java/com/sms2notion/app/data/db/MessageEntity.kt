package com.sms2notion.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 전송 추적용 로컬 DB 엔티티.
 * _id(안드로이드 SMS provider의 id) + date + address 를 조합해 고유성 확보.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val uniqueKey: String,        // "<smsId>_<date>_<address>"
    val smsId: Long,
    val address: String,                      // 전화번호 (정규화)
    val contactName: String?,                 // 연락처 이름 (있다면)
    val body: String,                         // 본문
    val date: Long,                           // epoch millis
    val type: Int,                            // 1=수신, 2=발신, 3=초안, ...
    val kind: String = "SMS",                 // SMS / MMS / CALL_MEMO / VITO
    val notionPageId: String? = null,         // 전송 성공 시 Notion 페이지 ID
    val status: String = "PENDING",           // PENDING / SENT / FAILED
    val lastError: String? = null,
    val attemptCount: Int = 0,
    val llmCategory: String? = null,          // Gemma 분류 결과
    val llmExtractedJson: String? = null,     // Gemma 추출 결과(JSON 문자열)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
