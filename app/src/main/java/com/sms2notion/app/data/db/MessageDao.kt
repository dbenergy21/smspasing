package com.sms2notion.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE uniqueKey = :key LIMIT 1")
    suspend fun findByKey(key: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE status = 'PENDING' OR status = 'FAILED' ORDER BY date ASC LIMIT :limit")
    suspend fun findPending(limit: Int = 50): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY date DESC LIMIT 500")
    fun observeRecent(): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'SENT'")
    fun countSent(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'FAILED' OR status = 'PENDING'")
    fun countPending(): Flow<Int>

    @Query("UPDATE messages SET status = :status, notionPageId = :pageId, lastError = :err, attemptCount = attemptCount + 1, updatedAt = :now WHERE uniqueKey = :key")
    suspend fun updateStatus(key: String, status: String, pageId: String?, err: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE messages SET llmCategory = :category, llmExtractedJson = :extracted, updatedAt = :now WHERE uniqueKey = :key")
    suspend fun updateLlm(key: String, category: String?, extracted: String?, now: Long = System.currentTimeMillis())
}
