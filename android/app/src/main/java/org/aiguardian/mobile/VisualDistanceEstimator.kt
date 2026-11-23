package org.aiguardian.mobile

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A user calibration for the camera's monocular pinhole model.
 *
 * focalRatio is focalLengthPixels / frameHeightPixels. It is derived from a
 * detected reference object whose physical height and camera distance were
 * measured by the user. It is device-, camera-, zoom- and resolution-specific.
 */
data class DistanceCalibration(
    val focalRatio: Float,
    val referenceLabel: String,
    val referenceDistanceMeters: Float,
    val referenceHeightMeters: Float,
    val calibratedAtEpochMs: Long
) {
    init {
        require(focalRatio.isFinite() && focalRatio > 0f)
        require(referenceDistanceMeters.isFinite() && referenceDistanceMeters > 0f)
        require(referenceHeightMeters.isFinite() && referenceHeightMeters > 0f)
        require(calibratedAtEpochMs >= 0L)
    }
}

/**
 * A range, not a point measurement. The interval accounts for approximate
 * object-size priors and imperfect bounding boxes; it is not a safety guarantee.
 */
data class VisualDistanceEstimate(
    val minimumMeters: Float?,
    val maximumMeters: Float?,
    val minimumSteps: Int?,
    val maximumSteps: Int?,
    val quality: DistanceQuality
) {
    init {
        if (minimumMeters != null || maximumMeters != null) {
            require(minimumMeters != null && maximumMeters != null)
            require(minimumMeters.isFinite() && maximumMeters.isFinite())
            require(minimumMeters > 0f && maximumMeters >= minimumMeters)
        }
    }
}

enum class DistanceQuality {
    CALIBRATED_APPROXIMATE,
    NOT_CALIBRATED,
    UNSUPPORTED_OBJECT,
    INVALID_DETECTION
}

/**
 * Experimental monocular distance estimation.
 *
 * A valid estimate requires explicit camera calibration. It uses approximate
 * COCO-class physical-size priors, so the result is deliberately expressed as
 * a broad range. Never interpret it as exact distance or collision prediction.
 */
object VisualDistanceEstimator {

    // Approximate typical vertical dimensions in metres, not guarantees.
    private val typicalObjectHeightsMeters = mapOf(
        "person" to 1.70f,
        "chair" to 0.90f,
        "couch" to 0.85f,
        "bed" to 0.60f,
        "dining table" to 0.75f,
        "backpack" to 0.45f,
        "handbag" to 0.30f,
        "suitcase" to 0.60f,
        "bench" to 0.48f,
        "bicycle" to 1.05f,
        "motorcycle" to 1.15f,
        "car" to 1.50f,
        "bus" to 3.00f,
        "truck" to 2.80f,
        "potted plant" to 0.65f,
        "dog" to 0.50f,
        "cat" to 0.28f
    )

    /** True when this COCO label has a configured approximate physical-height prior. */
    fun supportsObject(label: String): Boolean =
        typicalObjectHeightsMeters.containsKey(label.trim().lowercase())

    /** Labels for which this experimental estimator has a size prior. */
    fun supportedObjectLabels(): Set<String> = typicalObjectHeightsMeters.keys

    // Conservative relative uncertainty around the class-size prior.
    private const val RELATIVE_UNCERTAINTY = 0.35f
    private const val MIN_BOX_HEIGHT_RATIO = 0.015f

    /**
     * Derives focalLength/frameHeight from a reference detection and the
     * reference object's measured height and distance.
     */
    fun calibrate(
        referenceLabel: String,
        normalizedBoxHeight: Float,
        knownHeightMeters: Float,
        measuredDistanceMeters: Float,
        nowEpochMs: Long
    ): DistanceCalibration? {
        if (
            referenceLabel.isBlank() ||
            !normalizedBoxHeight.isFinite() ||
            normalizedBoxHeight !in MIN_BOX_HEIGHT_RATIO..1f ||
            !knownHeightMeters.isFinite() || knownHeightMeters <= 0f ||
            !measuredDistanceMeters.isFinite() || measuredDistanceMeters <= 0f ||
            nowEpochMs < 0L
        ) return null

        // Similar triangles: h_px / H_frame = f_px / H_frame * H_real / Z.
        val focalRatio = normalizedBoxHeight * measuredDistanceMeters / knownHeightMeters
        if (!focalRatio.isFinite() || focalRatio <= 0f) return null

        return DistanceCalibration(
            focalRatio = focalRatio,
            referenceLabel = referenceLabel.trim().lowercase(),
            referenceDistanceMeters = measuredDistanceMeters,
            referenceHeightMeters = knownHeightMeters,
            calibratedAtEpochMs = nowEpochMs
        )
    }

    fun estimate(
        label: String,
        normalizedBoxHeight: Float,
        calibration: DistanceCalibration?,
        strideLengthMeters: Float? = null
    ): VisualDistanceEstimate {
        if (
            !normalizedBoxHeight.isFinite() ||
            normalizedBoxHeight !in MIN_BOX_HEIGHT_RATIO..1f
        ) {
            return unavailable(DistanceQuality.INVALID_DETECTION)
        }

        val objectHeight = typicalObjectHeightsMeters[label.trim().lowercase()]
            ?: return unavailable(DistanceQuality.UNSUPPORTED_OBJECT)

        if (calibration == null) {
            return unavailable(DistanceQuality.NOT_CALIBRATED)
        }

        val centralMeters =
            calibration.focalRatio * objectHeight / normalizedBoxHeight

        if (!centralMeters.isFinite() || centralMeters <= 0f) {
            return unavailable(DistanceQuality.INVALID_DETECTION)
        }

        val minimum = max(0.10f, centralMeters * (1f - RELATIVE_UNCERTAINTY))
        val maximum = max(minimum, centralMeters * (1f + RELATIVE_UNCERTAINTY))

        val validStride = strideLengthMeters?.takeIf {
            it.isFinite() && it in 0.20f..1.50f
        }

        val minSteps = validStride?.let { ceil(minimum / it).toInt().coerceAtLeast(1) }
        val maxSteps = validStride?.let { ceil(maximum / it).toInt().coerceAtLeast(1) }

        return VisualDistanceEstimate(
            minimumMeters = minimum,
            maximumMeters = maximum,
            minimumSteps = minSteps,
            maximumSteps = maxSteps,
            quality = DistanceQuality.CALIBRATED_APPROXIMATE
        )
    }

    private fun unavailable(quality: DistanceQuality) = VisualDistanceEstimate(
        minimumMeters = null,
        maximumMeters = null,
        minimumSteps = null,
        maximumSteps = null,
        quality = quality
    )
}
