package com.example.aivisionassistant.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Đọc văn bản trong ảnh bằng Google ML Kit - chạy HOÀN TOÀN ON-DEVICE,
 * không cần gọi mạng/Gemini, nên tốc độ gần như tức thì so với việc
 * gửi ảnh lên server. Phù hợp cho các yêu cầu kiểu "đọc chữ", "đọc văn bản".
 *
 * Lưu ý: ML Kit Text Recognition v2 (Latin) nhận diện tốt chữ in, chữ đánh máy.
 * Với chữ viết tay hoặc văn bản mờ/nghiêng nhiều, độ chính xác sẽ giảm —
 * trường hợp đó vẫn có thể fallback sang Gemini để mô tả thay vì chỉ đọc chữ.
 */
class TextOcrHelper {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Nhận diện văn bản trong bitmap, trả về chuỗi text đã đọc được.
     * Trả về chuỗi rỗng nếu không tìm thấy chữ nào trong ảnh.
     */
    suspend fun recognizeText(bitmap: Bitmap): String {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val result = visionText.text.trim()
                    continuation.resume(result)
                }
                .addOnFailureListener { e ->
                    continuation.resume("") // coi như không đọc được chữ, để code gọi quyết định fallback
                }
        }
    }

    fun close() {
        recognizer.close()
    }
}