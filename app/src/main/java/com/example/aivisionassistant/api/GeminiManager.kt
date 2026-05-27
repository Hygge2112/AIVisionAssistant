package com.example.aivisionassistant.api

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiManager {
    // TODO: BẠN PHẢI THAY BẰNG API KEY THẬT CỦA BẠN VÀO ĐÂY
    private val apiKey = "AIzaSyDXu1zS2hteJtHxXXvqD0ULJ9fLIOSEC44"

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash", // Mô hình Flash nhanh nhất cho việc phản hồi giọng nói
        apiKey = apiKey
    )

    // Thiết lập tính cách (System Prompt) cho AI
    private val systemInstruction = """
        Bạn là một Trợ lý Thị giác AI tận tâm, được thiết kế để hỗ trợ người khiếm thị.
        Nhiệm vụ của bạn là nhận câu hỏi từ người dùng (dưới dạng văn bản được chuyển từ giọng nói) 
        và đưa ra câu trả lời ngắn gọn, rõ ràng, dễ nghe. 
        Đừng sử dụng định dạng phức tạp (như in đậm, in nghiêng, bảng biểu) vì người dùng sẽ nghe câu trả lời qua máy đọc.
        Hãy xưng hô là "Tôi" và gọi người dùng là "Bạn".
    """.trimIndent()

    suspend fun getResponse(userText: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Tạo một đoạn hội thoại mới kèm theo system prompt
                val chat = generativeModel.startChat(
                    history = listOf(
                        content(role = "user") { text(systemInstruction) },
                        content(role = "model") { text("Đã hiểu. Tôi đã sẵn sàng hỗ trợ bạn.") }
                    )
                )

                // Gửi câu hỏi của người dùng và chờ kết quả
                val response = chat.sendMessage(userText)

                // Trả về văn bản, nếu null thì báo lỗi
                response.text ?: "Xin lỗi, tôi không thể xử lý yêu cầu này lúc này."

            } catch (e: Exception) {
                "Đã có lỗi xảy ra khi kết nối: ${e.localizedMessage}"
            }
        }
    }
}