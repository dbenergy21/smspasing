package com.sms2notion.app.data.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Gemini API (v1beta) 클라이언트.
 * Gemma 3 오픈 모델들도 같은 엔드포인트에서 호출 가능.
 * 예: model = "gemma-3-27b-it" 또는 "gemini-2.5-flash"
 */
object GeminiClient {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(apiKey: String, model: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val payload = buildJsonObject {
                put("contents", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", buildJsonObject {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 512)
                })
            }

            val body = payload.toString()
                .toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                val str = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Timber.w("Gemini API 오류 ${resp.code}: $str")
                    return@withContext ""
                }
                // 응답 파싱: candidates[0].content.parts[0].text
                return@withContext try {
                    val root: JsonObject = json.parseToJsonElement(str).jsonObject
                    val cand = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    val parts = cand?.get("content")?.jsonObject?.get("parts")?.jsonArray
                    val text = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                    text.orEmpty()
                } catch (e: Exception) {
                    Timber.e(e, "Gemini 응답 파싱 실패")
                    ""
                }
            }
        }
}
