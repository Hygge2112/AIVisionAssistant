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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.aivisionassistant.ui.components.*
import com.example.aivisionassistant.ui.screens.*
import com.example.aivisionassistant.ui.theme.*
import kotlinx.coroutines.launch

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
    // ĐÃ FIX 1: Dùng PagerState thay cho selectedTabIndex để quản lý trạng thái vuốt
    // Khởi tạo ở trang số 1 (Trang Quét Camera)
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

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

    // Cơ chế báo rung khi gặp vật cản
    LaunchedEffect(isDangerZone) {
        if (isDangerZone) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Đã tối ưu: Nhịp rung kép (Tít-Tít) cảnh báo mạnh hơn cho người khiếm thị
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 150), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 150, 100, 150), -1)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
        topBar = { TopBarUI() },
        bottomBar = {
            BottomNavigationBarUI(
                selectedIndex = pagerState.currentPage,
                onItemSelected = { index ->
                    // Bấm nút thì sẽ vuốt mượt mà tới trang đó
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }
            )
        }
    ) { paddingValues ->

        // ĐÃ FIX 2: Bọc toàn bộ các trang vào HorizontalPager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->

            // Camera luôn chạy ngầm ở tất cả các trang để không bị gián đoạn âm thanh/nhận diện
            Box(modifier = Modifier.fillMaxSize()) {
                CameraContent(
                    onPreviewViewCreated = { cameraPreviewView = it },
                    onObjectDetected = { objName, distance, isDanger ->
                        detectedObject = objName
                        detectedDistance = distance
                        isDangerZone = isDanger
                    }
                )

                // Lớp giao diện đè lên camera tùy theo Trang đang vuốt tới
                when (page) {
                    0 -> {
                        // TRANG TRÁI CÙNG: SOS & Dẫn đường
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            SosScreen()
                        }
                    }
                    1 -> {
                        // TRANG Ở GIỮA: Mắt thần AI (Hiển thị xuyên thấu)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 16.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                                VoiceRecognitionScreen(cameraPreviewView)
                            }
                            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                                VisionInfoCard(detectedObject, detectedDistance, isDangerZone)
                            }
                        }
                    }
                    2 -> {
                        // TRANG PHẢI CÙNG: Người thân giám sát
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            GuardianScreen()
                        }
                    }
                }
            }
        }
    }
}