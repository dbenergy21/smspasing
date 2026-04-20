package com.sms2notion.app.data.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
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
import java.io.File

/**
 * LLM 통합 관리자.
 * - 온디바이스: MediaPipe GenAI (Gemma 3n E4B, .task)
 * - 클라우드: Gemini API (동일 프롬프트, HTTP 호출은 GeminiClient에 위임)
 */
class LlmManager(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private var inference: LlmInference? = null
    private var loadedModelPath: String? = null
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun ensureOnDeviceLoaded(modelPath: String): LlmInference {
        mutex.withLock {
            if (inference != null && loadedModelPath == modelPath) return inference!!
            // 기존 세션 해제
            try { inference?.close() } catch (_: Exception) {}
            inference = null

            val file = File(modelPath)
            require(file.exists()) { "Gemma 모델 파일을 찾을 수 없음: $modelPath" }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .setMaxTopK(40)
                .build()
            inference = LlmInference.createFromOptions(context, options)
            loadedModelPath = modelPath
            Timber.i("Gemma 온디바이스 로드 완료: $modelPath")
            return inference!!
        }
    }

    /**
     * 문자 분석 결과.
     * category: 자동 분류 (스팸/인증번호/택배/금융/개인/업무/기타)
     * extractedJson: 주요 정보 (금액, 날짜, 장소, 발신기관 등)를 JSON 문자열로
     */
    data class Analysis(
        val category: String?,
        val extractedJson: String?,
    )

    suspend fun analyze(body: String, sender: String): Analysis = withContext(Dispatchers.Default) {
        val s = settings.current()
        if (s.llmMode == LlmMode.OFF) return@withContext Analysis(null, null)

        val prompt = buildPrompt(sender, body)
        val rawResponse = try {
            when (s.llmMode) {
                LlmMode.ON_DEVICE -> {
                    if (s.gemmaModelPath.isBlank()) return@withContext Analysis(null, null)
                    val llm = ensureOnDeviceLoaded(s.gemmaModelPath)
                    // MediaPipe 0.10.x: 동기 generateResponse 사용
                    llm.generateResponse(prompt)
                }
                LlmMode.CLOUD -> {
                    if (s.geminiApiKey.isBlank()) return@withContext Analysis(null, null)
                    GeminiClient.generate(
                        apiKey = s.geminiApiKey,
                        model = s.geminiModel,
                        prompt = prompt
                    )
                }
                LlmMode.OFF -> return@withContext Analysis(null, null)
            }
        } catch (e: Exception) {
            Timber.e(e, "LLM 추론 실패")
            return@withContext Analysis(null, null)
        }

        parseResult(rawResponse)
    }

    private fun buildPrompt(sender: String, body: String): String = """
당신은 한국어 SMS 문자를 분류하고 핵심 정보를 추출하는 도우미입니다.
아래 문자를 분석하여 반드시 **JSON만** 출력하세요. 설명/주석/코드블록 금지.

필수 필드:
- category: 다음 중 하나 ["스팸", "인증번호", "택배", "금융", "개인", "업무", "공공/행정", "기타"]
- info: 문자의 핵심 정보를 담은 객체. 다음 키를 가능한 만큼 채우세요.
  - sender_name (기관/사람 이름)
  - amount (금액, 숫자만)
  - date_time (ISO 또는 원문, 예: "2024-03-15 14:30")
  - location
  - code (인증번호/쿠폰코드 등)
  - tracking_no (송장번호)
  - url
  - summary (한 줄 요약, 40자 이내)

예시 출력:
{"category":"인증번호","info":{"sender_name":"카카오","code":"483921","summary":"카카오 인증번호 483921"}}

---
발신자: $sender
본문:
$body
---
출력:
""".trimIndent()

    private fun parseResult(raw: String): Analysis {
        if (raw.isBlank()) return Analysis(null, null)
        // JSON 블록 추출 (모델이 여분 텍스트 반환할 경우 대비)
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return Analysis(null, raw.take(500))
        val jsonStr = raw.substring(start, end + 1)
        return try {
            val obj: JsonObject = json.parseToJsonElement(jsonStr).jsonObject
            val category = obj["category"]?.jsonPrimitive?.contentOrNullSafe()
            val info = obj["info"]?.toString()
            Analysis(category, info ?: jsonStr)
        } catch (e: Exception) {
            Timber.w(e, "LLM JSON 파싱 실패, 원문 저장")
            Analysis(null, jsonStr.take(1000))
        }
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        try { this.content.ifBlank { null } } catch (_: Exception) { null }

    fun close() {
        try { inference?.close() } catch (_: Exception) {}
        inference = null
        loadedModelPath = null
    }
}
