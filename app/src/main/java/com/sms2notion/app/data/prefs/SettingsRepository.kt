package com.sms2notion.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class LlmMode { OFF, ON_DEVICE, CLOUD }

data class Settings(
    val notionToken: String = "",
    val notionDatabaseId: String = "",
    val llmMode: LlmMode = LlmMode.OFF,
    val gemmaModelPath: String = "", // 예: /storage/emulated/0/Download/gemma-3n-E4B-it-int4.task
    val geminiApiKey: String = "",
    val geminiModel: String = "gemma-3-27b-it", // 클라우드 모드에서 호출할 모델
    val lastSyncTime: Long = 0L,
    val autoSyncEnabled: Boolean = true,
)

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("notion_token")
        private val KEY_DB = stringPreferencesKey("notion_db")
        private val KEY_LLM_MODE = stringPreferencesKey("llm_mode")
        private val KEY_GEMMA_PATH = stringPreferencesKey("gemma_path")
        private val KEY_GEMINI_KEY = stringPreferencesKey("gemini_key")
        private val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model")
        private val KEY_LAST_SYNC = longPreferencesKey("last_sync")
        private val KEY_AUTO_SYNC = booleanPreferencesKey("auto_sync")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            notionToken = p[KEY_TOKEN] ?: "",
            notionDatabaseId = p[KEY_DB] ?: "",
            llmMode = runCatching { LlmMode.valueOf(p[KEY_LLM_MODE] ?: "OFF") }.getOrDefault(LlmMode.OFF),
            gemmaModelPath = p[KEY_GEMMA_PATH] ?: "",
            geminiApiKey = p[KEY_GEMINI_KEY] ?: "",
            geminiModel = p[KEY_GEMINI_MODEL] ?: "gemma-3-27b-it",
            lastSyncTime = p[KEY_LAST_SYNC] ?: 0L,
            autoSyncEnabled = p[KEY_AUTO_SYNC] ?: true,
        )
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val current = Settings(
                notionToken = p[KEY_TOKEN] ?: "",
                notionDatabaseId = p[KEY_DB] ?: "",
                llmMode = runCatching { LlmMode.valueOf(p[KEY_LLM_MODE] ?: "OFF") }.getOrDefault(LlmMode.OFF),
                gemmaModelPath = p[KEY_GEMMA_PATH] ?: "",
                geminiApiKey = p[KEY_GEMINI_KEY] ?: "",
                geminiModel = p[KEY_GEMINI_MODEL] ?: "gemma-3-27b-it",
                lastSyncTime = p[KEY_LAST_SYNC] ?: 0L,
                autoSyncEnabled = p[KEY_AUTO_SYNC] ?: true,
            )
            val new = transform(current)
            p[KEY_TOKEN] = new.notionToken
            p[KEY_DB] = new.notionDatabaseId
            p[KEY_LLM_MODE] = new.llmMode.name
            p[KEY_GEMMA_PATH] = new.gemmaModelPath
            p[KEY_GEMINI_KEY] = new.geminiApiKey
            p[KEY_GEMINI_MODEL] = new.geminiModel
            p[KEY_LAST_SYNC] = new.lastSyncTime
            p[KEY_AUTO_SYNC] = new.autoSyncEnabled
        }
    }

    suspend fun current(): Settings = flow.first()
}
