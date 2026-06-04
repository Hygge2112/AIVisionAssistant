package com.example.aivisionassistant.ui.screens

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.aivisionassistant.ml.VisionAnalyzer // Import bộ phân tích
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraContent(
    onPreviewViewCreated: (PreviewView) -> Unit,
    // ĐÃ FIX: Nhận thêm biến Boolean (isDanger) để truyền trạng thái nguy hiểm ra ngoài
    onObjectDetected: (String, String, Boolean) -> Unit
) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    )

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    if (permissionsState.allPermissionsGranted) {
        CameraPreviewScreen(onPreviewViewCreated, onObjectDetected)
    } else {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Đang chờ quyền Camera và Micro...\nHãy cấp quyền để tiếp tục.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.surface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CameraPreviewScreen(
    onPreviewViewCreated: (PreviewView) -> Unit,
    // ĐÃ FIX: Nhận thêm biến Boolean (isDanger)
    onObjectDetected: (String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            onPreviewViewCreated(previewView)

            val executor = ContextCompat.getMainExecutor(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // 1. Luồng hiển thị (Preview)
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // 2. Luồng quét vật cản (ImageAnalysis)
                val imageAnalyzer = ImageAnalysis.Builder()
                    // Chỉ lấy frame mới nhất để máy thật không bị nóng và lag
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        // ĐÃ FIX: Hứng 3 biến (label, distance, isDanger) từ VisionAnalyzer
                        it.setAnalyzer(executor, VisionAnalyzer { label, distance, isDanger ->
                            // Gửi kết quả phát hiện được ra ngoài
                            onObjectDetected(label, distance, isDanger)
                        })
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    // Gắn cả mắt nhìn và não quét vào Camera
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalyzer)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, executor)
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}