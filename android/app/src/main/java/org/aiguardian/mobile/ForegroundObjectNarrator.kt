package org.aiguardian.mobile

import kotlin.math.sqrt

/**
 * Selects one prominent, confidently detected object for general scene narration.
 *
 * This is intentionally separate from PathAwareObstacleAnalyzer: ordinary object
 * narration must not require an object to be in the lower walking corridor.
 */
internal data class ForegroundObject(
    val label: String,
    val direction: String,
    val score: Float
)

internal object ForegroundObjectSelector {
    private const val MIN_CONFIDENCE = 0.40f

    fun select(detections: List<Detection>): ForegroundObject? {
        return detections.mapNotNull { detection ->
            val width = detection.frameWidth
            val height = detection.frameHeight
            val box = detection.boundingBox

            if (
                detection.label.isBlank() ||
                !detection.confidence.isFinite() ||
                detection.confidence < MIN_CONFIDENCE ||
                width <= 0 || height <= 0 ||
                !box.left.isFinite() || !box.top.isFinite() ||
                !box.right.isFinite() || !box.bottom.isFinite() ||
                box.right <= box.left || box.bottom <= box.top
            ) {
                return@mapNotNull null
            }

            val centerX = ((box.left + box.right) / 2f / width).coerceIn(0f, 1f)
            val centerY = ((box.top + box.bottom) / 2f / height).coerceIn(0f, 1f)
            val areaRatio = (
                (box.right - box.left).coerceAtMost(width.toFloat()) *
                    (box.bottom - box.top).coerceAtMost(height.toFloat()) /
                    (width.toFloat() * height.toFloat())
                ).coerceIn(0f, 1f)

            val centerDistance = sqrt(
                (centerX - 0.5f) * (centerX - 0.5f) +
                    (centerY - 0.5f) * (centerY - 0.5f)
            ) / 0.7072f
            val centerScore = 1f - centerDistance.coerceIn(0f, 1f)
            val sizeScore = sqrt(areaRatio).coerceIn(0f, 1f)
            val score = detection.confidence * 0.50f +
                centerScore * 0.25f +
                sizeScore * 0.25f

            val direction = when {
                centerX < 0.38f -> "left"
                centerX > 0.62f -> "right"
                else -> "ahead"
            }

            ForegroundObject(
                label = detection.label.trim().lowercase(),
                direction = direction,
                score = score
            )
        }.maxByOrNull { it.score }
    }
}

/**
 * Requires consecutive observations and suppresses repeat narration for the same
 * label while it remains in view, even if its estimated direction jitters. A new
 * announcement is allowed only after the label has been absent for a meaningful
 * interval.
 *
 * This is a visual-event key, not a physical object identity: a detector without
 * tracking IDs cannot distinguish two identical objects in the same image sector.
 */
internal class ForegroundObjectAnnouncementTracker(
    private val requiredObservations: Int = 2,
    private val reannounceAfterAbsenceMs: Long = 5_000L,
    private val maxObservationGapMs: Long = 1_500L,
    private val maxMemoryMs: Long = 60_000L
) {
    private var candidateKey: String? = null
    private var observationCount = 0
    private var lastCandidateObservationAtMs = 0L
    private val lastSeenAtMs = mutableMapOf<String, Long>()
    private val announcedKeys = mutableSetOf<String>()

    init {
        require(requiredObservations >= 1)
        require(reannounceAfterAbsenceMs > 0L)
        require(maxObservationGapMs > 0L)
        require(maxMemoryMs >= reannounceAfterAbsenceMs)
    }

    fun observe(candidate: ForegroundObject?, nowMs: Long): ForegroundObject? {
        require(nowMs >= 0L)

        if (candidate == null) {
            candidateKey = null
            observationCount = 0
            pruneExpired(nowMs)
            return null
        }

        val key = key(candidate.label)
        val previousSeenAt = lastSeenAtMs[key]
        if (previousSeenAt != null && nowMs - previousSeenAt >= reannounceAfterAbsenceMs) {
            announcedKeys.remove(key)
        }
        lastSeenAtMs[key] = nowMs
        pruneExpired(nowMs)

        if (
            key == candidateKey &&
            nowMs >= lastCandidateObservationAtMs &&
            nowMs - lastCandidateObservationAtMs <= maxObservationGapMs
        ) {
            observationCount += 1
        } else {
            candidateKey = key
            observationCount = 1
        }
        lastCandidateObservationAtMs = nowMs

        return candidate.takeIf {
            observationCount >= requiredObservations && key !in announcedKeys
        }
    }

    fun markAnnounced(label: String, nowMs: Long) {
        require(nowMs >= 0L)
        val key = key(label)
        announcedKeys += key
        lastSeenAtMs[key] = nowMs
    }

    fun reset() {
        candidateKey = null
        observationCount = 0
        lastCandidateObservationAtMs = 0L
        lastSeenAtMs.clear()
        announcedKeys.clear()
    }

    private fun pruneExpired(nowMs: Long) {
        val expired = lastSeenAtMs.filterValues {
            nowMs >= it && nowMs - it > maxMemoryMs
        }.keys
        expired.forEach { key ->
            lastSeenAtMs.remove(key)
            announcedKeys.remove(key)
        }
    }

    private fun key(label: String) = label.trim().lowercase()
}
