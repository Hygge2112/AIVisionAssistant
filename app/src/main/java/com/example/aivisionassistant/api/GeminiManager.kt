package com.example.aivisionassistant.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayOutputStream

class GeminiManager {

    // TODO: BẠN HÃY DÁN API KEY MỚI TẠO VÀO ĐÂY
    // QUAN TRỌNG: Key cũ đã từng dán vào đây nên được REVOKE ngay trong Google AI Studio.
    // Không hardcode key thật khi build bản chính thức — hãy đưa vào local.properties + BuildConfig.
    private val apiKey = "DAN_API_KEY_MOI_VAO_DAY"

    // Thiết lập tính cách (System Prompt) cho AI
    private val systemInstructionText = """
        Bạn là một Trợ lý Thị giác AI tận tâm, được thiết kế để hỗ trợ người khiếm thị.
        Nhiệm vụ của bạn là nhận câu hỏi từ người dùng (dưới dạng văn bản được chuyển từ giọng nói)
        và phân tích hình ảnh từ camera (nếu có) để đưa ra câu trả lời ngắn gọn, rõ ràng, dễ nghe.
        Đừng sử dụng định dạng phức tạp (như in đậm, in nghiêng, bảng biểu) vì người dùng sẽ nghe câu trả lời qua máy đọc.
        Hãy xưng hô là "Tôi" và gọi người dùng là "Bạn".
    """.trimIndent()

    // Giới hạn output token để câu trả lời ngắn gọn (đúng tinh thần system prompt) và nhanh hơn.
    // Giảm temperature để model bớt "lan man", trả lời thẳng và ổn định hơn.
    // Nếu version SDK bạn dùng đã hỗ trợ thinkingBudget, có thể thêm dòng:
    //     thinkingBudget = 0
    // vào trong generationConfig { } để tắt bước "suy nghĩ" của Gemini 2.5 Flash,
    // giúp giảm thêm latency. Kiểm tra changelog của com.google.ai.client.generativeai
    // nếu dòng đó báo lỗi "unresolved reference" tức là version hiện tại chưa hỗ trợ.
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        systemInstruction = content { text(systemInstructionText) },
        generationConfig = generationConfig {
            maxOutputTokens = 630
            temperature = 0.4f
        }
    )

    /**
     * Resize ảnh trước khi gửi lên Gemini.
     * Ảnh từ camera thường rất lớn (full HD+), gửi nguyên kích thước làm chậm cả
     * lúc encode lẫn lúc upload. 1024px chiều dài là đủ để model nhận diện tốt.
     */
    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int = 1024): Bitmap {
        val ratio = minOf(
            maxDimension.toFloat() / bitmap.width,
            maxDimension.toFloat() / bitmap.height
        )
        if (ratio >= 1f) return bitmap // ảnh đã nhỏ hơn giới hạn, không cần resize
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Nén ảnh dưới dạng JPEG để giảm dung lượng dữ liệu thực gửi đi,
     * ngoài việc giảm kích thước pixel ở resizeBitmap(). Quality 70 vẫn đủ
     * rõ để model nhận diện vật thể/chữ, nhưng nhẹ hơn đáng kể so với mặc định.
     */
    private fun compressBitmap(bitmap: Bitmap, quality: Int = 70): Bitmap {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val bytes = stream.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun prepareImage(bitmap: Bitmap): Bitmap {
        return compressBitmap(resizeBitmap(bitmap))
    }

    // HÀM 1: Chỉ xử lý giọng nói (không kèm ảnh), trả về toàn bộ câu trả lời 1 lần.
    suspend fun getResponse(userText: String): String {
        return try {
            val response = generativeModel.generateContent(
                content { text(userText) }
            )
            response.text ?: "Xin lỗi, tôi không thể xử lý yêu cầu này lúc này."
        } catch (e: Exception) {
            "Đã có lỗi xảy ra khi kết nối: ${e.localizedMessage}"
        }
    }

    // HÀM 2: Xử lý cả Giọng nói và Hình ảnh, trả về toàn bộ câu trả lời 1 lần.
    suspend fun getResponseWithImage(userText: String, image: Bitmap): String {
        return try {
            val preparedImage = prepareImage(image)
            val response = generativeModel.generateContent(
                content {
                    image(preparedImage)
                    text(userText)
                }
            )
            response.text ?: "Xin lỗi, tôi không thể phân tích hình ảnh này."
        } catch (e: Exception) {
            "Đã có lỗi xảy ra khi nhìn ảnh: ${e.localizedMessage}"
        }
    }

    /**
     * HÀM 3: Phiên bản streaming - phát ra từng đoạn text ngay khi Gemini trả về,
     * giúp TTS đọc dần theo từng câu thay vì chờ toàn bộ phản hồi.
     */
    fun getResponseWithImageStream(userText: String, image: Bitmap): Flow<String> = flow {
        try {
            val preparedImage = prepareImage(image)
            generativeModel.generateContentStream(
                content {
                    image(preparedImage)
                    text(userText)
                }
            ).collect { chunk ->
                chunk.text?.let { emit(it) }
            }
        } catch (e: Exception) {
            emit("Đã có lỗi xảy ra khi nhìn ảnh: ${e.localizedMessage}")
        }
    }.flowOn(Dispatchers.IO)

    // Phiên bản streaming khi không có ảnh (camera chưa sẵn sàng)
    fun getResponseStream(userText: String): Flow<String> = flow {
        try {
            generativeModel.generateContentStream(
                content { text(userText) }
            ).collect { chunk ->
                chunk.text?.let { emit(it) }
            }
        } catch (e: Exception) {
            emit("Đã có lỗi xảy ra khi kết nối: ${e.localizedMessage}")
        }
    }.flowOn(Dispatchers.IO)
}