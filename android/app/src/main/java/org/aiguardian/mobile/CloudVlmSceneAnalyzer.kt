package org.aiguardian.mobile

import android.graphics.Bitmap
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Calls the AI Guardian backend. Provider credentials stay on the server;
 * the app only receives a per-deployment client key.
 *
 * Invoke from Dispatchers.IO. The caller owns and retains the Bitmap.
 */
class CloudVlmSceneAnalyzer(
    private val baseUrl: String,
    private val apiKey: String,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000
) {

    fun describeScene(snapshot: Bitmap): CloudSceneDescription {
        require(baseUrl.startsWith("https://")) {
            "Cloud inference requires an HTTPS backend URL"
        }
        require(apiKey.isNotBlank()) { "Cloud API key is not configured" }
        require(!snapshot.isRecycled) { "Camera snapshot is no longer available" }

        val imageBytes = ByteArrayOutputStream().use { output ->
            check(snapshot.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                "Could not encode camera snapshot"
            }
            output.toByteArray()
        }
        require(imageBytes.size <= MAX_UPLOAD_BYTES) {
            "Compressed camera image exceeds the cloud upload limit"
        }

        val boundary = "Guardian-${UUID.randomUUID()}"
        val connection = (URL("${baseUrl.trimEnd('/')}/v1/scene/describe")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-API-Key", apiKey)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            BufferedOutputStream(connection.outputStream).use { output ->
                output.write("--$boundary\r\n".toByteArray())
                output.write(
                    "Content-Disposition: form-data; name=\"image\"; filename=\"scene.jpg\"\r\n"
                        .toByteArray()
                )
                output.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
                output.write(imageBytes)
                output.write("\r\n--$boundary--\r\n".toByteArray())
                output.flush()
            }

            val status = connection.responseCode
            val responseStream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseText = responseStream?.use { stream ->
                BufferedInputStream(stream).bufferedReader().use { it.readText() }
            }.orEmpty()

            if (status !in 200..299) {
                val detail = runCatching {
                    JSONObject(responseText).optString("detail")
                }.getOrDefault("")
                throw CloudInferenceException(
                    status,
                    detail.ifBlank { "Cloud scene analysis failed (HTTP $status)." }
                )
            }

            val json = JSONObject(responseText)
            val description = json.optString("description").trim()
            check(description.isNotBlank()) {
                "Cloud inference returned an empty scene description"
            }
            return CloudSceneDescription(
                requestId = json.optString("request_id"),
                description = description,
                model = json.optString("model")
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MAX_UPLOAD_BYTES = 8 * 1024 * 1024
    }
}

data class CloudSceneDescription(
    val requestId: String,
    val description: String,
    val model: String
)

class CloudInferenceException(
    val httpStatus: Int,
    message: String
) : Exception(message)
