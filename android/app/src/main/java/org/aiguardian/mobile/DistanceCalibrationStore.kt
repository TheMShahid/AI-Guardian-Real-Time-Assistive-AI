package org.aiguardian.mobile

import android.content.Context

/**
 * Stores only the user's local camera calibration and stride preference.
 * No camera images or personal audio are persisted by this store.
 */
object DistanceCalibrationStore {
    private const val PREFS = "guardian_distance_calibration"
    private const val KEY_FOCAL_RATIO = "focal_ratio"
    private const val KEY_REFERENCE_LABEL = "reference_label"
    private const val KEY_REFERENCE_DISTANCE = "reference_distance"
    private const val KEY_REFERENCE_HEIGHT = "reference_height"
    private const val KEY_CALIBRATED_AT = "calibrated_at"
    private const val KEY_STRIDE_LENGTH = "stride_length"

    fun loadCalibration(context: Context): DistanceCalibration? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_FOCAL_RATIO)) return null
        return try {
            DistanceCalibration(
                focalRatio = prefs.getFloat(KEY_FOCAL_RATIO, Float.NaN),
                referenceLabel = prefs.getString(KEY_REFERENCE_LABEL, null) ?: return null,
                referenceDistanceMeters = prefs.getFloat(KEY_REFERENCE_DISTANCE, Float.NaN),
                referenceHeightMeters = prefs.getFloat(KEY_REFERENCE_HEIGHT, Float.NaN),
                calibratedAtEpochMs = prefs.getLong(KEY_CALIBRATED_AT, -1L)
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun saveCalibration(context: Context, calibration: DistanceCalibration) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_FOCAL_RATIO, calibration.focalRatio)
            .putString(KEY_REFERENCE_LABEL, calibration.referenceLabel)
            .putFloat(KEY_REFERENCE_DISTANCE, calibration.referenceDistanceMeters)
            .putFloat(KEY_REFERENCE_HEIGHT, calibration.referenceHeightMeters)
            .putLong(KEY_CALIBRATED_AT, calibration.calibratedAtEpochMs)
            .apply()
    }

    fun clearCalibration(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_FOCAL_RATIO)
            .remove(KEY_REFERENCE_LABEL)
            .remove(KEY_REFERENCE_DISTANCE)
            .remove(KEY_REFERENCE_HEIGHT)
            .remove(KEY_CALIBRATED_AT)
            .apply()
    }

    fun loadStrideLength(context: Context): Float? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_STRIDE_LENGTH, Float.NaN)
        return value.takeIf { it.isFinite() && it in 0.20f..1.50f }
    }

    fun saveStrideLength(context: Context, strideLengthMeters: Float?) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (strideLengthMeters == null) {
            editor.remove(KEY_STRIDE_LENGTH)
        } else {
            require(strideLengthMeters.isFinite() && strideLengthMeters in 0.20f..1.50f)
            editor.putFloat(KEY_STRIDE_LENGTH, strideLengthMeters)
        }
        editor.apply()
    }
}
