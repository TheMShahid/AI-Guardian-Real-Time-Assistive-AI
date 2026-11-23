package org.aiguardian.mobile

/**
 * Selects YOLO detections that may obstruct the forward walking corridor.
 *
 * This is a 2D visual relevance heuristic, not a distance estimator
 * or a guarantee that a path is clear.
 */
data class PrioritizedObstacle(
    val detection: Detection,
    val direction: String,
    val relevanceScore: Float
)

object PathAwareObstacleAnalyzer {

    private const val CORRIDOR_LEFT = 0.22f
    private const val CORRIDOR_RIGHT = 0.78f

    private const val MIN_CORRIDOR_OVERLAP = 0.15f
    private const val MIN_BOTTOM_POSITION = 0.55f
    private const val MAX_OBSTACLES = 3

    private val obstacleClasses = setOf(
        "person",
        "bicycle",
        "car",
        "motorcycle",
        "bus",
        "train",
        "truck",
        "bench",
        "suitcase",
        "backpack",
        "chair",
        "couch",
        "bed",
        "dining table",
        "toilet",
        "potted plant"
    )

    fun analyze(
        detections: List<Detection>
    ): List<PrioritizedObstacle> {

        return detections.mapNotNull { detection ->

            if (detection.label !in obstacleClasses) {
                return@mapNotNull null
            }

            val frameWidth = detection.frameWidth
            val frameHeight = detection.frameHeight

            if (frameWidth <= 0 || frameHeight <= 0) {
                return@mapNotNull null
            }

            val box = detection.boundingBox

            if (
                !box.left.isFinite() ||
                !box.right.isFinite() ||
                !box.top.isFinite() ||
                !box.bottom.isFinite() ||
                box.width() <= 0f ||
                box.height() <= 0f
            ) {
                return@mapNotNull null
            }

            val normalizedLeft =
                (box.left / frameWidth).coerceIn(0f, 1f)

            val normalizedRight =
                (box.right / frameWidth).coerceIn(0f, 1f)

            val normalizedBottom =
                (box.bottom / frameHeight).coerceIn(0f, 1f)

            val normalizedCenterX =
                (normalizedLeft + normalizedRight) / 2f

            val overlapLeft =
                maxOf(normalizedLeft, CORRIDOR_LEFT)

            val overlapRight =
                minOf(normalizedRight, CORRIDOR_RIGHT)

            val overlapWidth =
                (overlapRight - overlapLeft).coerceAtLeast(0f)

            val boxWidth =
                normalizedRight - normalizedLeft

            if (boxWidth <= 0f) {
                return@mapNotNull null
            }

            val overlapRatio = overlapWidth / boxWidth

            if (
                overlapRatio < MIN_CORRIDOR_OVERLAP ||
                normalizedBottom < MIN_BOTTOM_POSITION
            ) {
                return@mapNotNull null
            }

            val direction = when {
                normalizedCenterX < 0.40f -> "front-left"
                normalizedCenterX > 0.60f -> "front-right"
                else -> "front-center"
            }

            // This score ranks visual relevance, not physical distance.
            val normalizedHeight =
                (box.height() / frameHeight).coerceIn(0f, 1f)

            val relevanceScore =
                (overlapRatio * 0.55f) +
                (normalizedBottom * 0.25f) +
                (normalizedHeight.coerceAtMost(0.60f) / 0.60f * 0.20f)

            PrioritizedObstacle(
                detection = detection,
                direction = direction,
                relevanceScore = relevanceScore
            )

        }
            .sortedByDescending { it.relevanceScore }
            .distinctBy {
                "${it.detection.classId}|${it.direction}"
            }
            .take(MAX_OBSTACLES)
    }
}
