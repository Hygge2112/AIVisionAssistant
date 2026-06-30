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
import com.google.firebase.auth.FirebaseAuth
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
    val auth = FirebaseAuth.getInstance()
    var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
    var showRegister by remember { mutableStateOf(false) }

    if (!isLoggedIn) {
        if (showRegister) {
            RegisterScreen(
                onRegisterSuccess = { isLoggedIn = true },
                onNavigateToLogin = { showRegister = false }
            )
        } else {
            LoginScreen(
                onLoginSuccess = { isLoggedIn = true },
                onNavigateToRegister = { showRegister = true }
            )
        }
    } else {
        MainAppContent(onSignOut = {
            auth.signOut()
            isLoggedIn = false
        })
    }
}

@Composable
fun MainAppContent(onSignOut: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 4 })
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

    LaunchedEffect(isDangerZone) {
        if (isDangerZone) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }
            )
        }
    ) { paddingValues ->

        // ĐÃ SỬA: Đưa CameraContent ra NGOÀI HorizontalPager để bảo vệ vòng đời camera đơn nhiệm không bị đơ
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            CameraContent(
                onPreviewViewCreated = { cameraPreviewView = it },
                onObjectDetected = { objName, distance, isDanger ->
                    detectedObject = objName
                    detectedDistance = distance
                    isDangerZone = isDanger
                }
            )

            // Các trang chức năng chỉ là lớp phủ trong suốt trượt lên trên Camera
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> {
                        // TRANG TRÁI CÙNG: SOS (Có nền background che camera để bảo mật thông tin)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            SosScreen()
                        }
                    }
                    1 -> {
                        // TRANG Ở GIỮA: Mắt thần AI (Không nền - Hiển thị xuyên thấu ra Camera sau)
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
                        // TRANG PHẢI CÙNG: Người thân giám sát (Có nền background)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            GuardianScreen()
                        }
                    }
                    3 -> {
                        // TRANG THỨ 4: Thông tin cá nhân
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            ProfileScreen(onSignOut = onSignOut)
                        }
                    }
                }
            }
        }
    }
}