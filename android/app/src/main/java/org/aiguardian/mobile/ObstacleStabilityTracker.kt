package org.aiguardian.mobile

/**
 * Confirms that a spatial obstacle key is observed in multiple distinct
 * camera-analysis frames before it is eligible for spoken announcement.
 *
 * Keys are expected to represent the existing label + direction identity.
 * This is temporal filtering only; it does not estimate distance or safety.
 */
internal class ObstacleStabilityTracker(
    private val requiredObservations: Int = 2,
    private val maxObservationGapMs: Long = 1_500L
) {
    private data class Observation(
        var count: Int,
        var lastSeenAtMs: Long
    )

    private val observations = mutableMapOf<String, Observation>()
    private var lastFrameId: Long = Long.MIN_VALUE

    init {
        require(requiredObservations >= 1) {
            "requiredObservations must be at least 1"
        }
        require(maxObservationGapMs >= 0L) {
            "maxObservationGapMs cannot be negative"
        }
    }

    fun update(
        currentKeys: Set<String>,
        frameId: Long,
        nowMs: Long
    ): Set<String> {
        require(nowMs >= 0L) { "nowMs cannot be negative" }

        // Settings changes can re-run the caller's effect for the same frame.
        // Do not count one camera frame more than once.
        if (frameId == lastFrameId) {
            return currentKeys.filterTo(linkedSetOf()) { key ->
                (observations[key]?.count ?: 0) >= requiredObservations
            }
        }

        lastFrameId = frameId

        observations.entries.removeAll { (_, observation) ->
            nowMs - observation.lastSeenAtMs > maxObservationGapMs
        }

        currentKeys.forEach { key ->
            val previous = observations[key]
            if (previous == null) {
                observations[key] = Observation(
                    count = 1,
                    lastSeenAtMs = nowMs
                )
            } else {
                previous.count += 1
                previous.lastSeenAtMs = nowMs
            }
        }

        return currentKeys.filterTo(linkedSetOf()) { key ->
            (observations[key]?.count ?: 0) >= requiredObservations
        }
    }

    fun reset() {
        observations.clear()
        lastFrameId = Long.MIN_VALUE
    }
}
