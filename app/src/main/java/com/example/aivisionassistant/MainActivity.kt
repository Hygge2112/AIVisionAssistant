package com.example.aivisionassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.view.PreviewView // ĐÃ THÊM: Import thư viện camera
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aivisionassistant.ui.components.*
import com.example.aivisionassistant.ui.screens.*
import com.example.aivisionassistant.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIVisionAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AIVisionApp()
                }
            }
        }
    }
}

@Composable
fun AIVisionApp() {
    var selectedTabIndex by remember { mutableIntStateOf(2) }

    // BIẾN CẦU NỐI: Lưu giữ Camera để cắt ảnh bất cứ lúc nào
    var cameraPreviewView by remember { mutableStateOf<PreviewView?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        topBar = { TopBarUI() },
        bottomBar = {
            BottomNavigationBarUI(
                selectedIndex = selectedTabIndex,
                onItemSelected = { selectedTabIndex = it }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Lớp Camera ở dưới cùng: Hứng lấy camera và lưu vào biến cầu nối
            CameraContent(onPreviewViewCreated = { cameraPreviewView = it })

            // Lớp phủ tối mờ Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CameraOverlay)
            )

            // Lớp hiển thị UI theo Tab
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                when (selectedTabIndex) {
                    // TRUYỀN Camera từ biến cầu nối sang màn hình Giọng nói
                    1 -> VoiceRecognitionScreen(cameraPreviewView)
                    2 -> VisionInfoCard()
                }
            }
        }
    }
}