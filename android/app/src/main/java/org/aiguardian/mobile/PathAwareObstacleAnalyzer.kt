package org.aiguardian.mobile

/**
 * Ranks detected objects that may obstruct the forward walking corridor.
 *
 * Apparent size is only a rough visual cue. It is not calibrated distance,
 * depth measurement, or a guarantee that a path is clear.
 */
data class PrioritizedObstacle(
    val detection: Detection,
    val direction: String,
    val relevanceScore: Float,
    val apparentProximity: String
)

object PathAwareObstacleAnalyzer {

    private const val CORRIDOR_LEFT = 0.22f
    private const val CORRIDOR_RIGHT = 0.78f
    private const val MIN_CORRIDOR_OVERLAP = 0.15f
    private const val MIN_BOTTOM_POSITION = 0.48f
    private const val MAX_OBSTACLES = 3

    private val obstacleClasses = setOf(
        "person", "bicycle", "car", "motorcycle", "bus", "train", "truck",
        "bench", "suitcase", "backpack", "chair", "couch", "bed",
        "dining table", "toilet", "potted plant"
    )

    private val openSpaceRelevantClasses = obstacleClasses + setOf(
        "dog", "cat", "horse", "traffic light", "fire hydrant",
        "stop sign", "parking meter", "umbrella"
    )

    fun analyze(detections: List<Detection>): List<PrioritizedObstacle> {
        return detections.mapNotNull { detection ->
            if (detection.label !in obstacleClasses) return@mapNotNull null

            val frameWidth = detection.frameWidth
            val frameHeight = detection.frameHeight
            if (frameWidth <= 0 || frameHeight <= 0) return@mapNotNull null

            val box = detection.boundingBox
            if (
                !box.left.isFinite() || !box.right.isFinite() ||
                !box.top.isFinite() || !box.bottom.isFinite() ||
                box.width() <= 0f || box.height() <= 0f
            ) return@mapNotNull null

            val left = (box.left / frameWidth).coerceIn(0f, 1f)
            val right = (box.right / frameWidth).coerceIn(0f, 1f)
            val bottom = (box.bottom / frameHeight).coerceIn(0f, 1f)
            val centerX = (left + right) / 2f

            val overlapWidth = (
                minOf(right, CORRIDOR_RIGHT) -
                    maxOf(left, CORRIDOR_LEFT)
                ).coerceAtLeast(0f)
            val boxWidth = right - left
            if (boxWidth <= 0f) return@mapNotNull null

            val overlapRatio = overlapWidth / boxWidth
            if (
                overlapRatio < MIN_CORRIDOR_OVERLAP ||
                bottom < MIN_BOTTOM_POSITION
            ) return@mapNotNull null

            val direction = when {
                centerX < 0.40f -> "front-left"
                centerX > 0.60f -> "front-right"
                else -> "front-center"
            }

            val normalizedHeight = (box.height() / frameHeight).coerceIn(0f, 1f)
            val apparentProximity = when {
                normalizedHeight >= 0.55f -> "very-close-looking"
                normalizedHeight >= 0.30f -> "close-looking"
                else -> "farther-looking"
            }

            // This ranks likely visual obstruction, not physical distance.
            val relevanceScore =
                overlapRatio * 0.55f +
                bottom * 0.25f +
                (normalizedHeight.coerceAtMost(0.60f) / 0.60f) * 0.20f

            PrioritizedObstacle(
                detection = detection,
                direction = direction,
                relevanceScore = relevanceScore,
                apparentProximity = apparentProximity
            )
        }
            .sortedByDescending { it.relevanceScore }
            .distinctBy { "${it.detection.classId}|${it.direction}" }
            .take(MAX_OBSTACLES)
    }

    /**
     * Reports sectors with no recognized, relevant object detection.
     * This is NOT proof that a sector is empty or safe to walk through.
     */
    fun sectorsWithoutRecognizedObstacles(
        detections: List<Detection>
    ): List<String> {
        val occupied = mutableSetOf<String>()

        detections
            .filter { it.label in openSpaceRelevantClasses }
            .forEach { detection ->
                val width = detection.frameWidth
                val height = detection.frameHeight
                val box = detection.boundingBox
                if (width <= 0 || height <= 0 || box.width() <= 0f || box.height() <= 0f) {
                    return@forEach
                }
                if (box.bottom / height < 0.40f) return@forEach

                val left = (box.left / width).coerceIn(0f, 1f)
                val right = (box.right / width).coerceIn(0f, 1f)
                if (left < 0.38f) occupied.add("left")
                if (right > 0.62f) occupied.add("right")
                if (right > 0.38f && left < 0.62f) occupied.add("ahead")
            }

        return listOf("ahead", "left", "right").filterNot { it in occupied }
    }
}
