package com.sms2notion.app.data.notion

import com.sms2notion.app.data.db.MessageEntity
import com.sms2notion.app.data.prefs.SettingsRepository
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class NotionRepository(private val settings: SettingsRepository) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client: OkHttpClient by lazy {
        val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(log)
            .build()
    }

    private val api: NotionApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.notion.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NotionApi::class.java)
    }

    private val isoFormatter by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    /**
     * MessageEntity → Notion 페이지 생성.
     * DB 속성: 제목(Title), 날짜(Date), 발신/수신(Select), 번호(Phone), 내용(Text), 종류(Select)
     * 추가 자동 속성: 카테고리(Select, LLM이 있을 때), 주요정보(Text, LLM이 있을 때)
     */
    suspend fun createPage(message: MessageEntity): String {
        val s = settings.current()
        require(s.notionToken.isNotBlank()) { "Notion 토큰이 설정되지 않았습니다." }
        require(s.notionDatabaseId.isNotBlank()) { "Notion DB ID가 설정되지 않았습니다." }

        val titleText = buildString {
            append(message.contactName ?: message.address.ifBlank { "(알 수 없음)" })
            val direction = when (message.type) {
                2 -> " → 보냄"
                1 -> " ← 받음"
                else -> ""
            }
            append(direction)
        }.take(80)

        val direction = when (message.type) {
            2 -> "보낸 문자"
            1 -> "받은 문자"
            else -> "기타"
        }

        val properties = buildJsonObject {
            // 제목 (Title)
            put("제목", buildJsonObject {
                put("title", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", buildJsonObject { put("content", titleText) })
                    })
                })
            })
            // 날짜 (Date)
            put("날짜", buildJsonObject {
                put("date", buildJsonObject {
                    put("start", isoFormatter.format(Date(message.date)))
                })
            })
            // 발신/수신 (Select)
            put("발신/수신", buildJsonObject {
                put("select", buildJsonObject { put("name", direction) })
            })
            // 번호 (Phone)
            put("번호", buildJsonObject {
                put("phone_number", JsonPrimitive(message.address.ifBlank { "unknown" }))
            })
            // 내용 (Text / rich_text) - 속성에도 요약본 저장 (2000자 제한)
            put("내용", buildJsonObject {
                put("rich_text", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", buildJsonObject {
                            put("content", message.body.take(1900))
                        })
                    })
                })
            })
            // 종류 (Select)
            put("종류", buildJsonObject {
                put("select", buildJsonObject { put("name", message.kind) })
            })

            // LLM 결과 (선택 속성)
            if (!message.llmCategory.isNullOrBlank()) {
                put("카테고리", buildJsonObject {
                    put("select", buildJsonObject { put("name", message.llmCategory) })
                })
            }
            if (!message.llmExtractedJson.isNullOrBlank()) {
                put("주요정보", buildJsonObject {
                    put("rich_text", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", buildJsonObject {
                                put("content", message.llmExtractedJson.take(1900))
                            })
                        })
                    })
                })
            }
        }

        // 본문(children)에 긴 문자 전체 저장 - 2000자 단위로 분할
        val bodyChunks = message.body.chunked(1900)
        val children = buildJsonArray {
            for (chunk in bodyChunks) {
                add(buildJsonObject {
                    put("object", "block")
                    put("type", "paragraph")
                    put("paragraph", buildJsonObject {
                        put("rich_text", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", buildJsonObject { put("content", chunk) })
                            })
                        })
                    })
                })
            }
        }

        val payload = buildJsonObject {
            put("parent", buildJsonObject { put("database_id", s.notionDatabaseId) })
            put("properties", properties)
            if (bodyChunks.isNotEmpty()) put("children", children)
        }

        val resp = api.createPage(
            auth = "Bearer ${s.notionToken}",
            body = payload
        )
        return resp.id ?: error("Notion 응답에 페이지 id 없음")
    }
}
