package com.example.aivisionassistant.ml

import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class VisionAnalyzer(
    private val onObjectDetected: (String, String, Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    private val objectOptions = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableMultipleObjects()
        .build()
    private val objectDetector = ObjectDetection.getClient(objectOptions)

    private val labelerOptions = ImageLabelerOptions.Builder()
        .setConfidenceThreshold(0.6f)
        .build()
    private val labeler = ImageLabeling.getClient(labelerOptions)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            objectDetector.process(image).addOnSuccessListener { detectedObjects: List<DetectedObject> ->
                var estimatedDistance = "An toàn"
                var isDanger = false

                if (detectedObjects.isNotEmpty()) {
                    val biggestObject = detectedObjects.maxByOrNull {
                        val bounds: Rect = it.boundingBox
                        bounds.width() * bounds.height()
                    }
                    if (biggestObject != null) {
                        val imageArea = imageProxy.width * imageProxy.height
                        val boxArea = biggestObject.boundingBox.width() * biggestObject.boundingBox.height()
                        val ratio = boxArea.toFloat() / imageArea.toFloat()

                        if (ratio > 0.40f) {
                            estimatedDistance = "Dưới 1.0m"
                            isDanger = true
                        } else if (ratio > 0.15f) {
                            estimatedDistance = "Khoảng 1.5m - 2.0m"
                            isDanger = false
                        } else {
                            estimatedDistance = "Trên 3.0m"
                            isDanger = false
                        }
                    }
                }

                labeler.process(image).addOnSuccessListener { labels ->
                    if (labels.isNotEmpty()) {
                        val topLabels = labels.take(3).map { it.text }.joinToString(",")

                        // ĐÃ FIX: THUẬT TOÁN CHÉO NHẬN DIỆN CON NGƯỜI
                        // Nếu AI thấy rõ các bộ phận này, chắc chắn có người đang đứng ngay trước mặt!
                        val humanParts = listOf("Face", "Hair", "Smile", "Selfie", "Skin", "Hand", "Arm", "Leg", "Sitting", "Standing", "Portrait")
                        val isHumanClose = labels.take(3).any { it.text in humanParts && it.confidence > 0.7f }

                        var finalDistance = estimatedDistance
                        var finalDanger = isDanger

                        // Ép báo động đỏ nếu thấy rõ người mà thuật toán khoảng cách bị sai
                        if (isHumanClose && !isDanger) {
                            finalDistance = "Dưới 1.0m"
                            finalDanger = true
                        }

                        onObjectDetected(topLabels, finalDistance, finalDanger)
                    }
                }.addOnCompleteListener {
                    imageProxy.close()
                }

            }.addOnFailureListener {
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }
}