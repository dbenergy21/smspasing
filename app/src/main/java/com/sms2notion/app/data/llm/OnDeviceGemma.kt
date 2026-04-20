package com.sms2notion.app.data.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * MediaPipe Tasks GenAI의 LlmInference를 이용해 Gemma 3n .task 모델을 돌린다.
 * 참고: https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android
 *
 * Google AI Edge Gallery가 다운로드한 모델 파일을 그대로 사용 가능
 *  (예: /sdcard/Download/gemma-3n-E4B-it-int4.task)
 */
class OnDeviceGemma(private val context: Context) {

    private var engine: LlmInference? = null

    suspend fun load(modelPath: String) = withContext(Dispatchers.IO) {
        close()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .setMaxTopK(40)
            .build()
        engine = LlmInference.createFromOptions(context, options)
        Timber.i("Gemma loaded from %s", modelPath)
    }

    suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        val e = engine ?: error("Gemma not loaded")
        e.generateResponse(prompt)
    }

    fun close() {
        runCatching { engine?.close() }
        engine = null
    }
}
