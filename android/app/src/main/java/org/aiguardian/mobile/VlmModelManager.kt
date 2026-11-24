package org.aiguardian.mobile

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Explicit, one-time on-device download for the optional VLM model.
 * The large model is deliberately not bundled in the APK or Git repository.
 */
internal class VlmModelManager(context: Context) {

    companion object {
        private const val TAG = "GuardianVLMDownload"
        const val MODEL_FILE_NAME = "SmolVLM2-500M.litertlm"
        const val MODEL_SIZE_LABEL = "about 361 MB"
        private const val MODEL_URL =
            "https://huggingface.co/litert-community/SmolVLM2-500M/resolve/main/SmolVLM2-500M.litertlm"
        private const val EXPECTED_SHA256 =
            "b808b328d845a600a33c5295f93d9217487317bd334dbc91b2a8d50e26e60ad0"
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER_SIZE = 1024 * 1024
    }

    private val modelDirectory = File(context.applicationContext.filesDir, "models")
    val modelFile: File
        get() = File(modelDirectory, MODEL_FILE_NAME)

    fun isModelAvailable(): Boolean {
        if (!modelFile.isFile || modelFile.length() <= 0L) return false
        return try {
            sha256(modelFile) == EXPECTED_SHA256
        } catch (error: Exception) {
            Log.e(TAG, "Could not verify installed VLM model", error)
            false
        }
    }

    /**
     * Blocking download. Call from Dispatchers.IO.
     * The destination is published only after SHA-256 verification succeeds.
     */
    fun downloadModel(onProgress: (Int) -> Unit = {}): File {
        if (isModelAvailable()) {
            onProgress(100)
            return modelFile
        }

        check(modelDirectory.exists() || modelDirectory.mkdirs()) {
            "Could not create the private VLM model directory"
        }

        val partialFile = File(modelDirectory, "$MODEL_FILE_NAME.download")
        if (partialFile.exists()) partialFile.delete()

        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "AI-Guardian-Android")
        }

        try {
            connection.connect()
            val responseCode = connection.responseCode
            check(responseCode in 200..299) {
                "VLM model download failed (HTTP $responseCode)"
            }

            val expectedLength = connection.contentLengthLong
            var downloaded = 0L
            val digest = MessageDigest.getInstance("SHA-256")

            BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                BufferedOutputStream(FileOutputStream(partialFile), BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count

                        if (expectedLength > 0L) {
                            val percent = ((downloaded * 100L) / expectedLength)
                                .toInt().coerceIn(0, 99)
                            onProgress(percent)
                        }
                    }
                    output.flush()
                }
            }

            val actualHash = digest.digest().toHexString()
            check(actualHash == EXPECTED_SHA256) {
                "Downloaded VLM model failed SHA-256 verification"
            }

            if (modelFile.exists()) {
                check(modelFile.delete()) { "Could not replace the old VLM model file" }
            }
            check(partialFile.renameTo(modelFile)) {
                "Could not finalize the downloaded VLM model"
            }

            onProgress(100)
            Log.i(TAG, "VLM model downloaded and SHA-256 verified; bytes=$downloaded")
            return modelFile
        } catch (error: Exception) {
            partialFile.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }
}
