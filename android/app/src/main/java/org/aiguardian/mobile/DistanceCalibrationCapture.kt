package org.aiguardian.mobile

/**
 * A one-frame calibration sample captured from the live YOLO detector.
 * It contains geometry only; no image or camera frame is persisted.
 */
data class DistanceCalibrationCapture(
    val referenceLabel: String,
    val normalizedBoxHeight: Float,
    val capturedAtEpochMs: Long
) {
    init {
        require(referenceLabel.isNotBlank())
        require(normalizedBoxHeight.isFinite() && normalizedBoxHeight in 0.015f..1f)
        require(capturedAtEpochMs >= 0L)
    }

    companion object {
        fun fromDetection(
            label: String,
            boxHeightPixels: Float,
            frameHeightPixels: Int,
            capturedAtEpochMs: Long
        ): DistanceCalibrationCapture? {
            if (frameHeightPixels <= 0 || !boxHeightPixels.isFinite() || boxHeightPixels <= 0f) {
                return null
            }
            val ratio = boxHeightPixels / frameHeightPixels.toFloat()
            if (!ratio.isFinite() || ratio !in 0.015f..1f) return null
            return runCatching {
                DistanceCalibrationCapture(
                    referenceLabel = label.trim().lowercase(),
                    normalizedBoxHeight = ratio,
                    capturedAtEpochMs = capturedAtEpochMs
                )
            }.getOrNull()
        }
    }
}
