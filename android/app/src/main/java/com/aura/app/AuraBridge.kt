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
    private val prefs = context.getSharedPreferences("aura_prefs", android.content.Context.MODE_PRIVATE)

    init {
        localEngine = LocalInferenceEngine(context)
        try {
            val py = Python.getInstance()
            // Loads aura_core/engine.py (Full Python Build)
            val engineModule = py.getModule("aura_core.engine")
            pythonEngine = engineModule.callAttr("OllamaClient")
            
            // 💾 PERSISTENCE: Load saved URL or use default
            val savedUrl = prefs.getString("orchestrator_url", "http://10.0.0.1:11434")
            pythonEngine?.callAttr("set_base_url", savedUrl)
        } catch (e: Exception) {
            android.util.Log.e("AuraBridge", "Python Engine Initialization Failed: ${e.message}")
        }
    }

    /**
     * Updates the base URL for the Python engine (Remote Bridge).
     * Persists the URL in SharedPreferences.
     */
    fun setOrchestratorUrl(url: String) {
        prefs.edit().putString("orchestrator_url", url).apply()
        pythonEngine?.callAttr("set_base_url", url)
    }

    /**
     * Gets the current orchestrator URL from preferences.
     */
    fun getOrchestratorUrl(): String {
        return prefs.getString("orchestrator_url", "http://10.0.0.1:11434") ?: "http://10.0.0.1:11434"
    }

    /**
     * Tests if the remote orchestrator is reachable.
     */
    fun testConnection(url: String, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "GET"
                val responseCode = connection.responseCode
                mainHandler.post { callback(responseCode == 200 || responseCode == 404) } // 404 is fine as long as server responds
            } catch (e: Exception) {
                mainHandler.post { callback(false) }
            }
        }.start()
    }

    /**
     * Toggles between Remote Python Engine and Standalone Local Engine.
     */
    fun setLocalMode(enabled: Boolean, modelPath: String? = null, onPartialResult: (String, Boolean) -> Unit = { _, _ -> }, onReady: (Boolean) -> Unit = {}) {
        useLocalInference = enabled
        if (enabled && modelPath != null) {
            localEngine?.initialize(modelPath, onPartialResult) { success ->
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
            localEngine?.generateResponse(prompt) { result, isComplete ->
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
