package com.example.aivisionassistant.ui.screens

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.camera.view.PreviewView
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
import java.util.Locale

@Composable
fun VoiceRecognitionScreen(previewView: PreviewView?) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val geminiManager = remember { GeminiManager() }

    var recognizedText by remember { mutableStateOf("Nhấn vào mic để bắt đầu nói...") }
    var aiResponse by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // Khởi tạo bộ đọc giọng nói (Text-To-Speech)
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("vi", "VN")
            }
        }
        textToSpeech = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val speechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { recognizedText = "Đang lắng nghe..." }

                override fun onBeginningOfSpeech() {
                    isListening = true
                    aiResponse = ""
                    textToSpeech?.stop()
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }

                // ĐÃ CẬP NHẬT: Xử lý lỗi chuẩn cho điện thoại thật
                override fun onError(error: Int) {
                    isListening = false
                    isLoading = false

                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tôi không nghe thấy gì. Bạn có thể nói lại được không?"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Tôi chưa nghe rõ lệnh. Bạn vui lòng thử lại nhé."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Lỗi mạng. Vui lòng kiểm tra lại 4G hoặc Wifi."
                        else -> "Có lỗi âm thanh xảy ra. Vui lòng thử lại."
                    }

                    recognizedText = errorMessage
                    textToSpeech?.speak(errorMessage, TextToSpeech.QUEUE_FLUSH, null, null)
                }

                // Xử lý khi người dùng nói thật
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        recognizedText = matches[0]
                        isLoading = true

                        coroutineScope.launch {
                            // Cắt ảnh từ Camera nền
                            val bitmap = previewView?.bitmap

                            val response = if (bitmap != null) {
                                geminiManager.getResponseWithImage(recognizedText, bitmap)
                            } else {
                                geminiManager.getResponse(recognizedText)
                            }

                            aiResponse = response
                            isLoading = false
                            textToSpeech?.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
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
            .fillMaxHeight(0.5f)
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = recognizedText,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (aiResponse.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
                }

                if (aiResponse.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = aiResponse,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }

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