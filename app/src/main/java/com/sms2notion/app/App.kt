package com.sms2notion.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import com.sms2notion.app.data.db.AppDatabase
import com.sms2notion.app.data.prefs.SettingsRepository
import com.sms2notion.app.data.notion.NotionRepository
import com.sms2notion.app.data.llm.LlmManager
import timber.log.Timber

class App : Application(), Configuration.Provider {

    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val notionRepo: NotionRepository by lazy { NotionRepository(settings) }
    val llmManager: LlmManager by lazy { LlmManager(this, settings) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SYNC,
                    "동기화 작업",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_STATUS,
                    "상태 알림",
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    companion object {
        const val CHANNEL_SYNC = "sync_channel"
        const val CHANNEL_STATUS = "status_channel"

        @Volatile private var instance: App? = null
        fun get(): App = instance!!
    }
}
