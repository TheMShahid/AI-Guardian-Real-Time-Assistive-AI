package org.aiguardian.mobile

import kotlin.math.sqrt

/**
 * Selects one prominent, confidently detected object for general scene narration.
 *
 * The normalized box geometry is retained so the announcement tracker can detect
 * a meaningful change of visual focus, including a different object of the same class.
 */
internal data class ForegroundObject(
    val label: String,
    val direction: String,
    val score: Float,
    val normalizedBoxHeight: Float = 0f,
    val centerXRatio: Float = 0.5f,
    val centerYRatio: Float = 0.5f,
    val normalizedBoxWidth: Float = 0f
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
                score = score,
                normalizedBoxHeight = ((box.bottom - box.top) / height.toFloat())
                    .coerceIn(0f, 1f),
                centerXRatio = centerX,
                centerYRatio = centerY,
                normalizedBoxWidth = ((box.right - box.left) / width.toFloat())
                    .coerceIn(0f, 1f)
            )
        }.maxByOrNull { it.score }
    }
}

/**
 * Requires consecutive observations and suppresses repeat narration for the same
 * visual object. A new object of the same class becomes eligible when its normalized
 * position/scale differs materially from the last announced object.
 *
 * This is a visual-focus heuristic, not persistent physical object tracking.
 */
internal class ForegroundObjectAnnouncementTracker(
    private val requiredObservations: Int = 2,
    private val reannounceAfterAbsenceMs: Long = 5_000L,
    private val maxObservationGapMs: Long = 1_500L,
    private val maxMemoryMs: Long = 60_000L,
    private val maxFocusShiftRatio: Float = 0.22f,
    private val maxScaleRatio: Float = 1.8f
) {
    private var candidateObject: ForegroundObject? = null
    private var observationCount = 0
    private var lastCandidateObservationAtMs = 0L
    private val lastSeenAtMs = mutableMapOf<String, Long>()
    private val lastAnnouncedObjects = mutableMapOf<String, ForegroundObject>()

    init {
        require(requiredObservations >= 1)
        require(reannounceAfterAbsenceMs > 0L)
        require(maxObservationGapMs > 0L)
        require(maxMemoryMs >= reannounceAfterAbsenceMs)
        require(maxFocusShiftRatio in 0.05f..0.75f)
        require(maxScaleRatio >= 1f)
    }

    fun observe(candidate: ForegroundObject?, nowMs: Long): ForegroundObject? {
        require(nowMs >= 0L)

        if (candidate == null) {
            candidateObject = null
            observationCount = 0
            pruneExpired(nowMs)
            return null
        }

        val key = key(candidate.label)
        val previousSeenAt = lastSeenAtMs[key]
        if (
            previousSeenAt != null &&
            nowMs >= previousSeenAt &&
            nowMs - previousSeenAt >= reannounceAfterAbsenceMs
        ) {
            lastAnnouncedObjects.remove(key)
        }
        lastSeenAtMs[key] = nowMs
        pruneExpired(nowMs)

        val previousCandidate = candidateObject
        if (
            previousCandidate != null &&
            previousCandidate.label == candidate.label &&
            nowMs >= lastCandidateObservationAtMs &&
            nowMs - lastCandidateObservationAtMs <= maxObservationGapMs &&
            sameVisualFocus(previousCandidate, candidate)
        ) {
            observationCount += 1
        } else {
            observationCount = 1
        }
        candidateObject = candidate
        lastCandidateObservationAtMs = nowMs

        val lastAnnounced = lastAnnouncedObjects[key]
        val isNewVisualFocus = lastAnnounced == null ||
            !sameVisualFocus(lastAnnounced, candidate)

        return candidate.takeIf {
            observationCount >= requiredObservations && isNewVisualFocus
        }
    }

    fun markAnnounced(candidate: ForegroundObject, nowMs: Long) {
        require(nowMs >= 0L)
        val key = key(candidate.label)
        lastAnnouncedObjects[key] = candidate
        lastSeenAtMs[key] = nowMs
    }

    fun reset() {
        candidateObject = null
        observationCount = 0
        lastCandidateObservationAtMs = 0L
        lastSeenAtMs.clear()
        lastAnnouncedObjects.clear()
    }

    private fun sameVisualFocus(first: ForegroundObject, second: ForegroundObject): Boolean {
        val dx = first.centerXRatio - second.centerXRatio
        val dy = first.centerYRatio - second.centerYRatio
        val centreDistance = sqrt(dx * dx + dy * dy)
        if (centreDistance > maxFocusShiftRatio) return false

        val firstHeight = first.normalizedBoxHeight
        val secondHeight = second.normalizedBoxHeight
        if (firstHeight <= 0f || secondHeight <= 0f) return true

        val heightRatio = maxOf(firstHeight, secondHeight) / minOf(firstHeight, secondHeight)
        if (heightRatio > maxScaleRatio) return false

        val firstWidth = first.normalizedBoxWidth
        val secondWidth = second.normalizedBoxWidth
        if (firstWidth > 0f && secondWidth > 0f) {
            val widthRatio = maxOf(firstWidth, secondWidth) / minOf(firstWidth, secondWidth)
            if (widthRatio > maxScaleRatio) return false
        }
        return true
    }

    private fun pruneExpired(nowMs: Long) {
        val expired = lastSeenAtMs.filterValues {
            nowMs >= it && nowMs - it > maxMemoryMs
        }.keys
        expired.forEach { key ->
            lastSeenAtMs.remove(key)
            lastAnnouncedObjects.remove(key)
        }
    }

    private fun key(label: String) = label.trim().lowercase()
}
