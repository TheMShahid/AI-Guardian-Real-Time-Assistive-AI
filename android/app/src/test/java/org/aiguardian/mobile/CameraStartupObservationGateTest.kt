package org.aiguardian.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStartupObservationGateTest {

    @Test
    fun waitsForMinimumWarmupAndRequiredDistinctFrames() {
        val gate = CameraStartupObservationGate(requiredFrames = 5, minimumWarmupMs = 1_500L)
        gate.reset(100L)

        assertFalse(gate.observe(frameId = 1L, nowMs = 100L))
        assertFalse(gate.observe(frameId = 2L, nowMs = 400L))
        assertFalse(gate.observe(frameId = 3L, nowMs = 800L))
        assertFalse(gate.observe(frameId = 4L, nowMs = 1_200L))
        assertTrue(gate.observe(frameId = 5L, nowMs = 1_600L))
    }

    @Test
    fun sameFrameCannotSatisfyObservationCount() {
        val gate = CameraStartupObservationGate(requiredFrames = 3, minimumWarmupMs = 500L)
        gate.reset(0L)

        assertFalse(gate.observe(frameId = 10L, nowMs = 0L))
        assertFalse(gate.observe(frameId = 10L, nowMs = 600L))
        assertFalse(gate.observe(frameId = 10L, nowMs = 900L))
        assertFalse(gate.observe(frameId = 11L, nowMs = 900L))
        assertTrue(gate.observe(frameId = 12L, nowMs = 900L))
    }

    @Test
    fun resetStartsANewWarmupPeriod() {
        val gate = CameraStartupObservationGate(requiredFrames = 2, minimumWarmupMs = 500L)
        gate.reset(0L)
        assertFalse(gate.observe(frameId = 1L, nowMs = 0L))
        assertTrue(gate.observe(frameId = 2L, nowMs = 500L))

        gate.reset(1_000L)
        assertFalse(gate.observe(frameId = 3L, nowMs = 1_500L))
        assertTrue(gate.observe(frameId = 4L, nowMs = 1_500L))
    }
}
