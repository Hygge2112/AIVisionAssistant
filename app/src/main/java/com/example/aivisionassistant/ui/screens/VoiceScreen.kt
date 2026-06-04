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

    var recognizedText by remember { mutableStateOf("Hệ thống đã sẵn sàng...") }
    var aiResponse by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    val speechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { recognizedText = "Tôi đang nghe đây..." }
                override fun onBeginningOfSpeech() { isListening = true; aiResponse = "" }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }

                override fun onError(error: Int) {
                    isListening = false
                    isLoading = false
                    if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                        recognizedText = "Đang chờ lệnh từ bạn..."
                        coroutineScope.launch(Dispatchers.Main) { startListening(speechIntent) }
                    } else {
                        recognizedText = "Lỗi mạng. Vui lòng kiểm tra lại."
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        recognizedText = matches[0]
                        isLoading = true
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

    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.setLanguage(Locale("vi", "VN"))
                ttsInstance?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        coroutineScope.launch(Dispatchers.Main) { speechRecognizer.startListening(speechIntent) }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })
                ttsInstance?.speak("Xin chào, tôi có thể giúp gì được cho bạn?", TextToSpeech.QUEUE_FLUSH, null, "GREETING")
            }
        }
        textToSpeech = ttsInstance
        onDispose {
            ttsInstance?.stop()
            ttsInstance?.shutdown()
            speechRecognizer.destroy()
        }
    }

    LaunchedEffect(aiResponse) {
        if (aiResponse.isNotEmpty()) {
            textToSpeech?.speak(aiResponse, TextToSpeech.QUEUE_FLUSH, null, "AI_RESPONSE")
        }
    }

    // ĐÃ FIX: Giao diện phụ đề nổi (Không viền, Không nền che khuất Camera)
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
            // Icon Mic nhỏ gọn báo trạng thái Đỏ (Đang nghe)
            Icon(
                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                contentDescription = "Mic",
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isListening) MaterialTheme.colorScheme.error else Color.Black.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .padding(8.dp),
                tint = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Phụ đề: Lời người dùng nói
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
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            }

            // Phụ đề: AI trả lời
            if (aiResponse.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = aiResponse,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Cyan, // Chữ màu Cyan phát sáng cho dễ đọc trên nền camera
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}