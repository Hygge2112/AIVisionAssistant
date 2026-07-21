package com.example.aivisionassistant.ui.screens

import android.content.Context
import android.location.Location
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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
import com.example.aivisionassistant.utils.PairingManager
import com.google.firebase.auth.FirebaseAuth
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SosScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val locationHelper = remember { LocationHelper(context) }
    val pairingManager = remember { PairingManager(context) }

    // Mã kết nối cố định theo tài khoản — rỗng lúc đầu, sẽ được điền sau khi tải từ Firebase
    var pairingCode by remember { mutableStateOf("") }
    var isCodeLoading by remember { mutableStateOf(true) } // true = đang tải mã từ Firebase

    var address by remember { mutableStateOf("Đang định vị trí của bạn...") }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var sosState by remember { mutableStateOf("STANDBY") } // STANDBY, SENDING, SENT
    var isPairingCreated by remember { mutableStateOf(false) }
    // Trạng thái xác nhận: người thân đã nhận tín hiệu SOS chưa?
    var isAcknowledged by remember { mutableStateOf(false) }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // ── Text-to-Speech: khởi tạo và tự giải phóng khi rời màn hình ─────────────
    // Dùng var riêng biệt trước để tránh lỗi forward reference trong lambda
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var ttsEngine: TextToSpeech? = null   // khai báo trước
        ttsEngine = TextToSpeech(context) { status ->
            // Callback này chạy bất đồng bộ → ttsEngine đã được gán xong
            if (status == TextToSpeech.SUCCESS) {
                val result = ttsEngine?.setLanguage(Locale("vi", "VN"))
                when (result) {
                    TextToSpeech.LANG_MISSING_DATA -> {
                        // Giọng Việt chưa được cài → tự động mở dialog tải về
                        val installIntent = Intent(
                            TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(installIntent)
                        // Trong khi chờ tải: tạm dùng tiếng Anh
                        ttsEngine?.setLanguage(Locale.US)
                    }
                    TextToSpeech.LANG_NOT_SUPPORTED -> {
                        // Ngôn ngữ không hỗ trợ → fallback tiếng Anh
                        ttsEngine?.setLanguage(Locale.US)
                    }
                    // else: đã cài đặt tiếng Việt thành công ✓
                }
            }
        }
        tts = ttsEngine
        onDispose {
            ttsEngine?.stop()
            ttsEngine?.shutdown()
        }
    }

    // Tải mã cố định từ Firebase và khởi tạo phòng kết nối khi mở màn hình
    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        // Chạy song song: lấy vị trí + lấy mã kết nối từ Firebase
        withContext(Dispatchers.IO) {
            // 1. Lấy vị trí GPS
            val result = locationHelper.getCurrentLocationAndAddress()
            withContext(Dispatchers.Main) {
                currentLocation = result.first
                address = result.second
            }

            // 2. Lấy hoặc tạo mã kết nối cố định gắn với uid
            if (uid != null) {
                val code = pairingManager.getOrCreatePairingCode(uid)
                withContext(Dispatchers.Main) {
                    pairingCode = code
                    isCodeLoading = false
                }

                // 3. Khởi tạo phòng kết nối trên Firebase (chỉ tạo mới nếu chưa có)
                val success = pairingManager.createPairingSession(code)
                withContext(Dispatchers.Main) {
                    isPairingCreated = success
                }
            } else {
                withContext(Dispatchers.Main) {
                    isCodeLoading = false
                    address = "Lỗi: Chưa đăng nhập tài khoản!"
                }
            }
        }
    }

    // ── Lắng nghe Firebase: người thân có xác nhận nhận cứu hộ không? ────────
    // Chỉ bật listener khi đã gửi SOS (sosState == "SENT") và có mã hợp lệ
    DisposableEffect(sosState, pairingCode) {
        var ackListener: ValueEventListener? = null

        if (sosState == "SENT" && pairingCode.isNotEmpty()) {
            val ackRef = FirebaseDatabase.getInstance()
                .getReference("pairings/$pairingCode/acknowledged")

            ackListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val acked = snapshot.getValue(Boolean::class.java) ?: false
                    isAcknowledged = acked
                }
                override fun onCancelled(error: DatabaseError) { /* bỏ qua */ }
            }
            ackRef.addValueEventListener(ackListener)
        }

        onDispose {
            if (pairingCode.isNotEmpty()) {
                val ackRef = FirebaseDatabase.getInstance()
                    .getReference("pairings/$pairingCode/acknowledged")
                ackListener?.let { ackRef.removeEventListener(it) }
            }
        }
    }

    // ── Đọc thông báo bằng giọng nói khi người thân xác nhận ────────────────
    LaunchedEffect(isAcknowledged) {
        if (isAcknowledged) {
            val message = "Đã có người thân xác nhận. Họ đang trên đường đến chỗ bạn. Hãy giữ bình tĩnh."
            // Đọc lần 1 ngay lập tức
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "ack_1")
            // Đọc lần 2 sau 4 giây để đảm bảo người khiếm thị nghe rõ
            delay(4000)
            tts?.speak(message, TextToSpeech.QUEUE_ADD, null, "ack_2")
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
                    if (isCodeLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(38.dp).padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Text(
                            text = pairingCode.chunked(3).joinToString(" "), // Định dạng "526 336" cho dễ đọc
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            letterSpacing = 6.sp
                        )
                    }
                    Text(
                        text = when {
                            isCodeLoading -> "Đang tải mã định danh của bạn..."
                            isPairingCreated -> "Mã cố định — người thân chỉ cần nhập 1 lần"
                            else -> "Đang đồng bộ với máy chủ đám mây..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // 1b. Banner xác nhận — hiện ra khi người thân đã nhấn ĐÃ NHẬN
            AnimatedVisibility(
                visible = isAcknowledged && sosState == "SENT",
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(animationSpec = tween(400)),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Người thân đã nhận được!",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                            Text(
                                "Họ đang trên đường đến chỗ bạn.",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp
                            )
                        }
                    }
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
                    isAcknowledged = false // Reset xác nhận trước khi gửi mới

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(1000)
                    }

                    coroutineScope.launch(Dispatchers.IO) {
                        val lat = currentLocation?.latitude ?: 0.0
                        val lng = currentLocation?.longitude ?: 0.0

                        // Reset acknowledged trên Firebase trước khi gửi SOS mới
                        pairingManager.resetAcknowledge(pairingCode)

                        val isSent = pairingManager.sendSosSignal(pairingCode, lat, lng, address)
                        
                        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
                        if (isSent && currentUserUid != null) {
                            pairingManager.incrementSosCount(currentUserUid)
                        }

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
            text = when {
                sosState == "STANDBY" -> "Hãy đọc mã số trên cho người thân nhập vào ứng dụng kết nối giám sát."
                isAcknowledged       -> "Người thân đã xác nhận và đang đến. Hãy giữ bình tĩnh!"
                else                 -> "Tín hiệu cứu hộ đang được truyền đi. Chờ người thân xác nhận..."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (isAcknowledged) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
            fontWeight = if (isAcknowledged) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}