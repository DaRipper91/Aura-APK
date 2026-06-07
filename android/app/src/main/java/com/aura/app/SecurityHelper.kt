package com.aura.app

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object SecurityHelper {
    /**
     * Calculates the SHA-256 hash of a file.
     */
    fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        FileInputStream(file).use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies if the file's SHA-256 matches the expected hash.
     */
    fun verifyIntegrity(file: File, expectedHash: String): Boolean {
        return try {
            val actualHash = calculateSHA256(file)
            actualHash.equals(expectedHash, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}
