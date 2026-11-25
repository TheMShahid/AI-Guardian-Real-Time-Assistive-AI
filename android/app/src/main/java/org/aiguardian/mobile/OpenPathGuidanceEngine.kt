package org.aiguardian.mobile

/**
 * Makes a conservative direction choice from independent free-space evidence.
 *
 * Evidence must come from a ground/free-space estimator, not from the absence
 * of YOLO detections. This class deliberately does not claim that a route is
 * safe; it only selects the comparatively more open visual corridor.
 */
internal class OpenPathGuidanceEngine(
    private val requiredStableFrames: Int = 3,
    private val minimumFreeSpaceConfidence: Float = 0.70f,
    private val minimumVisibleGroundRatio: Float = 0.40f,
    private val maximumObstacleOccupancyRatio: Float = 0.35f,
    private val minimumScoreMargin: Float = 0.10f
) {
    enum class Zone { FAR_LEFT, LEFT, CENTER, RIGHT, FAR_RIGHT }

    data class ZoneEvidence(
        val zone: Zone,
        /** Fraction of the zone with visible, classified ground (0..1). */
        val visibleGroundRatio: Float,
        /** Model confidence that the candidate region is traversable (0..1). */
        val freeSpaceConfidence: Float,
        /** Fraction of the candidate corridor occupied by detected obstacles (0..1). */
        val obstacleOccupancyRatio: Float
    )

    enum class Status {
        NEED_MORE_EVIDENCE,
        NO_COMPARATIVELY_OPEN_ROUTE,
        GUIDE_DIRECTION
    }

    data class Decision(
        val status: Status,
        val direction: Zone? = null,
        val score: Float? = null
    )

    private var lastFrameId = Long.MIN_VALUE
    private var pendingDirection: Zone? = null
    private var stableFrameCount = 0

    init {
        require(requiredStableFrames >= 1)
        require(minimumFreeSpaceConfidence in 0f..1f)
        require(minimumVisibleGroundRatio in 0f..1f)
        require(maximumObstacleOccupancyRatio in 0f..1f)
        require(minimumScoreMargin in 0f..1f)
    }

    /**
     * All five zones must be assessed for the same frame before comparison.
     * Missing/invalid evidence never becomes an "open" direction.
     */
    fun evaluate(evidence: List<ZoneEvidence>, frameId: Long): Decision {
        if (frameId == lastFrameId) return currentPendingDecision()
        lastFrameId = frameId

        if (evidence.size != Zone.entries.size ||
            evidence.map { it.zone }.toSet().size != Zone.entries.size
        ) {
            resetStability()
            return Decision(Status.NEED_MORE_EVIDENCE)
        }

        val valuesAreValid = evidence.all {
            it.visibleGroundRatio.isFinite() &&
                it.freeSpaceConfidence.isFinite() &&
                it.obstacleOccupancyRatio.isFinite() &&
                it.visibleGroundRatio in 0f..1f &&
                it.freeSpaceConfidence in 0f..1f &&
                it.obstacleOccupancyRatio in 0f..1f
        }
        if (!valuesAreValid) {
            resetStability()
            return Decision(Status.NEED_MORE_EVIDENCE)
        }

        val reliable = evidence.all {
            it.freeSpaceConfidence >= minimumFreeSpaceConfidence &&
                it.visibleGroundRatio >= minimumVisibleGroundRatio
        }
        if (!reliable) {
            resetStability()
            return Decision(Status.NEED_MORE_EVIDENCE)
        }

        val candidates = evidence
            .filter { it.obstacleOccupancyRatio <= maximumObstacleOccupancyRatio }
            .map { item ->
                val score = item.visibleGroundRatio *
                    item.freeSpaceConfidence *
                    (1f - item.obstacleOccupancyRatio)
                item.zone to score
            }
            .sortedByDescending { it.second }

        if (candidates.isEmpty()) {
            resetStability()
            return Decision(Status.NO_COMPARATIVELY_OPEN_ROUTE)
        }

        val best = candidates[0]
        val secondScore = candidates.getOrNull(1)?.second ?: 0f
        if (best.second - secondScore < minimumScoreMargin) {
            resetStability()
            return Decision(Status.NEED_MORE_EVIDENCE)
        }

        if (pendingDirection == best.first) {
            stableFrameCount += 1
        } else {
            pendingDirection = best.first
            stableFrameCount = 1
        }

        return if (stableFrameCount >= requiredStableFrames) {
            Decision(Status.GUIDE_DIRECTION, best.first, best.second)
        } else {
            Decision(Status.NEED_MORE_EVIDENCE)
        }
    }

    fun reset() {
        lastFrameId = Long.MIN_VALUE
        resetStability()
    }

    private fun currentPendingDecision() =
        Decision(Status.NEED_MORE_EVIDENCE)

    private fun resetStability() {
        pendingDirection = null
        stableFrameCount = 0
    }
}
