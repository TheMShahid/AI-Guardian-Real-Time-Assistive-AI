package org.aiguardian.mobile

/**
 * Allows a previously announced foreground object to receive a useful distance
 * update when its calibrated range materially decreases.
 *
 * This is a speech de-duplication policy, not object tracking or collision prediction.
 */
internal class ForegroundDistanceUpdateTracker(
    private val closerDistanceRatio: Float = 0.70f,
    private val minimumUpdateIntervalMs: Long = 2_000L
) {
    private data class Baseline(
        val key: String,
        val centerMeters: Float?,
        val minimumSteps: Int?,
        val announcedAtMs: Long
    )

    private var baseline: Baseline? = null

    init {
        require(closerDistanceRatio in 0.1f..0.95f)
        require(minimumUpdateIntervalMs >= 0L)
    }

    fun shouldAnnounce(
        candidate: ForegroundObject,
        estimate: VisualDistanceEstimate,
        nowMs: Long
    ): Boolean {
        require(nowMs >= 0L)
        if (estimate.quality != DistanceQuality.CALIBRATED_APPROXIMATE) return false

        val currentCenter = estimate.centerMeters() ?: return false
        val current = baseline ?: return false
        if (current.key != key(candidate)) return false
        if (nowMs < current.announcedAtMs ||
            nowMs - current.announcedAtMs < minimumUpdateIntervalMs
        ) return false

        val materiallyCloser = current.centerMeters?.let {
            currentCenter <= it * closerDistanceRatio
        } == true
        val currentMinimumSteps = estimate.minimumSteps
        val previousMinimumSteps = current.minimumSteps
        val stepsReduced = currentMinimumSteps != null &&
            previousMinimumSteps != null &&
            previousMinimumSteps - currentMinimumSteps >= 2

        return materiallyCloser || stepsReduced
    }

    fun markAnnounced(
        candidate: ForegroundObject,
        estimate: VisualDistanceEstimate?,
        nowMs: Long
    ) {
        require(nowMs >= 0L)
        val usableEstimate = estimate?.takeIf {
            it.quality == DistanceQuality.CALIBRATED_APPROXIMATE
        }
        baseline = Baseline(
            key = key(candidate),
            centerMeters = usableEstimate?.centerMeters(),
            minimumSteps = usableEstimate?.minimumSteps,
            announcedAtMs = nowMs
        )
    }

    fun reset() {
        baseline = null
    }

    private fun key(candidate: ForegroundObject): String =
        "${candidate.label.trim().lowercase()}|${candidate.direction}"

    private fun VisualDistanceEstimate.centerMeters(): Float? {
        val minimum = minimumMeters ?: return null
        val maximum = maximumMeters ?: return null
        if (!minimum.isFinite() || !maximum.isFinite() ||
            minimum <= 0f || maximum < minimum
        ) return null
        return (minimum + maximum) / 2f
    }
}
