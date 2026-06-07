package com.aura.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ModelManager(private val context: Context) {
    
    // Placeholder URLs for MediaPipe Quantized Models
    private val modelUrls = mapOf(
        "QWEN_1.5B" to "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_seq128_q8_ekv1280.task",
        "PHI3_MINI" to "https://huggingface.co/google/phi-3-mini-4k-instruct-tflite/resolve/main/phi-3-mini-4k-instruct.bin"
    )

    private val expectedHashes = mapOf(
        "QWEN_1.5B" to "8771564e61c908c2199bcaa28b0ff9c5f55afb2ae73fbe263142a067113968df",
        "QWEN_1.5B_CI" to "094406159c788591e102f9e42152862a98f1f77395c32988185794770245a491",
        "PHI3_MINI" to "4057864f199b82885906d2d2a507851897c55afb2ae73fbe263142a067113968df" // Placeholder, CI will confirm
    )

    fun getModelFile(modelName: String): File {
        return File(context.getExternalFilesDir(null), "$modelName.bin")
    }

    fun isModelDownloaded(modelName: String): Boolean {
        return getModelFile(modelName).exists()
    }

    fun isModelInAssets(modelName: String): Boolean {
        return try {
            context.assets.list("models")?.contains("$modelName.bin") ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extracts a model from the APK assets to internal storage.
     * MediaPipe requires a file path on the filesystem.
     */
    fun extractModelFromAssets(modelName: String, onComplete: (Boolean, String?) -> Unit) {
        val targetFile = getModelFile(modelName)
        if (targetFile.exists()) {
            onComplete(true, null)
            return
        }

        Thread {
            try {
                val assetPath = "models/$modelName.bin"
                val inputStream: InputStream = context.assets.open(assetPath)
                val outputStream = FileOutputStream(targetFile)
                
                val buffer = ByteArray(1024 * 8)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                // Verify Integrity
                val actualHash = SecurityHelper.calculateSHA256(targetFile)
                val isValid = expectedHashes.values.contains(actualHash)
                
                if (!isValid) {
                    android.util.Log.e("AuraModel", "Integrity Check Failed for $modelName. Actual: $actualHash")
                    targetFile.delete()
                    onComplete(false, "Integrity Check Failed: $actualHash")
                    return@Thread
                }

                onComplete(true, null)
            } catch (e: Exception) {
                android.util.Log.e("AuraModel", "Asset Extraction Failed: ${e.message}")
                onComplete(false, e.message)
            }
        }.start()
    }

    fun downloadModel(modelName: String, onComplete: (Boolean) -> Unit) {
        val url = modelUrls[modelName] ?: return onComplete(false)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Aura // Pulling Model")
            .setDescription("Downloading $modelName for standalone inference")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, "$modelName.bin")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        
        Thread {
            while (!isModelDownloaded(modelName)) {
                Thread.sleep(2000)
            }
            onComplete(true)
        }.start()
    }
}
