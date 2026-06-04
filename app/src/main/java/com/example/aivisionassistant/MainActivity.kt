package com.example.aivisionassistant

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.aivisionassistant.ui.components.*
import com.example.aivisionassistant.ui.screens.*
import com.example.aivisionassistant.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIVisionAssistantTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AIVisionApp()
                }
            }
        }
    }
}

@Composable
fun AIVisionApp() {
    var selectedTabIndex by remember { mutableIntStateOf(2) }
    var cameraPreviewView by remember { mutableStateOf<PreviewView?>(null) }

    var detectedObject by remember { mutableStateOf("Đang quét...") }
    var detectedDistance by remember { mutableStateOf("...") }
    var isDangerZone by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    LaunchedEffect(isDangerZone) {
        if (isDangerZone) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
        topBar = { TopBarUI() },
        bottomBar = {
            BottomNavigationBarUI(selectedIndex = selectedTabIndex, onItemSelected = { selectedTabIndex = it })
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // Lớp 1: Camera hiển thị full màn hình
            CameraContent(
                onPreviewViewCreated = { cameraPreviewView = it },
                onObjectDetected = { objName, distance, isDanger ->
                    detectedObject = objName
                    detectedDistance = distance
                    isDangerZone = isDanger
                }
            )

            // Lớp 2: Giao diện AR nổi đè lên trên Camera
            when (selectedTabIndex) {
                // Tab Giọng Nói (Nếu bạn bấm nhầm) thì nó vẫn hiện y hệt Tab Quét
                1, 2 -> {
                    Box(modifier = Modifier.fillMaxSize()) {

                        // Ở TRÊN CÙNG: Phụ đề Giọng nói & Trả lời của AI
                        Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                            VoiceRecognitionScreen(cameraPreviewView)
                        }

                        // Ở DƯỚI CÙNG: Thẻ báo nguy hiểm rung/màu đỏ
                        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                            VisionInfoCard(detectedObject, detectedDistance, isDangerZone)
                        }
                    }
                }
            }
        }
    }
}