package com.example.aivisionassistant.ui.screens

import android.content.Context
import android.location.Location
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aivisionassistant.utils.LocationHelper
import com.example.aivisionassistant.utils.PairingManager // ĐÃ THÊM: Import bộ quản lý kết nối Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SosScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val locationHelper = remember { LocationHelper(context) }
    val pairingManager = remember { PairingManager() } // ĐÃ THÊM: Khởi tạo PairingManager

    // ĐÃ THÊM: Sinh ra mã 6 số duy nhất cho phiên làm việc này
    val pairingCode = remember { pairingManager.generatePairingCode() }

    var address by remember { mutableStateOf("Đang định vị trí của bạn...") }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var sosState by remember { mutableStateOf("STANDBY") } // STANDBY, SENDING, SENT
    var isPairingCreated by remember { mutableStateOf(false) }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // Tự động quét vị trí và đẩy phòng lên Firebase ngay khi mở màn hình
    LaunchedEffect(Unit) {
        // 1. Lấy vị trí và địa chỉ từ GPS phần cứng
        val result = locationHelper.getCurrentLocationAndAddress()
        currentLocation = result.first
        address = result.second

        // 2. Khởi tạo dữ liệu phòng chờ trên Firebase bằng luồng IO IO-Thread
        coroutineScope.launch(Dispatchers.IO) {
            val success = pairingManager.createPairingSession(pairingCode)
            isPairingCreated = success
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "sosPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (sosState == "SENDING") 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween // Phân bổ khoảng cách đều đặn, không bị tràn màn hình
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. ĐÃ THÊM: THẢ THẺ HIỂN THỊ MÃ SỐ KẾT NỐI LÊN TRÊN CÙNG
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MÃ KẾT NỐI NGƯỜI THÂN",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = pairingCode,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        letterSpacing = 6.sp // Tạo khoảng cách giữa các con số cho dễ đọc
                    )
                    Text(
                        text = if (isPairingCreated) "Máy chủ đám mây đã sẵn sàng liên kết" else "Đang đồng bộ với máy chủ đám mây...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // 2. Thẻ hiển thị địa chỉ hiện tại từ GPS
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Vị trí", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "VỊ TRÍ CỦA BẠN",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 3. NÚT SOS CỨU HỘ ĐỔI MÀU HOẠT HÌNH
        val buttonColor by animateColorAsState(
            targetValue = when (sosState) {
                "STANDBY" -> Color(0xFFD32F2F)
                "SENDING" -> Color(0xFFFF9800)
                else -> Color(0xFF4CAF50)
            }, label = "btnColor"
        )

        Box(
            modifier = Modifier
                .size(220.dp) // Co lại một chút thành 220dp để nhường không gian cho thẻ Mã Số
                .scale(scale)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable(enabled = sosState == "STANDBY") {
                    sosState = "SENDING"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(1000)
                    }

                    // ĐÃ FIX: Bắn dữ liệu GPS thật lên Firebase Realtime Database
                    coroutineScope.launch(Dispatchers.IO) {
                        val lat = currentLocation?.latitude ?: 0.0
                        val lng = currentLocation?.longitude ?: 0.0

                        // Gọi hàm bắn tín hiệu mạng sang server đám mây
                        val isSent = pairingManager.sendSosSignal(pairingCode, lat, lng, address)

                        // Quay lại Main Thread để cập nhật giao diện
                        launch(Dispatchers.Main) {
                            if (isSent) {
                                sosState = "SENT"
                            } else {
                                sosState = "STANDBY"
                                address = "Lỗi kết nối mạng đám mây! Vui lòng thử lại."
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (sosState) {
                        "STANDBY" -> "GỬI\nCỨU HỘ"
                        "SENDING" -> "ĐANG GỬI..."
                        else -> "ĐÃ GỬI!"
                    },
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 4. Dòng chữ hướng dẫn dưới cùng màn hình
        Text(
            text = if (sosState == "STANDBY") "Hãy đọc mã số trên cho người thân nhập vào ứng dụng kết nối giám sát." else "Tín hiệu cứu hộ khẩn cấp đang được truyền đi liên tục!",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}