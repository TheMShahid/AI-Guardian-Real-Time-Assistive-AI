package org.aiguardian.mobile

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import java.util.Locale

/**
 * Read-only diagnostics for selecting and validating on-device depth providers.
 * No camera is opened and no image or sensor stream is started.
 */
object DeviceCapabilityInspector {

    fun inspect(context: Context): String {
        val appContext = context.applicationContext
        val output = StringBuilder()

        output.appendLine("AI Guardian device capability report")
        output.appendLine("manufacturer=${Build.MANUFACTURER}")
        output.appendLine("model=${Build.MODEL}")
        output.appendLine("device=${Build.DEVICE}")
        output.appendLine("android_sdk=${Build.VERSION.SDK_INT}")
        output.appendLine("supported_abis=${Build.SUPPORTED_ABIS.joinToString()}")
        output.appendLine("")

        appendCameraCharacteristics(appContext, output)
        appendSensors(appContext, output)

        return output.toString()
    }

    private fun appendCameraCharacteristics(
        context: Context,
        output: StringBuilder
    ) {
        output.appendLine("[CAMERAS]")
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (manager == null) {
            output.appendLine("camera_manager=unavailable")
            return
        }

        try {
            val cameraIds = manager.cameraIdList
            output.appendLine("camera_count=${cameraIds.size}")
            cameraIds.forEach { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                val facing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                    CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                    else -> "UNKNOWN"
                }
                val focalLengths = characteristics
                    .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.joinToString { format(it) } ?: "unavailable"
                val physicalSize = characteristics
                    .get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    ?.let { "${format(it.width)}x${format(it.height)}mm" }
                    ?: "unavailable"
                val pixelArray = characteristics
                    .get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                    ?.let { "${it.width}x${it.height}" } ?: "unavailable"
                val activeArray = characteristics
                    .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    ?.let { "${it.width()}x${it.height()}" } ?: "unavailable"
                val hardwareLevel = characteristics
                    .get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    ?.let(::hardwareLevelName) ?: "unavailable"
                val capabilities = characteristics
                    .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.joinToString { capabilityName(it) } ?: "unavailable"

                output.appendLine(
                    "camera_id=$id facing=$facing focal_lengths_mm=$focalLengths " +
                        "sensor_size_mm=$physicalSize pixel_array=$pixelArray " +
                        "active_array=$activeArray hardware_level=$hardwareLevel " +
                        "capabilities=$capabilities"
                )
            }
        } catch (error: Exception) {
            output.appendLine("camera_error=${error.javaClass.simpleName}:${error.message}")
        }
    }

    private fun appendSensors(context: Context, output: StringBuilder) {
        output.appendLine("")
        output.appendLine("[SENSORS]")
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (manager == null) {
            output.appendLine("sensor_manager=unavailable")
            return
        }

        val sensors = manager.getSensorList(Sensor.TYPE_ALL)
        output.appendLine("sensor_count=${sensors.size}")
        sensors.forEach { sensor ->
            output.appendLine(
                "sensor name=${sensor.name} type=${sensor.type} " +
                    "vendor=${sensor.vendor} wake_up=${sensor.isWakeUpSensor}"
            )
        }

        val hasDepth = sensors.any {
            it.type == Sensor.TYPE_DEPTH
        }
        output.appendLine("depth_or_dynamic_sensor_present=$hasDepth")
    }

    private fun hardwareLevelName(level: Int): String = when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN($level)"
    }

    private fun capabilityName(value: Int): String = when (value) {
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BACKWARD_COMPATIBLE"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "DEPTH_OUTPUT"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI_CAMERA"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "MONOCHROME"
        else -> "CAPABILITY_$value"
    }

    private fun format(value: Float): String =
        String.format(Locale.US, "%.4f", value)
}
