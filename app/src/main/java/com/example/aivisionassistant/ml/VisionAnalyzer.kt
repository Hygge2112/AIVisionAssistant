package com.example.aivisionassistant.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class VisionAnalyzer(
    private val context: Context,
    private val onObjectDetected: (String, String, Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    private var objectDetector: ObjectDetector? = null

    init {
        // ĐÃ FIX: Hạ độ khắt khe xuống 30% và cho phép quét 10 vật thể cùng lúc để phù hợp mật độ giao thông VN
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setScoreThreshold(0.30f)
            .setMaxResults(10)
            .build()

        try {
            objectDetector = ObjectDetector.createFromFileAndOptions(context, "yolo_model.tflite", options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()

        // ĐÃ FIX: Xoay ảnh về đúng chiều dọc (Portrait) trước khi đưa cho AI phân tích
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        }
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // Đưa bức ảnh đã xoay đúng chiều vào bộ đọc Tensor
        val tensorImage = TensorImage.fromBitmap(rotatedBitmap)
        val results = objectDetector?.detect(tensorImage)

        if (results.isNullOrEmpty()) {
            onObjectDetected("Đường đi an toàn", "Trống", false)
        } else {
            val objectNames = results.joinToString(", ") { it.categories.first().label }

            val closestObject = results.maxByOrNull {
                it.boundingBox.width() * it.boundingBox.height()
            }

            if (closestObject != null) {
                // Tính toán tỷ lệ dựa trên kích thước bức ảnh ĐÃ XOAY
                val screenArea = rotatedBitmap.width * rotatedBitmap.height
                val boxArea = closestObject.boundingBox.width() * closestObject.boundingBox.height()
                val ratio = boxArea / screenArea.toFloat()

                var distance: String
                var isDanger: Boolean

                if (ratio > 0.35f) {
                    distance = "Dưới 1.0m"
                    isDanger = true
                } else if (ratio > 0.12f) {
                    distance = "Khoảng 1.0 - 2.5m"
                    isDanger = false
                } else {
                    distance = "Trên 3.0m"
                    isDanger = false
                }

                if (objectNames.contains("person", ignoreCase = true) && ratio > 0.2f) {
                    isDanger = true
                    distance = "Cẩn thận người phía trước!"
                }

                onObjectDetected(objectNames, distance, isDanger)
            }
        }

        imageProxy.close()
    }
}