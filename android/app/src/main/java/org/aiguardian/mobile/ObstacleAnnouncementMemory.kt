package org.aiguardian.mobile

/**
 * Remembers obstacle announcements by detector label and image direction.
 *
 * Continuous detections do not repeat. A key is re-armed after it has been absent
 * long enough to represent a new visual event. A higher severity or a material
 * reduction in estimated distance may be announced as a meaningful update.
 *
 * Because the detector does not provide persistent object IDs, this policy cannot
 * distinguish identical objects occupying the same direction at the same time.
 */
internal class ObstacleAnnouncementMemory(
    private val reannounceAfterAbsenceMs: Long = 5_000L,
    private val escalationCooldownMs: Long = 1_500L,
    private val maxMemoryMs: Long = 60_000L,
    private val closerDistanceRatio: Float = 0.70f
) {
    private data class Entry(
        var lastSeenAtMs: Long,
        var lastAnnouncedAtMs: Long? = null,
        var highestAnnouncedSeverity: Int = -1,
        var lastAnnouncedDistanceCenterMeters: Float? = null,
        var lastAnnouncedMinimumSteps: Int? = null
    )

    private val entries = mutableMapOf<String, Entry>()

    init {
        require(reannounceAfterAbsenceMs > 0L)
        require(escalationCooldownMs >= 0L)
        require(maxMemoryMs >= reannounceAfterAbsenceMs)
        require(closerDistanceRatio in 0.1f..0.95f)
    }

    /**
     * Call once per detector frame before querying announcement eligibility.
     * A sufficiently long absence re-arms the key as a new visual event.
     */
    fun updateSeen(currentKeys: Set<String>, nowMs: Long) {
        require(nowMs >= 0L)

        val expiredKeys = entries.filterValues {
            nowMs >= it.lastSeenAtMs && nowMs - it.lastSeenAtMs > maxMemoryMs
        }.keys
        expiredKeys.forEach(entries::remove)

        currentKeys.forEach { key ->
            val previous = entries[key]
            if (
                previous == null ||
                (nowMs >= previous.lastSeenAtMs &&
                    nowMs - previous.lastSeenAtMs >= reannounceAfterAbsenceMs)
            ) {
                entries[key] = Entry(lastSeenAtMs = nowMs)
            } else {
                previous.lastSeenAtMs = nowMs
            }
        }
    }

    /**
     * Severity: 0 = routine, 1 = approaching/close, 2 = very close-looking.
     * Distance updates are eligible only when the estimated range moves materially
     * closer or the lower step bound drops by at least two steps.
     */
    fun shouldAnnounce(
        key: String,
        severity: Int,
        nowMs: Long,
        estimate: VisualDistanceEstimate? = null
    ): Boolean {
        require(severity in 0..2)
        require(nowMs >= 0L)

        val entry = entries[key] ?: return true
        val lastAnnouncedAt = entry.lastAnnouncedAtMs ?: return true
        if (nowMs < lastAnnouncedAt || nowMs - lastAnnouncedAt < escalationCooldownMs) {
            return false
        }

        val currentCenter = estimate?.distanceCenterMeters()
        val materiallyCloser = currentCenter != null &&
            entry.lastAnnouncedDistanceCenterMeters?.let {
                currentCenter <= it * closerDistanceRatio
            } == true
        val stepsReduced = estimate?.minimumSteps != null &&
            entry.lastAnnouncedMinimumSteps?.let {
                it - estimate.minimumSteps >= 2
            } == true

        return severity > entry.highestAnnouncedSeverity ||
            materiallyCloser ||
            stepsReduced
    }

    fun markAnnounced(
        key: String,
        severity: Int,
        nowMs: Long,
        estimate: VisualDistanceEstimate? = null
    ) {
        require(key.isNotBlank())
        require(severity in 0..2)
        require(nowMs >= 0L)

        val entry = entries.getOrPut(key) { Entry(lastSeenAtMs = nowMs) }
        entry.lastSeenAtMs = nowMs
        entry.lastAnnouncedAtMs = nowMs
        entry.highestAnnouncedSeverity = maxOf(entry.highestAnnouncedSeverity, severity)
        estimate?.distanceCenterMeters()?.let {
            entry.lastAnnouncedDistanceCenterMeters = it
        }
        estimate?.minimumSteps?.let {
            entry.lastAnnouncedMinimumSteps = it
        }
    }

    fun reset() {
        entries.clear()
    }

    private fun VisualDistanceEstimate.distanceCenterMeters(): Float? {
        val minimum = minimumMeters ?: return null
        val maximum = maximumMeters ?: return null
        if (!minimum.isFinite() || !maximum.isFinite() || minimum <= 0f || maximum < minimum) {
            return null
        }
        return (minimum + maximum) / 2f
    }
}
