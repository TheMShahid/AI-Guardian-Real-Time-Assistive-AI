package org.aiguardian.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import android.os.SystemClock
import android.util.Log

class YoloImageAnalyzer(
    context: Context,
    private val onDetections: (
        List<Detection>,
        Long,
        OutputTransform
    ) -> Unit,
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {

    companion object {
        private const val MIN_INTERVAL_MS = 500L
    }

    private val detector = lazy {
        YoloDetector(context.applicationContext)
    }

    private val transformFactory =
        ImageProxyTransformFactory().apply {
            setUsingCropRect(true)
            setUsingRotationDegrees(true)
        }

    private var lastAnalysisTime = 0L
    private var rgbaRowBuffer = ByteArray(0)
    private var pixelBuffer = IntArray(0)

    @Volatile
    private var closed = false

    @Synchronized
    override fun analyze(image: ImageProxy) {
        try {
            if (closed) return

            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastAnalysisTime < MIN_INTERVAL_MS) return
            lastAnalysisTime = currentTime

            val startTime = SystemClock.elapsedRealtime()
            val sourceTransform = transformFactory.getOutputTransform(image)

            val conversionStart = SystemClock.elapsedRealtime()
            val bitmap = imageToBitmap(image)
            val conversionTime = SystemClock.elapsedRealtime() - conversionStart

            try {
                val detectionStart = SystemClock.elapsedRealtime()
                val detections = detector.value.detect(bitmap)
                val detectionTime = SystemClock.elapsedRealtime() - detectionStart
                val totalTime = SystemClock.elapsedRealtime() - startTime

                Log.d(
                    "GuardianPerformance",
                    "frame=${image.width}x${image.height}, " +
                        "bitmapConversionMs=$conversionTime, " +
                        "yoloDetectionMs=$detectionTime, totalMs=$totalTime, " +
                        "detections=${detections.size}"
                )

                onDetections(detections, totalTime, sourceTransform)
            } finally {
                bitmap.recycle()
            }
        } catch (exception: Exception) {
            onError(exception.message ?: "Unknown camera inference error")
        } finally {
            image.close()
        }
    }

    /**
     * Uses bulk row reads instead of four ByteBuffer.get() calls per pixel.
     * Row padding is handled explicitly; reusable buffers reduce GC pressure.
     */
    private fun imageToBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val bufferStart = buffer.position()

        require(pixelStride >= 4) { "Expected RGBA camera output" }

        val rowByteCount = width * pixelStride
        if (rgbaRowBuffer.size < rowByteCount) {
            rgbaRowBuffer = ByteArray(rowByteCount)
        }
        if (pixelBuffer.size < width * height) {
            pixelBuffer = IntArray(width * height)
        }

        for (y in 0 until height) {
            val rowStart = bufferStart + y * rowStride
            require(rowStart + rowByteCount <= buffer.limit()) {
                "Invalid camera image buffer"
            }

            buffer.position(rowStart)
            buffer.get(rgbaRowBuffer, 0, rowByteCount)

            val outputRow = y * width
            for (x in 0 until width) {
                val offset = x * pixelStride
                val red = rgbaRowBuffer[offset].toInt() and 0xFF
                val green = rgbaRowBuffer[offset + 1].toInt() and 0xFF
                val blue = rgbaRowBuffer[offset + 2].toInt() and 0xFF
                val alpha = rgbaRowBuffer[offset + 3].toInt() and 0xFF

                pixelBuffer[outputRow + x] =
                    (alpha shl 24) or
                        (red shl 16) or
                        (green shl 8) or
                        blue
            }
        }

        val source = Bitmap.createBitmap(
            pixelBuffer,
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return source

        val matrix = Matrix().apply {
            postRotate(rotation.toFloat())
        }

        return try {
            Bitmap.createBitmap(source, 0, 0, width, height, matrix, true)
        } finally {
            source.recycle()
        }
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            if (detector.isInitialized()) detector.value.close()
            rgbaRowBuffer = ByteArray(0)
            pixelBuffer = IntArray(0)
        }
    }
}
