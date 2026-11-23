
package org.aiguardian.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Detection(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val frameWidth: Int,
    val frameHeight: Int
)

class YoloDetector(context: Context) : AutoCloseable {

    companion object {
        private const val MODEL_NAME = "yolov8n.onnx"
        private const val INPUT_SIZE = 416
        private const val OUTPUT_CHANNELS = 84
        private const val CANDIDATES = 3549

        private const val CONFIDENCE_THRESHOLD = 0.35f
        private const val NMS_IOU_THRESHOLD = 0.45f
        private const val MAX_DETECTIONS = 50

        private val COCO_CLASSES = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane",
            "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench",
            "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite",
            "baseball bat", "baseball glove", "skateboard",
            "surfboard", "tennis racket", "bottle", "wine glass",
            "cup", "fork", "knife", "spoon", "bowl", "banana",
            "apple", "sandwich", "orange", "broccoli", "carrot",
            "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv",
            "laptop", "mouse", "remote", "keyboard", "cell phone",
            "microwave", "oven", "toaster", "sink", "refrigerator",
            "book", "clock", "vase", "scissors", "teddy bear",
            "hair drier", "toothbrush"
        )
    }

    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelBytes = context.assets
            .open(MODEL_NAME)
            .use { it.readBytes() }

        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setInterOpNumThreads(1)
        }

        session = environment.createSession(modelBytes, options)
        options.close()

        val inputInfo = session.inputInfo
        val outputInfo = session.outputInfo

        require(inputInfo.containsKey("images")) {
            "Expected model input named 'images'"
        }

        require(outputInfo.containsKey("output0")) {
            "Expected model output named 'output0'"
        }

        val inputShape =
            (inputInfo["images"]!!.info as TensorInfo).shape

        val outputShape =
            (outputInfo["output0"]!!.info as TensorInfo).shape

        require(
            inputShape.contentEquals(
                longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            )
        ) {
            "Unexpected model input shape: ${inputShape.contentToString()}"
        }

        require(
            outputShape.contentEquals(
                longArrayOf(1, OUTPUT_CHANNELS.toLong(), CANDIDATES.toLong())
            )
        ) {
            "Unexpected model output shape: ${outputShape.contentToString()}"
        }
    }

    /**
     * Runs a dummy-input test to verify ONNX Runtime execution.
     */
    fun runSmokeTest(): String {
        val inputData = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)

        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(inputData),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        ).use { inputTensor ->

            session.run(mapOf("images" to inputTensor)).use { result ->

                val output = result[0] as OnnxTensor
                val outputShape =
                    (output.info as TensorInfo).shape

                require(
                    outputShape.contentEquals(
                        longArrayOf(1, OUTPUT_CHANNELS.toLong(), CANDIDATES.toLong())
                    )
                ) {
                    "Unexpected inference output: ${outputShape.contentToString()}"
                }

                return "ONNX inference successful. Output shape: ${outputShape.contentToString()}"
            }
        }
    }

    /**
     * Runs real object detection on an Android Bitmap.
     *
     * Returns bounding boxes in the original Bitmap's coordinates.
     */
    fun detect(bitmap: Bitmap): List<Detection> {
        require(bitmap.width > 0 && bitmap.height > 0) {
            "Input image is empty"
        }

        val prepared = preprocess(bitmap)

        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(prepared.input),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        ).use { inputTensor ->

            session.run(mapOf("images" to inputTensor)).use { result ->

                val output = result[0] as OnnxTensor

                @Suppress("UNCHECKED_CAST")
                val values = output.value as Array<Array<FloatArray>>

                val predictions = values[0]

                require(
                    predictions.size == OUTPUT_CHANNELS &&
                    predictions.all { it.size == CANDIDATES }
                ) {
                    "Unexpected YOLO output dimensions"
                }

                return decode(
                    predictions = predictions,
                    originalWidth = bitmap.width,
                    originalHeight = bitmap.height,
                    scale = prepared.scale,
                    padLeft = prepared.padLeft,
                    padTop = prepared.padTop
                )
            }
        }
    }

    private data class PreprocessedImage(
        val input: FloatArray,
        val scale: Float,
        val padLeft: Int,
        val padTop: Int
    )

    /**
     * Resizes the image while preserving aspect ratio.
     * Empty areas are filled with RGB 114, matching YOLO letterboxing.
     * Output tensor uses RGB, planar CHW, normalized to 0..1.
     */
    private fun preprocess(bitmap: Bitmap): PreprocessedImage {
        val scale = min(
            INPUT_SIZE.toFloat() / bitmap.width,
            INPUT_SIZE.toFloat() / bitmap.height
        )

        val resizedWidth = (bitmap.width * scale).roundToInt()
            .coerceIn(1, INPUT_SIZE)

        val resizedHeight = (bitmap.height * scale).roundToInt()
            .coerceIn(1, INPUT_SIZE)

        val padLeft = (INPUT_SIZE - resizedWidth) / 2
        val padTop = (INPUT_SIZE - resizedHeight) / 2

        val letterbox = Bitmap.createBitmap(
            INPUT_SIZE,
            INPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(letterbox)
        canvas.drawColor(Color.rgb(114, 114, 114))

        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        val destination = RectF(
            padLeft.toFloat(),
            padTop.toFloat(),
            (padLeft + resizedWidth).toFloat(),
            (padTop + resizedHeight).toFloat()
        )

        canvas.drawBitmap(bitmap, null, destination, paint)

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

        letterbox.getPixels(
            pixels,
            0,
            INPUT_SIZE,
            0,
            0,
            INPUT_SIZE,
            INPUT_SIZE
        )

        letterbox.recycle()

        val planeSize = INPUT_SIZE * INPUT_SIZE
        val input = FloatArray(3 * planeSize)

        for (index in pixels.indices) {
            val color = pixels[index]

            input[index] =
                Color.red(color) / 255.0f

            input[planeSize + index] =
                Color.green(color) / 255.0f

            input[2 * planeSize + index] =
                Color.blue(color) / 255.0f
        }

        return PreprocessedImage(
            input = input,
            scale = scale,
            padLeft = padLeft,
            padTop = padTop
        )
    }

    /**
     * Decodes YOLOv8 output:
     * [1, 84, 3549] = 4 box values + 80 class scores.
     */
    private fun decode(
        predictions: Array<FloatArray>,
        originalWidth: Int,
        originalHeight: Int,
        scale: Float,
        padLeft: Int,
        padTop: Int
    ): List<Detection> {

        val candidates = mutableListOf<Detection>()

        for (index in 0 until CANDIDATES) {

            var bestClass = -1
            var bestConfidence = 0f

            for (classId in 0 until COCO_CLASSES.size) {
                val confidence = predictions[4 + classId][index]

                if (confidence > bestConfidence) {
                    bestConfidence = confidence
                    bestClass = classId
                }
            }

            if (bestConfidence < CONFIDENCE_THRESHOLD || bestClass < 0) {
                continue
            }

            val centerX = predictions[0][index]
            val centerY = predictions[1][index]
            val width = predictions[2][index]
            val height = predictions[3][index]

            val left = (centerX - width / 2f - padLeft) / scale
            val top = (centerY - height / 2f - padTop) / scale
            val right = (centerX + width / 2f - padLeft) / scale
            val bottom = (centerY + height / 2f - padTop) / scale

            val box = RectF(
                left.coerceIn(0f, originalWidth.toFloat()),
                top.coerceIn(0f, originalHeight.toFloat()),
                right.coerceIn(0f, originalWidth.toFloat()),
                bottom.coerceIn(0f, originalHeight.toFloat())
            )

            if (box.width() <= 0f || box.height() <= 0f) {
                continue
            }

            candidates.add(
                Detection(
                    classId = bestClass,
                    label = COCO_CLASSES[bestClass],
                    confidence = bestConfidence,
                    boundingBox = box,
                    frameWidth = originalWidth,
                    frameHeight = originalHeight
                )
            )
        }

        return applyNms(candidates)
    }

    /**
     * Class-aware Non-Maximum Suppression.
     */
    private fun applyNms(
        detections: List<Detection>
    ): List<Detection> {

        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()

        for (candidate in sorted) {

            val shouldSuppress = selected.any { existing ->
                existing.classId == candidate.classId &&
                    intersectionOverUnion(
                        existing.boundingBox,
                        candidate.boundingBox
                    ) > NMS_IOU_THRESHOLD
            }

            if (!shouldSuppress) {
                selected.add(candidate)
            }

            if (selected.size >= MAX_DETECTIONS) {
                break
            }
        }

        return selected
    }

    private fun intersectionOverUnion(
        first: RectF,
        second: RectF
    ): Float {

        val left = max(first.left, second.left)
        val top = max(first.top, second.top)
        val right = min(first.right, second.right)
        val bottom = min(first.bottom, second.bottom)

        val intersectionWidth = max(0f, right - left)
        val intersectionHeight = max(0f, bottom - top)

        val intersection = intersectionWidth * intersectionHeight

        val union =
            first.width() * first.height() +
            second.width() * second.height() -
            intersection

        return if (union > 0f) intersection / union else 0f
    }

    override fun close() {
        session.close()
    }
}
