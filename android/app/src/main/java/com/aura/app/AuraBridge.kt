package com.aura.app

import android.os.Handler
import android.os.Looper
import com.chaquo.python.Python
import com.chaquo.python.PyObject

class AuraBridge(private val context: android.content.Context) {
    private var pythonEngine: PyObject? = null
    private var localEngine: LocalInferenceEngine? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var useLocalInference = false

    init {
        localEngine = LocalInferenceEngine(context)
        try {
            val py = Python.getInstance()
            // Loads aura_core/engine.py (Full Python Build)
            val engineModule = py.getModule("aura_core.engine")
            pythonEngine = engineModule.callAttr("OllamaClient")
        } catch (e: Exception) {
            android.util.Log.e("AuraBridge", "Python Engine Initialization Failed: ${e.message}")
        }
    }

    /**
     * Updates the base URL for the Python engine (Remote Bridge).
     */
    fun setOrchestratorUrl(url: String) {
        pythonEngine?.callAttr("set_base_url", url)
    }

    /**
     * Toggles between Remote Python Engine and Standalone Local Engine.
     */
    fun setLocalMode(enabled: Boolean, modelPath: String? = null, onReady: (Boolean) -> Unit = {}) {
        useLocalInference = enabled
        if (enabled && modelPath != null) {
            localEngine?.initialize(modelPath) { success ->
                mainHandler.post { onReady(success) }
            }
        } else {
            onReady(true)
        }
    }

    /**
     * Pipes the prompt to the active engine (Python/Remote or Standalone/Local).
     */
    fun sendPrompt(prompt: String, model: String = "qwen2.5:7b", callback: (String) -> Unit) {
        if (useLocalInference) {
            localEngine?.generateResponse(prompt) { result ->
                mainHandler.post { callback(result) }
            }
            return
        }

        Thread {
            try {
                // Chaquopy translates Python generators to Java Iterators
                val generator = pythonEngine?.callAttr("stream_chat", model, prompt)
                val iterator = generator?.callAttr("__iter__")
                
                var fullResponse = ""
                while (true) {
                    val chunk = iterator?.callAttr("__next__")?.toString() ?: break
                    fullResponse += chunk
                    
                    // Push state update to the UI thread for safe Compose updates and Haptics
                    mainHandler.post {
                        callback(fullResponse) 
                    }
                }
            } catch (e: Exception) {
                // Python StopIteration ends the loop; catch it silently
            }
        }.start()
    }
}
