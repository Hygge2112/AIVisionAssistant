package com.example.aivisionassistant.api

import android.graphics.Bitmap // ĐÃ THÊM: Thư viện xử lý hình ảnh
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiManager {
    // TODO: BẠN HÃY DÁN API KEY MỚI TẠO VÀO ĐÂY (Tuyệt đối không chia sẻ cho ai)
    private val apiKey = "YOUR_API_KEY_HERE"

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    // Thiết lập tính cách (System Prompt) cho AI
    private val systemInstruction = """
        Bạn là một Trợ lý Thị giác AI tận tâm, được thiết kế để hỗ trợ người khiếm thị.
        Nhiệm vụ của bạn là nhận câu hỏi từ người dùng (dưới dạng văn bản được chuyển từ giọng nói) 
        và phân tích hình ảnh từ camera (nếu có) để đưa ra câu trả lời ngắn gọn, rõ ràng, dễ nghe. 
        Đừng sử dụng định dạng phức tạp (như in đậm, in nghiêng, bảng biểu) vì người dùng sẽ nghe câu trả lời qua máy đọc.
        Hãy xưng hô là "Tôi" và gọi người dùng là "Bạn".
    """.trimIndent()

    // HÀM 1: Chỉ xử lý giọng nói (Dành cho lúc camera chưa sẵn sàng)
    suspend fun getResponse(userText: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val chat = generativeModel.startChat(
                    history = listOf(
                        content(role = "user") { text(systemInstruction) },
                        content(role = "model") { text("Đã hiểu. Tôi đã sẵn sàng hỗ trợ bạn.") }
                    )
                )

                val response = chat.sendMessage(userText)
                response.text ?: "Xin lỗi, tôi không thể xử lý yêu cầu này lúc này."

            } catch (e: Exception) {
                "Đã có lỗi xảy ra khi kết nối: ${e.localizedMessage}"
            }
        }
    }

    // HÀM 2 (MỚI THÊM): Xử lý cả Giọng nói và Hình ảnh
    suspend fun getResponseWithImage(userText: String, image: Bitmap): String {
        return withContext(Dispatchers.IO) {
            try {
                // Gộp chung lời nhắc hệ thống, hình ảnh và câu hỏi vào một gói
                val response = generativeModel.generateContent(
                    content {
                        image(image)
                        text("$systemInstruction\n\nCâu hỏi của người dùng: $userText")
                    }
                )
                response.text ?: "Xin lỗi, tôi không thể phân tích hình ảnh này."
            } catch (e: Exception) {
                "Đã có lỗi xảy ra khi nhìn ảnh: ${e.localizedMessage}"
            }
        }
    }
}