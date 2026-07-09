package com.sms2notion.app.data.llm

import android.content.Context
import com.sms2notion.app.data.prefs.LlmMode
import com.sms2notion.app.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * LLM 통합 관리자.
 * - 온디바이스: OnDeviceGemma (MediaPipe GenAI, Gemma 3n .task)
 * - 클라우드: GeminiClient (Gemini/Gemma API)
 *
 * 설정에 따라 자동으로 분기하며, 실패 시 null 을 리턴하여 파이프라인을 막지 않는다.
 */
class LlmManager(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private var gemma: OnDeviceGemma? = null
    private var loadedModelPath: String? = null
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    data class Analysis(
        val category: String?,
        val extractedJson: String?,
    )

    private suspend fun ensureGemmaLoaded(modelPath: String): OnDeviceGemma {
        mutex.withLock {
            if (gemma != null && loadedModelPath == modelPath) return gemma!!
            runCatching { gemma?.close() }
            val g = OnDeviceGemma(context)
            g.load(modelPath)
            gemma = g
            loadedModelPath = modelPath
            return g
        }
    }

    /**
     * 문자 본문을 분석해 카테고리 + 주요정보(JSON)를 반환.
     * LLM 모드가 OFF 이면 즉시 null.
     */
    suspend fun analyze(body: String): Analysis? = withContext(Dispatchers.Default) {
        val s = settings.current()
        if (s.llmMode == LlmMode.OFF || body.isBlank()) return@withContext null

        val prompt = buildPrompt(body)
        val raw = try {
            when (s.llmMode) {
                LlmMode.ON_DEVICE -> {
                    if (s.gemmaModelPath.isBlank()) return@withContext null
                    val g = ensureGemmaLoaded(s.gemmaModelPath)
                    g.generate(prompt)
                }
                LlmMode.CLOUD -> {
                    if (s.geminiApiKey.isBlank()) return@withContext null
                    GeminiClient.generate(
                        apiKey = s.geminiApiKey,
                        model = s.geminiModel,
                        prompt = prompt
                    )
                }
                LlmMode.OFF -> return@withContext null
            }
        } catch (e: Throwable) {
            Timber.w(e, "LLM 추론 실패")
            return@withContext null
        }

        parseResult(raw)
    }

    private fun buildPrompt(body: String): String = """
당신은 한국어 SMS 문자를 분류하고 핵심 정보를 추출하는 도우미입니다.
아래 문자를 분석하여 반드시 **JSON만** 출력하세요. 설명/주석/코드블록 금지.

필수 필드:
- category: 다음 중 하나 ["스팸", "인증번호", "택배", "금융", "개인", "업무", "공공/행정", "기타"]
- info: 핵심 정보를 담은 객체. 다음 키를 가능한 만큼 채우세요.
  - sender_name (기관/사람 이름)
  - amount (금액, 숫자만)
  - date_time (ISO 또는 원문)
  - location
  - code (인증번호/쿠폰코드)
  - tracking_no (송장번호)
  - url
  - summary (한 줄 요약, 40자 이내)

예시 출력:
{"category":"인증번호","info":{"sender_name":"카카오","code":"483921","summary":"카카오 인증번호 483921"}}

---
문자 본문:
$body
---
출력:
""".trimIndent()

    private fun parseResult(raw: String): Analysis? {
        if (raw.isBlank()) return null
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return Analysis(null, raw.take(500))
        val jsonStr = raw.substring(start, end + 1)
        return try {
            val obj: JsonObject = json.parseToJsonElement(jsonStr).jsonObject
            val category = obj["category"]?.jsonPrimitive?.let {
                runCatching { it.content.ifBlank { null } }.getOrNull()
            }
            val info = obj["info"]?.toString()
            Analysis(category, info ?: jsonStr)
        } catch (e: Exception) {
            Timber.w(e, "LLM JSON 파싱 실패, 원문 저장")
            Analysis(null, jsonStr.take(1000))
        }
    }

    /** 리소스 해제 (BulkSync 완료 시) */
    fun shutdown() {
        runCatching { gemma?.close() }
        gemma = null
        loadedModelPath = null
    }
}
