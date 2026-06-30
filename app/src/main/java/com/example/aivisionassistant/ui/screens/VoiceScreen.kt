package com.example.aivisionassistant.ui.screens

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aivisionassistant.api.GeminiManager
import com.example.aivisionassistant.ml.TextOcrHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

// Các từ khóa cho biết người dùng muốn ĐỌC CHỮ, không phải hỏi mô tả chung chung.
// Khi khớp, app sẽ dùng ML Kit OCR (on-device, nhanh) thay vì gọi Gemini.
// Danh sách được viết CÓ DẤU cho dễ đọc/maintain, việc so khớp thực tế sẽ
// bỏ dấu cả 2 bên (xem removeDiacritics + isOcrRequest bên dưới) để không bị
// trượt khi Speech-to-Text nhận thiếu/sai dấu thanh.
private val OCR_KEYWORDS = listOf(
    // Yêu cầu trực tiếp
    "đọc chữ", "đọc văn bản", "đọc giúp tôi", "đọc giùm",
    "đọc dòng chữ", "đọc nội dung", "đọc cái này", "đọc tờ giấy",
    "đọc cho tôi", "đọc xem", "đọc thử",
    // Hỏi về nội dung chữ
    "có chữ gì", "viết gì", "ghi gì", "ghi cái gì",
    "viết cái gì", "nội dung là gì", "nội dung gì",
    // Ngữ cảnh cụ thể hay gặp (nhãn, biển báo, tài liệu)
    "trên nhãn", "trên biển", "trên hộp", "trên gói",
    "trên tờ giấy", "trên bao bì", "hạn sử dụng",
    "thành phần", "tên thuốc", "giá bao nhiêu", "số mấy"
)

// Bỏ dấu tiếng Việt (kể cả "đ" -> "d") để so khớp không bị ảnh hưởng
// bởi việc Speech-to-Text nhận thiếu/sai dấu thanh.
private fun removeDiacritics(text: String): String {
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
    return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace('đ', 'd').replace('Đ', 'D')
}

private val OCR_KEYWORDS_NO_DIACRITICS = OCR_KEYWORDS.map {
    removeDiacritics(it.lowercase(Locale("vi", "VN")))
}

private fun isOcrRequest(text: String): Boolean {
    val normalized = removeDiacritics(text.lowercase(Locale("vi", "VN")))
    return OCR_KEYWORDS_NO_DIACRITICS.any { normalized.contains(it) }
}

@Composable
fun VoiceRecognitionScreen(previewView: PreviewView?) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val geminiManager = remember { GeminiManager() }
    val ocrHelper = remember { TextOcrHelper() }

    var recognizedText by remember { mutableStateOf("Sẵn sàng nhận lệnh...") }
    var aiResponse by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }

    // Biến kích hoạt khởi tạo lại SpeechRecognizer một cách an toàn
    var speechRecognizerTrigger by remember { mutableStateOf(0) }

    // Đếm số câu đã được gửi cho TTS đọc trong lượt trả lời hiện tại,
    // dùng để biết khi nào AI đã đọc xong toàn bộ (để mở lại Mic).
    val utteranceCounter = remember { AtomicInteger(0) }
    var pendingUtterances by remember { mutableStateOf(0) }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    // Dọn dẹp ML Kit recognizer khi rời màn hình
    DisposableEffect(Unit) {
        onDispose { ocrHelper.close() }
    }

    // Tách văn bản streaming thành từng câu hoàn chỉnh để TTS đọc ngay khi nhận được,
    // không cần chờ toàn bộ phản hồi từ Gemini.
    fun speakSentencesFrom(buffer: StringBuilder, isFinal: Boolean) {
        val regex = Regex("(?<=[.!?\\n])\\s*")
        val text = buffer.toString()
        val parts = text.split(regex)

        val completeParts = if (isFinal) parts else parts.dropLast(1)
        if (completeParts.isEmpty()) return

        val remaining = if (isFinal) "" else parts.lastOrNull() ?: ""

        completeParts.forEach { sentence ->
            val trimmed = sentence.trim()
            if (trimmed.isNotEmpty()) {
                val id = "AI_RESPONSE_${utteranceCounter.incrementAndGet()}"
                pendingUtterances++
                textToSpeech?.speak(trimmed, TextToSpeech.QUEUE_ADD, null, id)
            }
        }

        buffer.clear()
        buffer.append(remaining)
    }

    // Đọc một câu trả lời ngắn (dùng cho kết quả OCR, vốn trả về 1 lần, không streaming)
    fun speakWholeText(text: String) {
        val id = "AI_RESPONSE_${utteranceCounter.incrementAndGet()}"
        pendingUtterances++
        textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    // Gọi Gemini streaming và đọc dần kết quả - dùng chung cho cả nhánh hỏi thường
    // lẫn nhánh fallback (khi OCR không tìm thấy chữ).
    suspend fun askGeminiAndSpeak(question: String, bitmap: android.graphics.Bitmap?) {
        val sentenceBuffer = StringBuilder()
        val fullTextBuffer = StringBuilder()

        val flow = if (bitmap != null) {
            geminiManager.getResponseWithImageStream(question, bitmap)
        } else {
            geminiManager.getResponseStream(question)
        }

        flow.collect { chunk ->
            sentenceBuffer.append(chunk)
            fullTextBuffer.append(chunk)
            aiResponse = fullTextBuffer.toString()
            speakSentencesFrom(sentenceBuffer, isFinal = false)
        }
        speakSentencesFrom(sentenceBuffer, isFinal = true)
    }

    // Quản lý Vòng đời an toàn của Bộ nhận diện giọng nói
    DisposableEffect(speechRecognizerTrigger) {
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                recognizedText = "Tôi đang nghe bạn nói..."
                isListening = true
            }
            override fun onBeginningOfSpeech() {
                aiResponse = ""
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                isLoading = false
                speechRecognizerTrigger++
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedText = matches[0]
                    isLoading = true
                    aiResponse = ""

                    val bitmap = previewView?.bitmap

                    if (isOcrRequest(recognizedText) && bitmap != null) {
                        // ĐƯỜNG NHANH: dùng ML Kit on-device, không gọi Gemini
                        coroutineScope.launch {
                            val ocrResult = ocrHelper.recognizeText(bitmap)

                            if (ocrResult.isBlank()) {
                                // Không tìm thấy chữ trong khung hình -> tự động chuyển
                                // sang hỏi Gemini để mô tả vật thể thay vì chỉ báo lỗi suông.
                                askGeminiAndSpeak(
                                    "Trước mặt tôi không có chữ, hãy mô tả ngắn gọn vật thể đang nhìn thấy giúp tôi",
                                    bitmap
                                )
                            } else {
                                aiResponse = ocrResult
                                speakWholeText(ocrResult)
                            }

                            isLoading = false
                            if (pendingUtterances == 0) {
                                speechRecognizerTrigger++
                            }
                        }
                    } else {
                        // ĐƯỜNG BÌNH THƯỜNG: hỏi mô tả/phân tích chung -> gọi Gemini streaming
                        coroutineScope.launch {
                            askGeminiAndSpeak(recognizedText, bitmap)
                            isLoading = false
                            if (pendingUtterances == 0) {
                                speechRecognizerTrigger++
                            }
                        }
                    }
                } else {
                    speechRecognizerTrigger++
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedText = matches[0]
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(speechIntent)

        onDispose {
            speechRecognizer.stopListening()
            speechRecognizer.cancel()
            speechRecognizer.destroy()
        }
    }

    // Quản lý TextToSpeech và điều phối Luồng loa phát
    DisposableEffect(context) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.setLanguage(Locale("vi", "VN"))

                ttsInstance?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId?.startsWith("AI_RESPONSE_") == true) {
                            pendingUtterances--
                            if (pendingUtterances <= 0) {
                                pendingUtterances = 0
                                coroutineScope.launch(Dispatchers.Main) {
                                    speechRecognizerTrigger++
                                }
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId?.startsWith("AI_RESPONSE_") == true) {
                            pendingUtterances = (pendingUtterances - 1).coerceAtLeast(0)
                        }
                    }
                })

                ttsInstance?.speak("Xin chào tôi có thể giúp gì cho bạn", TextToSpeech.QUEUE_FLUSH, null, "GREETING")
            }
        }
        textToSpeech = ttsInstance

        onDispose {
            ttsInstance?.stop()
            ttsInstance?.shutdown()
        }
    }

    // Layout giao diện hiển thị trong suốt đè lên Camera Preview
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                contentDescription = "Mic Status",
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isListening) MaterialTheme.colorScheme.error else Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .padding(10.dp),
                tint = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (recognizedText.isNotEmpty()) {
                Text(
                    text = recognizedText,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(26.dp), color = Color.White)
            }

            if (aiResponse.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = aiResponse,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Cyan,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}