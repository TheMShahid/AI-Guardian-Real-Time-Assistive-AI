package org.aiguardian.mobile

/**
 * Debounces empty detector observations and rate-limits neutral scene feedback.
 *
 * An empty detector frame is not proof that a route is clear. This tracker only
 * authorizes wording that says no supported object was detected in the current view.
 */
internal class EmptySceneAnnouncementTracker(
    private val requiredEmptyFrames: Int = 4,
    private val maxObservationGapMs: Long = 1_500L,
    private val repeatIntervalMs: Long = 15_000L
) {
    private var lastFrameId: Long = Long.MIN_VALUE
    private var emptyFrameCount = 0
    private var lastObservationAtMs: Long? = null
    private var lastAnnouncementAtMs: Long? = null

    init {
        require(requiredEmptyFrames >= 1)
        require(maxObservationGapMs > 0L)
        require(repeatIntervalMs > 0L)
    }

    fun observe(hasDetections: Boolean, frameId: Long, nowMs: Long) {
        require(nowMs >= 0L)
        if (frameId == lastFrameId) return
        lastFrameId = frameId

        if (hasDetections) {
            emptyFrameCount = 0
            lastObservationAtMs = nowMs
            lastAnnouncementAtMs = null
            return
        }

        val previousAt = lastObservationAtMs
        if (previousAt == null || nowMs < previousAt ||
            nowMs - previousAt > maxObservationGapMs
        ) {
            emptyFrameCount = 0
            lastAnnouncementAtMs = null
        }

        emptyFrameCount += 1
        lastObservationAtMs = nowMs
    }

    fun shouldAnnounce(nowMs: Long): Boolean {
        require(nowMs >= 0L)
        if (emptyFrameCount < requiredEmptyFrames) return false
        val last = lastAnnouncementAtMs ?: return true
        return nowMs >= last && nowMs - last >= repeatIntervalMs
    }

    fun markAnnounced(nowMs: Long) {
        require(nowMs >= 0L)
        if (emptyFrameCount >= requiredEmptyFrames) {
            lastAnnouncementAtMs = nowMs
        }
    }

    fun reset() {
        lastFrameId = Long.MIN_VALUE
        emptyFrameCount = 0
        lastObservationAtMs = null
        lastAnnouncementAtMs = null
    }
}
