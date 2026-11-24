package org.aiguardian.mobile

/**
 * Calculates a user's average stride from a measured, safe walking trial.
 * The result is intentionally rejected outside the supported physiological
 * input range; it must not be replaced with a generic assumed stride.
 */
object StrideLengthCalculator {
    fun calculate(walkedDistanceMeters: Float, stepCount: Int): Float? {
        if (
            !walkedDistanceMeters.isFinite() ||
            walkedDistanceMeters !in 1.0f..100.0f ||
            stepCount !in 1..1_000
        ) return null

        val stride = walkedDistanceMeters / stepCount
        return stride.takeIf { it.isFinite() && it in 0.20f..1.50f }
    }
}
