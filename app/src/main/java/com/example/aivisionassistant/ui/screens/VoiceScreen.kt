package com.example.aivisionassistant.ui.screens

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aivisionassistant.api.GeminiManager
import kotlinx.coroutines.launch

@Composable
fun VoiceRecognitionScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Khởi tạo Gemini Manager
    val geminiManager = remember { GeminiManager() }

    var recognizedText by remember { mutableStateOf("Nhấn vào mic để bắt đầu nói...") }
    var aiResponse by remember { mutableStateOf("") } // Biến lưu câu trả lời của AI
    var isListening by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) } // Biến hiển thị trạng thái chờ AI suy nghĩ

    val speechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { recognizedText = "Đang lắng nghe..." }
                override fun onBeginningOfSpeech() { isListening = true; aiResponse = "" }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }
                override fun onError(error: Int) {
                    isListening = false
                    recognizedText = "Không nghe rõ. Vui lòng thử lại! (Mã lỗi: $error)"
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        recognizedText = matches[0]

                        // KHI NGHE XONG, GỌI API GEMINI
                        isLoading = true
                        coroutineScope.launch {
                            aiResponse = geminiManager.getResponse(recognizedText)
                            isLoading = false
                        }
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) { recognizedText = matches[0] }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    DisposableEffect(Unit) {
        onDispose { speechRecognizer.destroy() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f) // Đẩy chiều cao thẻ lên để có chỗ chứa câu trả lời dài
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Phần hiển thị hội thoại (Có thể cuộn được)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Lời của người dùng
                Text(
                    text = recognizedText,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (aiResponse.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Trạng thái AI đang suy nghĩ
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
                }

                // Câu trả lời của AI
                if (aiResponse.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = aiResponse,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Nút bấm ghi âm luôn nằm ở dưới cùng
            Button(
                onClick = {
                    if (isListening) speechRecognizer.stopListening() else speechRecognizer.startListening(speechIntent)
                },
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Ghi âm",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }
        }
    }
}