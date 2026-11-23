package org.aiguardian.mobile

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Converts a calibrated distance interval into cautious spoken wording.
 * It never formats unavailable estimates as a distance or step count.
 */
object DistanceSpeechFormatter {
    fun format(estimate: VisualDistanceEstimate, language: String): String? {
        if (estimate.quality != DistanceQuality.CALIBRATED_APPROXIMATE ||
            estimate.minimumMeters == null || estimate.maximumMeters == null
        ) return null

        val minMeters = estimate.minimumMeters
        val maxMeters = estimate.maximumMeters
        if (!minMeters.isFinite() || !maxMeters.isFinite() || minMeters <= 0f) return null

        val minSteps = estimate.minimumSteps
        val maxSteps = estimate.maximumSteps
        val stepText = if (minSteps != null && maxSteps != null &&
            minSteps > 0 && maxSteps >= minSteps
        ) {
            when (language) {
                "Urdu (Pakistan)" -> "تقریباً $minSteps سے $maxSteps قدم"
                "Pashto (Afghanistan)" -> "شاوخوا له $minSteps تر $maxSteps ګامونو"
                else -> "approximately $minSteps to $maxSteps steps"
            }
        } else null

        val meterText = when (language) {
            "Urdu (Pakistan)" ->
                "تقریباً ${formatMeters(minMeters)} سے ${formatMeters(maxMeters)} میٹر"
            "Pashto (Afghanistan)" ->
                "شاوخوا ${formatMeters(minMeters)} تر ${formatMeters(maxMeters)} مترو"
            else ->
                "approximately ${formatMeters(minMeters)} to ${formatMeters(maxMeters)} metres"
        }

        return when (language) {
            "Urdu (Pakistan)" -> if (stepText != null) {
                "$meterText، یعنی $stepText"
            } else meterText
            "Pashto (Afghanistan)" -> if (stepText != null) {
                "$meterText، یعنې $stepText"
            } else meterText
            else -> if (stepText != null) {
                "$meterText, or $stepText"
            } else meterText
        }
    }

    private fun formatMeters(value: Float): String =
        String.format(Locale.US, "%.1f", (value * 10f).roundToInt() / 10f)
}
