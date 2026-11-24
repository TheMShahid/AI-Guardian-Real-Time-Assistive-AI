package org.aiguardian.mobile

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Geometry-only sample from one detector frame. No camera pixels are retained.
 */
data class DistanceCalibrationCapture(
    val referenceLabel: String,
    val normalizedBoxHeight: Float,
    val normalizedCenterX: Float,
    val normalizedCenterY: Float,
    val capturedAtEpochMs: Long
) {
    init {
        require(referenceLabel.isNotBlank())
        require(normalizedBoxHeight.isFinite() && normalizedBoxHeight in 0.015f..1f)
        require(normalizedCenterX.isFinite() && normalizedCenterX in 0f..1f)
        require(normalizedCenterY.isFinite() && normalizedCenterY in 0f..1f)
        require(capturedAtEpochMs >= 0L)
    }

    companion object {
        fun fromDetection(
            label: String,
            boxLeftPixels: Float,
            boxTopPixels: Float,
            boxRightPixels: Float,
            boxBottomPixels: Float,
            frameWidthPixels: Int,
            frameHeightPixels: Int,
            capturedAtEpochMs: Long
        ): DistanceCalibrationCapture? {
            if (
                frameWidthPixels <= 0 || frameHeightPixels <= 0 ||
                !boxLeftPixels.isFinite() || !boxTopPixels.isFinite() ||
                !boxRightPixels.isFinite() || !boxBottomPixels.isFinite() ||
                boxLeftPixels < 0f || boxTopPixels < 0f ||
                boxRightPixels > frameWidthPixels.toFloat() ||
                boxBottomPixels > frameHeightPixels.toFloat() ||
                boxRightPixels <= boxLeftPixels || boxBottomPixels <= boxTopPixels
            ) return null

            val width = frameWidthPixels.toFloat()
            val height = frameHeightPixels.toFloat()
            val boxHeight = (boxBottomPixels - boxTopPixels) / height
            val centerX = ((boxLeftPixels + boxRightPixels) / 2f) / width
            val centerY = ((boxTopPixels + boxBottomPixels) / 2f) / height

            if (
                !boxHeight.isFinite() || boxHeight !in 0.015f..1f ||
                !centerX.isFinite() || centerX !in 0f..1f ||
                !centerY.isFinite() || centerY !in 0f..1f
            ) return null

            return runCatching {
                DistanceCalibrationCapture(
                    referenceLabel = label.trim().lowercase(),
                    normalizedBoxHeight = boxHeight,
                    normalizedCenterX = centerX,
                    normalizedCenterY = centerY,
                    capturedAtEpochMs = capturedAtEpochMs
                )
            }.getOrNull()
        }
    }
}

/**
 * Collects consecutive observations of one stationary reference object.
 * A capture is emitted only after enough frames agree on object class,
 * apparent height and normalized image position.
 */
class DistanceCalibrationCaptureTracker(
    private val requiredSamples: Int = 5,
    private val maxRelativeHeightDeviation: Float = 0.10f,
    private val maxCenterMovement: Float = 0.08f,
    private val maxGapBetweenSamplesMs: Long = 1_500L,
    private val maxCaptureDurationMs: Long = 12_000L
) {
    private val samples = mutableListOf<DistanceCalibrationCapture>()
    private var captureStartedAtEpochMs: Long? = null
    var isCollecting: Boolean = false
        private set

    init {
        require(requiredSamples >= 3)
        require(maxRelativeHeightDeviation > 0f && maxRelativeHeightDeviation < 1f)
        require(maxCenterMovement > 0f && maxCenterMovement < 1f)
        require(maxGapBetweenSamplesMs > 0L)
        require(maxCaptureDurationMs > 0L)
    }

    fun start(nowEpochMs: Long = 0L) {
        require(nowEpochMs >= 0L)
        samples.clear()
        captureStartedAtEpochMs = nowEpochMs
        isCollecting = true
    }

    fun cancel() {
        samples.clear()
        captureStartedAtEpochMs = null
        isCollecting = false
    }

    /** Returns true and cancels collection when the capture deadline is exceeded. */
    fun hasTimedOut(nowEpochMs: Long): Boolean {
        require(nowEpochMs >= 0L)
        val startedAt = captureStartedAtEpochMs ?: return false
        if (!isCollecting || nowEpochMs - startedAt < maxCaptureDurationMs) return false
        cancel()
        return true
    }

    /**
     * Returns a stable median sample when collection succeeds.
     * Returns null while collecting or when the current sample is invalid.
     */
    fun add(sample: DistanceCalibrationCapture): DistanceCalibrationCapture? {
        if (!isCollecting || hasTimedOut(sample.capturedAtEpochMs)) return null

        val previous = samples.lastOrNull()
        if (
            previous != null &&
            (sample.referenceLabel != previous.referenceLabel ||
                sample.capturedAtEpochMs <= previous.capturedAtEpochMs ||
                sample.capturedAtEpochMs - previous.capturedAtEpochMs > maxGapBetweenSamplesMs ||
                abs(sample.normalizedCenterX - previous.normalizedCenterX) > maxCenterMovement ||
                abs(sample.normalizedCenterY - previous.normalizedCenterY) > maxCenterMovement)
        ) {
            samples.clear()
        }

        samples.add(sample)

        if (samples.size < requiredSamples) return null

        val heights = samples.map { it.normalizedBoxHeight }.sorted()
        val median = heights[heights.size / 2]
        val deviation = heights.maxOf { abs(it - median) } / median
        val minX = samples.minOf { it.normalizedCenterX }
        val maxX = samples.maxOf { it.normalizedCenterX }
        val minY = samples.minOf { it.normalizedCenterY }
        val maxY = samples.maxOf { it.normalizedCenterY }

        if (
            deviation > maxRelativeHeightDeviation ||
            maxX - minX > maxCenterMovement ||
            maxY - minY > maxCenterMovement
        ) {
            // Keep the newest observation as the start of a fresh stable window.
            val newest = samples.last()
            samples.clear()
            samples.add(newest)
            return null
        }

        val medianX = medianOf(samples.map { it.normalizedCenterX })
        val medianY = medianOf(samples.map { it.normalizedCenterY })
        val medianTime = samples[samples.size / 2].capturedAtEpochMs

        val result = DistanceCalibrationCapture(
            referenceLabel = sample.referenceLabel,
            normalizedBoxHeight = median,
            normalizedCenterX = medianX,
            normalizedCenterY = medianY,
            capturedAtEpochMs = medianTime
        )
        cancel()
        return result
    }

    private fun medianOf(values: List<Float>): Float {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2f
        } else {
            sorted[middle]
        }
    }

    fun collectedSampleCount(): Int = samples.size
}
