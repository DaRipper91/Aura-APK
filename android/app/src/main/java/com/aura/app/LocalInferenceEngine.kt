package com.aura.app

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

class LocalInferenceEngine(private val context: Context) {
    private var llmInference: LlmInference? = null
    private var isInitialized = false

    /**
     * Streams a response from the local model using the asynchronous MediaPipe API.
     */
    fun generateResponse(prompt: String, onUpdate: (String, Boolean) -> Unit) {
        if (!isInitialized || llmInference == null) {
            onUpdate("[ERROR] Local Engine Not Ready", true)
            return
        }

        try {
            // Use generateResponseAsync for real-time streaming chunks
            llmInference?.generateResponseAsync(prompt)
        } catch (e: Exception) {
            onUpdate("[INFERENCE_ERROR] ${e.message}", true)
        }
    }

    /**
     * Initializes the MediaPipe engine with a local model file and a result listener for streaming.
     */
    fun initialize(modelPath: String, onPartialResult: (String, Boolean) -> Unit, onComplete: (Boolean) -> Unit) {
        if (isInitialized) {
            onComplete(true)
            return
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            android.util.Log.e("AuraInference", "Model file not found at: $modelPath")
            onComplete(false)
            return
        }

        Thread {
            try {
                android.util.Log.d("AuraInference", "Loading model into RAM (CPU Backend): $modelPath")
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(512) // Slightly smaller for stability during testing
                    .setTopK(40)
                    .setTemperature(0.7f)
                    .setRandomSeed(42)
                    .setResultListener { result, isComplete ->
                        onPartialResult(result, isComplete)
                    }
                    .setErrorListener { error ->
                        android.util.Log.e("AuraInference", "Async Error: ${error.message}")
                    }
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                isInitialized = true
                android.util.Log.d("AuraInference", "Model Loaded Successfully on CPU")
                onComplete(true)
            } catch (e: Exception) {
                android.util.Log.e("AuraInference", "Engine Init Failed: ${e.message}")
                e.printStackTrace()
                onComplete(false)
            }
        }.start()
    }
}
