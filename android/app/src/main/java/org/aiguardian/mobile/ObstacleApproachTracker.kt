package org.aiguardian.mobile

/**
 * Detects a sustained increase in an obstacle's apparent image size.
 *
 * This is an approach trend, not metric speed, distance, or collision prediction.
 * It uses the existing label + direction key and therefore remains conservative
 * about identity: overlapping same-class objects may share a key.
 */
internal class ObstacleApproachTracker(
    private val requiredGrowthFrames: Int = 2,
    private val growthThreshold: Float = 0.12f,
    private val minimumAbsoluteGrowth: Float = 0.02f,
    private val maxObservationGapMs: Long = 1_500L
) {
    private data class Observation(
        var previousHeight: Float,
        var growthFrames: Int,
        var lastSeenAtMs: Long
    )

    private val observations = mutableMapOf<String, Observation>()
    private var lastFrameId: Long = Long.MIN_VALUE

    init {
        require(requiredGrowthFrames >= 1)
        require(growthThreshold > 0f && growthThreshold.isFinite())
        require(minimumAbsoluteGrowth > 0f && minimumAbsoluteGrowth.isFinite())
        require(maxObservationGapMs >= 0L)
    }

    fun update(
        obstacles: List<PrioritizedObstacle>,
        frameId: Long,
        nowMs: Long
    ): Set<String> {
        require(nowMs >= 0L)
        val keys = obstacles.associateBy {
            "${it.detection.label}|${it.direction}"
        }

        if (frameId == lastFrameId) {
            return keys.keys.filterTo(linkedSetOf()) { key ->
                (observations[key]?.growthFrames ?: 0) >= requiredGrowthFrames
            }
        }
        lastFrameId = frameId

        observations.entries.removeAll { (_, value) ->
            nowMs - value.lastSeenAtMs > maxObservationGapMs
        }

        keys.forEach { (key, obstacle) ->
            val height = obstacle.normalizedHeight
            if (!height.isFinite() || height <= 0f) return@forEach

            val previous = observations[key]
            if (previous == null) {
                observations[key] = Observation(height, 0, nowMs)
            } else {
                val absoluteGrowth = height - previous.previousHeight
                val relativeGrowth = absoluteGrowth / previous.previousHeight
                previous.growthFrames = if (
                    absoluteGrowth >= minimumAbsoluteGrowth &&
                    relativeGrowth >= growthThreshold
                ) {
                    previous.growthFrames + 1
                } else {
                    0
                }
                previous.previousHeight = height
                previous.lastSeenAtMs = nowMs
            }
        }

        return keys.keys.filterTo(linkedSetOf()) { key ->
            (observations[key]?.growthFrames ?: 0) >= requiredGrowthFrames
        }
    }

    fun reset() {
        observations.clear()
        lastFrameId = Long.MIN_VALUE
    }
}
