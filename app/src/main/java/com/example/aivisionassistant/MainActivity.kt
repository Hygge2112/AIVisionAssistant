package com.example.aivisionassistant

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.aivisionassistant.ui.theme.* // Đảm bảo import Theme của bạn
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 1. Bọc toàn bộ app bằng Theme đã thiết lập
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AIVisionApp() {
    // Quản lý trạng thái tab đang được chọn (Mặc định chọn tab "Quét" ở vị trí số 2)
    var selectedTabIndex by remember { mutableIntStateOf(2) }

    // Cấu trúc chuẩn của một màn hình
    Scaffold(
        // ĐÃ FIX: Thêm windowInsetsPadding để tự động đẩy UI khỏi thanh trạng thái và camera đục lỗ
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
        // Khu vực nội dung chính nằm giữa TopBar và BottomBar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Tránh bị đè dưới BottomNav
        ) {
            CameraContent()

            // Lớp phủ tối mờ để text/thẻ thông tin nổi bật hơn
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x33000000))
            )

            // TODO: Tại đây sẽ hiển thị các Card (Thẻ thông tin màu trắng) dựa theo selectedTabIndex
            // Ví dụ: if (selectedTabIndex == 2) { ObjectDetectionCard() }
        }
    }
}

@Composable
fun TopBarUI() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            // ĐÃ FIX: Tăng padding để cân đối khoảng trống sau khi đẩy insets
            .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = "Logo",
            tint = Color(0xFF0062FF)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "TRỢ LÝ THỊ GIÁC",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF0062FF) // BluePrimary
        )
    }
}

@Composable
fun BottomNavigationBarUI(selectedIndex: Int, onItemSelected: (Int) -> Unit) {
    // Danh sách 5 chức năng
    val tabs = listOf(
        Pair("SOS", Icons.Default.Warning),
        Pair("Giọng nói", Icons.Default.Mic),
        Pair("Quét", Icons.Default.Search),
        Pair("Dịch", Icons.Default.Edit),
        Pair("Âm thanh", Icons.Default.Notifications)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = selectedIndex == index

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onItemSelected(index) }
                    .background(if (isSelected) Color(0xFFE8F0FE) else Color.Transparent) // BlueLight
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = tab.second,
                    contentDescription = tab.first,
                    tint = if (isSelected) Color(0xFF0062FF) else Color.Gray // BluePrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tab.first,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF0062FF) else Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraContent() {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        CameraPreviewScreen()
    } else {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Đang chờ quyền Camera...", color = Color.White)
        }
    }
}

@Composable
fun CameraPreviewScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, executor)

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}