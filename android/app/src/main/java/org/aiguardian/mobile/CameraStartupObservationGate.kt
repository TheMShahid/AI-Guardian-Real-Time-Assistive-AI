package org.aiguardian.mobile

/**
 * Holds live narration until the camera has produced enough distinct observations
 * over a minimum warm-up interval. This prevents startup-frame flicker from being
 * spoken as a reliable scene description.
 */
internal class CameraStartupObservationGate(
    private val requiredFrames: Int = 5,
    private val minimumWarmupMs: Long = 1_500L
) {
    private var startedAtMs: Long? = null
    private var lastFrameId: Long? = null
    private var observedFrames = 0

    init {
        require(requiredFrames >= 1)
        require(minimumWarmupMs >= 0L)
    }

    fun reset(nowMs: Long) {
        require(nowMs >= 0L)
        startedAtMs = nowMs
        lastFrameId = null
        observedFrames = 0
    }

    fun observe(frameId: Long, nowMs: Long): Boolean {
        require(nowMs >= 0L)
        val started = startedAtMs ?: nowMs.also { reset(it) }

        if (frameId != lastFrameId) {
            observedFrames += 1
            lastFrameId = frameId
        }

        return observedFrames >= requiredFrames &&
            nowMs >= started &&
            nowMs - started >= minimumWarmupMs
    }

    fun isReady(): Boolean {
        val started = startedAtMs ?: return false
        return observedFrames >= requiredFrames && started >= 0L
    }
}
