package org.aiguardian.mobile

/**
 * Debounces sectors in which YOLO has not detected a configured obstacle class.
 *
 * This is detector-coverage feedback only. It does not estimate free space,
 * floor traversability, depth, or route safety.
 */
internal class UnobservedSectorAnnouncementTracker(
    private val requiredObservations: Int = 4,
    private val maxObservationGapMs: Long = 1_500L,
    private val repeatIntervalMs: Long = 15_000L
) {
    private data class Observation(var count: Int, var lastSeenAtMs: Long)

    private val observations = mutableMapOf<String, Observation>()
    private val lastAnnouncedAtMs = mutableMapOf<String, Long>()
    private var lastFrameId = Long.MIN_VALUE

    init {
        require(requiredObservations >= 1)
        require(maxObservationGapMs > 0L)
        require(repeatIntervalMs > 0L)
    }

    fun observe(candidateSectors: Set<String>, frameId: Long, nowMs: Long): String? {
        require(nowMs >= 0L)
        if (frameId == lastFrameId) return null
        lastFrameId = frameId

        val validCandidates = candidateSectors.intersect(VALID_SECTORS)
        observations.keys.toList().forEach { sector ->
            if (sector !in validCandidates) observations.remove(sector)
        }

        validCandidates.forEach { sector ->
            val previous = observations[sector]
            if (previous == null || nowMs < previous.lastSeenAtMs ||
                nowMs - previous.lastSeenAtMs > maxObservationGapMs
            ) {
                observations[sector] = Observation(1, nowMs)
            } else {
                previous.count += 1
                previous.lastSeenAtMs = nowMs
            }
        }

        return SECTOR_PRIORITY.firstOrNull { sector ->
            val observation = observations[sector] ?: return@firstOrNull false
            if (observation.count < requiredObservations) return@firstOrNull false
            val last = lastAnnouncedAtMs[sector] ?: return@firstOrNull true
            nowMs >= last && nowMs - last >= repeatIntervalMs
        }
    }

    fun markAnnounced(sector: String, nowMs: Long) {
        require(nowMs >= 0L)
        if (sector in VALID_SECTORS) lastAnnouncedAtMs[sector] = nowMs
    }

    fun reset() {
        observations.clear()
        lastAnnouncedAtMs.clear()
        lastFrameId = Long.MIN_VALUE
    }

    private companion object {
        val VALID_SECTORS = setOf("ahead", "left", "right")
        val SECTOR_PRIORITY = listOf("ahead", "left", "right")
    }
}
