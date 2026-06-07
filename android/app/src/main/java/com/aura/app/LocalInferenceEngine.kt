package com.aura.app

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

class LocalInferenceEngine(private val context: Context) {
    private var llmInference: LlmInference? = null
    private var isInitialized = false

    /**
     * Initializes the MediaPipe engine with a local model file.
     */
    fun initialize(modelPath: String, onComplete: (Boolean) -> Unit) {
        if (isInitialized) {
            onComplete(true)
            return
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            onComplete(false)
            return
        }

        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .setTopK(40)
                .setTemperature(0.7f)
                .setRandomSeed(42)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isInitialized = true
            onComplete(true)
        } catch (e: Exception) {
            android.util.Log.e("AuraInference", "Engine Init Failed: ${e.message}")
            onComplete(false)
        }
    }

    /**
     * Streams a response from the local model.
     */
    fun generateResponse(prompt: String, onUpdate: (String) -> Unit) {
        if (!isInitialized || llmInference == null) {
            onUpdate("[ERROR] Local Engine Not Ready")
            return
        }

        Thread {
            try {
                // MediaPipe LLM Inference provides a streamable generate method
                // Note: The actual API might vary slightly by version, 0.10.14 uses this pattern
                val result = llmInference?.generateResponse(prompt)
                if (result != null) {
                    onUpdate(result)
                }
            } catch (e: Exception) {
                onUpdate("[INFERENCE_ERROR] ${e.message}")
            }
        }.start()
    }
}
