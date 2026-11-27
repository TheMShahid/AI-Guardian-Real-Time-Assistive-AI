package org.aiguardian.mobile

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A depth image aligned to one camera frame. Values are metric distances in metres;
 * zero, negative, NaN and infinite values represent invalid pixels.
 *
 * The producer must guarantee that depth and RGB coordinates are registered. A
 * relative/scale-ambiguous depth map must never be wrapped as metric metres.
 */
class MetricDepthFrame(
    val width: Int,
    val height: Int,
    val timestampNanos: Long,
    depthMeters: FloatArray
) {
    private val values = depthMeters.copyOf()

    init {
        require(width > 0 && height > 0)
        require(timestampNanos >= 0L)
        require(values.size == width * height)
    }

    internal fun valueAt(x: Int, y: Int): Float = values[y * width + x]
}

enum class DepthSampleQuality {
    VALID,
    INVALID_REGION,
    LOW_VALID_COVERAGE,
    HIGH_VARIANCE,
    STALE_FRAME
}

/** Robust depth statistics sampled from the central portion of a detection box. */
data class DetectionDepthSample(
    val medianMeters: Float?,
    val lowerQuartileMeters: Float?,
    val upperQuartileMeters: Float?,
    val validPixelRatio: Float,
    val timestampDeltaNanos: Long,
    val quality: DepthSampleQuality
) {
    val isUsable: Boolean
        get() = quality == DepthSampleQuality.VALID && medianMeters != null
}

/**
 * Associates one detection with a metric depth frame. This class deliberately does
 * not infer depth from YOLO box size or class labels.
 */
object DetectionDepthSampler {
    private const val MIN_VALID_PIXEL_RATIO = 0.45f
    private const val MAX_RELATIVE_IQR = 0.30f
    private const val MIN_ABSOLUTE_IQR_METERS = 0.35f
    private const val MAX_SAMPLES = 256
    private const val DEFAULT_MAX_TIMESTAMP_DELTA_NANOS = 100_000_000L

    fun sample(
        frame: MetricDepthFrame,
        detectionTimestampNanos: Long,
        leftRatio: Float,
        topRatio: Float,
        rightRatio: Float,
        bottomRatio: Float,
        maxTimestampDeltaNanos: Long = DEFAULT_MAX_TIMESTAMP_DELTA_NANOS
    ): DetectionDepthSample {
        require(detectionTimestampNanos >= 0L)
        require(maxTimestampDeltaNanos >= 0L)

        val delta = absoluteDifference(frame.timestampNanos, detectionTimestampNanos)
        if (delta > maxTimestampDeltaNanos) {
            return unavailable(DepthSampleQuality.STALE_FRAME, delta)
        }

        if (
            !leftRatio.isFinite() || !topRatio.isFinite() ||
            !rightRatio.isFinite() || !bottomRatio.isFinite() ||
            leftRatio < 0f || topRatio < 0f ||
            rightRatio > 1f || bottomRatio > 1f ||
            rightRatio <= leftRatio || bottomRatio <= topRatio
        ) {
            return unavailable(DepthSampleQuality.INVALID_REGION, delta)
        }

        // Ignore the outer 20% of the box on each side to reduce boundary mixing.
        val boxWidth = rightRatio - leftRatio
        val boxHeight = bottomRatio - topRatio
        val innerLeft = leftRatio + boxWidth * 0.20f
        val innerRight = rightRatio - boxWidth * 0.20f
        val innerTop = topRatio + boxHeight * 0.20f
        val innerBottom = bottomRatio - boxHeight * 0.20f

        val x0 = (innerLeft * frame.width).toInt().coerceIn(0, frame.width - 1)
        val x1 = ceil(innerRight * frame.width).toInt().coerceIn(x0 + 1, frame.width)
        val y0 = (innerTop * frame.height).toInt().coerceIn(0, frame.height - 1)
        val y1 = ceil(innerBottom * frame.height).toInt().coerceIn(y0 + 1, frame.height)

        val regionPixels = (x1 - x0) * (y1 - y0)
        val stride = max(1, ceil(kotlin.math.sqrt(
            regionPixels.toDouble() / MAX_SAMPLES
        )).toInt())
        val samples = ArrayList<Float>(min(regionPixels, MAX_SAMPLES))

        var inspected = 0
        for (y in y0 until y1 step stride) {
            for (x in x0 until x1 step stride) {
                inspected++
                val value = frame.valueAt(x, y)
                if (value.isFinite() && value in 0.10f..20.0f) {
                    samples.add(value)
                }
            }
        }

        val validRatio = if (inspected == 0) 0f else samples.size.toFloat() / inspected
        if (samples.isEmpty() || validRatio < MIN_VALID_PIXEL_RATIO) {
            return DetectionDepthSample(
                null, null, null, validRatio, delta,
                DepthSampleQuality.LOW_VALID_COVERAGE
            )
        }

        samples.sort()
        val median = quantile(samples, 0.50f)
        val lower = quantile(samples, 0.25f)
        val upper = quantile(samples, 0.75f)
        val iqr = upper - lower

        val quality = if (iqr > max(MIN_ABSOLUTE_IQR_METERS, median * MAX_RELATIVE_IQR)) {
            DepthSampleQuality.HIGH_VARIANCE
        } else {
            DepthSampleQuality.VALID
        }

        return DetectionDepthSample(
            median, lower, upper, validRatio, delta, quality
        )
    }

    private fun quantile(sorted: List<Float>, q: Float): Float {
        if (sorted.size == 1) return sorted[0]
        val position = (sorted.size - 1) * q
        val lowerIndex = position.toInt()
        val upperIndex = (lowerIndex + 1).coerceAtMost(sorted.lastIndex)
        val fraction = position - lowerIndex
        return sorted[lowerIndex] + (sorted[upperIndex] - sorted[lowerIndex]) * fraction
    }

    private fun absoluteDifference(first: Long, second: Long): Long =
        if (first >= second) first - second else second - first

    private fun unavailable(
        quality: DepthSampleQuality,
        delta: Long
    ) = DetectionDepthSample(null, null, null, 0f, delta, quality)
}
