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
import androidx.compose.runtime.DisposableEffect
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
import com.example.aivisionassistant.ml.VisionAnalyzer
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.concurrent.Executors // ĐÃ THÊM: Thư viện quản lý Đa luồng

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraContent(
    onPreviewViewCreated: (PreviewView) -> Unit,
    onObjectDetected: (String, String, Boolean) -> Unit
) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
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
                text = "Đang chờ quyền Camera, Micro và Vị trí...\nApp cần quyền Vị trí để gửi SOS khẩn cấp.",
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
    onObjectDetected: (String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // ĐÃ FIX 1: Tạo một "Nhà máy" ngầm riêng biệt chỉ để chạy AI
    val aiExecutor = remember { Executors.newSingleThreadExecutor() }

    // Tự động dọn dẹp bộ nhớ luồng ngầm khi người dùng thoát màn hình
    DisposableEffect(Unit) {
        onDispose {
            aiExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            onPreviewViewCreated(previewView)

            // Luồng chính (Chỉ dùng để vẽ Camera lên màn hình)
            val mainExecutor = ContextCompat.getMainExecutor(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        // ĐÃ FIX 2: Giao việc phân tích ảnh cho Luồng ngầm (aiExecutor) xử lý!
                        it.setAnalyzer(aiExecutor, VisionAnalyzer(ctx) { label, distance, isDanger ->
                            onObjectDetected(label, distance, isDanger)
                        })
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalyzer)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, mainExecutor)
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}