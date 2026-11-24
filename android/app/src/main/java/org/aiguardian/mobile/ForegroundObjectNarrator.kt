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
            val score = detection.confidence * 0.45f +
                centerScore * 0.15f +
                sizeScore * 0.40f

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
 * Requires repeated observations before narration and avoids repeating the same
 * continuously visible class. A caller marks an item only after TTS accepts it.
 */
internal class ForegroundObjectAnnouncementTracker(
    private val requiredObservations: Int = 2
) {
    private var candidateLabel: String? = null
    private var observationCount = 0
    private var announcedLabel: String? = null

    init {
        require(requiredObservations >= 1)
    }

    fun observe(candidate: ForegroundObject?): ForegroundObject? {
        if (candidate == null) {
            candidateLabel = null
            observationCount = 0
            return null
        }

        if (candidate.label == candidateLabel) {
            observationCount += 1
        } else {
            candidateLabel = candidate.label
            observationCount = 1
        }

        return candidate.takeIf {
            observationCount >= requiredObservations &&
                candidate.label != announcedLabel
        }
    }

    fun markAnnounced(label: String) {
        announcedLabel = label.trim().lowercase()
    }

    fun reset() {
        candidateLabel = null
        observationCount = 0
        announcedLabel = null
    }
}
