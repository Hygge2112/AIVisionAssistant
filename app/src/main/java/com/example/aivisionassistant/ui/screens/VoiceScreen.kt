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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun VoiceRecognitionScreen(previewView: PreviewView?) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val geminiManager = remember { GeminiManager() }

    var recognizedText by remember { mutableStateOf("Sẵn sàng nhận lệnh...") }
    var aiResponse by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }

    // Biến kích hoạt khởi tạo lại SpeechRecognizer một cách an toàn
    var speechRecognizerTrigger by remember { mutableStateOf(0) }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
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

                // BIỆN PHÁP SỬA LỖI: Nếu Mic bị ngắt/hết hạn, ta hủy phiên cũ và kích hoạt làm mới Session
                speechRecognizerTrigger++
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedText = matches[0]
                    isLoading = true

                    // Phát âm thanh báo hiệu hệ thống bắt đầu xử lý ảnh/văn bản
                    textToSpeech?.speak("Đang kiểm tra", TextToSpeech.QUEUE_FLUSH, null, "PROCESSING")

                    coroutineScope.launch {
                        val bitmap = previewView?.bitmap
                        val response = if (bitmap != null) {
                            geminiManager.getResponseWithImage(recognizedText, bitmap)
                        } else {
                            geminiManager.getResponse(recognizedText)
                        }
                        aiResponse = response
                        isLoading = false
                    }
                } else {
                    // Nếu không nhận được chữ, khởi động lại bộ lắng nghe
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

        // Bắt đầu lắng nghe ngay khi phiên làm việc được thiết lập sạch sẽ
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
                    override fun onStart(utteranceId: String?) {
                        // Không làm gì để tránh xung đột ngang luồng
                    }

                    override fun onDone(utteranceId: String?) {
                        // Khi AI đọc xong nội dung văn bản/vật thể, ép làm mới luồng Mic để nghe câu hỏi tiếp theo
                        coroutineScope.launch(Dispatchers.Main) {
                            speechRecognizerTrigger++
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })

                // Câu chào duy nhất khi mở màn hình
                ttsInstance?.speak("Xin chào tôi có thể giúp gì cho bạn", TextToSpeech.QUEUE_FLUSH, null, "GREETING")
            }
        }
        textToSpeech = ttsInstance

        onDispose {
            ttsInstance?.stop()
            ttsInstance?.shutdown()
        }
    }

    // Theo dõi câu trả lời từ Gemini để phát ra loa
    LaunchedEffect(aiResponse) {
        if (aiResponse.isNotEmpty()) {
            textToSpeech?.speak(aiResponse, TextToSpeech.QUEUE_FLUSH, null, "AI_RESPONSE")
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