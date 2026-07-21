package com.example.aivisionassistant.ui.screens

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aivisionassistant.utils.PairingManager
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Key dùng để lưu mã vào SharedPreferences
private const val PREFS_NAME = "guardian_prefs"
private const val KEY_SAVED_CODE = "saved_pairing_code"
private const val KEY_IS_CONNECTED = "is_connected"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pairingManager = remember { PairingManager(context) }

    // ── SharedPreferences: đọc mã đã lưu từ lần trước ──────────────────────
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val savedCode = remember { prefs.getString(KEY_SAVED_CODE, "") ?: "" }
    val savedIsConnected = remember { prefs.getBoolean(KEY_IS_CONNECTED, false) }

    var inputCode by remember { mutableStateOf(savedCode) }
    var isConnected by remember { mutableStateOf(savedIsConnected) }
    var hasSavedCode by remember { mutableStateOf(savedCode.length == 6) }

    // ── Dữ liệu nạn nhân ────────────────────────────────────────────────────
    var victimStatus by remember { mutableStateOf("Chưa kết nối") }
    var victimAddress by remember { mutableStateOf("...") }
    var victimLat by remember { mutableStateOf(0.0) }
    var victimLng by remember { mutableStateOf(0.0) }
    var isAcknowledged by remember { mutableStateOf(false) }  // người thân đã xác nhận chưa?
    var isAckLoading by remember { mutableStateOf(false) }    // đang gửi xác nhận lên Firebase

    // ── Công cụ báo động ─────────────────────────────────────────────────────
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    val ringtone = remember {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        RingtoneManager.getRingtone(context, uri)
    }

    // ── Hàm mở Google Maps Navigation đến vị trí nạn nhân ───────────────────
    fun openGoogleMapsNavigation() {
        val navUri = Uri.parse(
            "google.navigation:q=$victimLat,$victimLng&mode=d"
        )
        val mapIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        // Fallback nếu chưa cài Google Maps
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            val browserUri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1&destination=$victimLat,$victimLng&travelmode=driving"
            )
            context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    }

    // ── Lắng nghe Firebase theo thời gian thực ───────────────────────────────
    DisposableEffect(isConnected, inputCode) {
        var listener: ValueEventListener? = null
        val databaseRef = FirebaseDatabase.getInstance().getReference("pairings/$inputCode")

        if (isConnected && inputCode.length == 6) {
            listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        victimStatus = snapshot.child("status").getValue(String::class.java) ?: "STANDBY"
                        victimAddress = snapshot.child("address").getValue(String::class.java) ?: "Không rõ"
                        victimLat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                        victimLng = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                        isAcknowledged = snapshot.child("acknowledged").getValue(Boolean::class.java) ?: false

                        // NẾU CÓ SOS VÀ CHƯA XÁC NHẬN → BÁO ĐỘNG!
                        if (victimStatus == "SOS_ACTIVE" && !isAcknowledged) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(longArrayOf(0, 500, 500), 0)
                            }
                            if (!ringtone.isPlaying) ringtone.play()
                        } else {
                            // Đã xác nhận hoặc không có SOS → tắt báo động
                            vibrator.cancel()
                            if (ringtone.isPlaying) ringtone.stop()
                        }
                    } else {
                        victimStatus = "Mã không tồn tại!"
                        isConnected = false
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    victimStatus = "Lỗi kết nối mạng"
                }
            }
            databaseRef.addValueEventListener(listener)
        }

        onDispose {
            listener?.let { databaseRef.removeEventListener(it) }
            vibrator.cancel()
            if (ringtone.isPlaying) ringtone.stop()
        }
    }

    // ── Giao diện ──────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                when {
                    victimStatus == "SOS_ACTIVE" && !isAcknowledged -> Color(0xFFFFEBEE)
                    victimStatus == "SOS_ACTIVE" && isAcknowledged  -> Color(0xFFE8F5E9)
                    else -> MaterialTheme.colorScheme.background
                }
            )
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // ── Tiêu đề ────────────────────────────────────────────────────────────
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            "CHẾ ĐỘ NGƯỜI THÂN",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(20.dp))

        // ── Banner "Mã đã lưu" khi có mã cũ ──────────────────────────────────
        AnimatedVisibility(
            visible = hasSavedCode && !isConnected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF1565C0),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "Đã lưu mã kết nối",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1565C0),
                            fontSize = 14.sp
                        )
                        Text(
                            "Mã: $savedCode — nhấn kết nối lại ngay",
                            color = Color(0xFF1565C0).copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // ── Ô nhập mã 6 số ────────────────────────────────────────────────────
        OutlinedTextField(
            value = inputCode,
            onValueChange = { if (it.length <= 6) inputCode = it },
            label = { Text("Nhập mã 6 số của người khiếm thị") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnected
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Nút Kết nối / Ngắt kết nối ────────────────────────────────────────
        Button(
            onClick = {
                if (!isConnected) {
                    // Lưu mã vào SharedPreferences trước khi kết nối
                    if (inputCode.length == 6) {
                        prefs.edit().putString(KEY_SAVED_CODE, inputCode).apply()
                        hasSavedCode = true
                        isAcknowledged = false // reset trạng thái xác nhận
                    }
                }
                isConnected = !isConnected
                prefs.edit().putBoolean(KEY_IS_CONNECTED, isConnected).apply()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) Color(0xFF757575) else MaterialTheme.colorScheme.primary
            ),
            enabled = inputCode.length == 6
        ) {
            Text(
                if (isConnected) "NGẮT KẾT NỐI" else "KẾT NỐI GIÁM SÁT",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Khu vực hiển thị trạng thái khi đã kết nối ───────────────────────
        if (isConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        victimStatus == "SOS_ACTIVE" && !isAcknowledged -> Color(0xFFD32F2F)
                        victimStatus == "SOS_ACTIVE" && isAcknowledged  -> Color(0xFF388E3C)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (victimStatus == "SOS_ACTIVE") {
                        // ── TRẠNG THÁI: CÓ SOS ─────────────────────────────────
                        Icon(
                            Icons.Default.CrisisAlert,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(60.dp)
                        )
                        Text(
                            if (isAcknowledged) "ĐÃ XÁC NHẬN CỨU HỘ ✓"
                            else "PHÁT HIỆN TÍN HIỆU SOS!",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (victimLat != 0.0) {
                            Text("Vị trí cứu hộ:", color = Color.White, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "📍 $victimAddress",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ── Nút dẫn đường (dự phòng) ───────────────────────────
                        OutlinedButton(
                            onClick = { openGoogleMapsNavigation() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DẪN ĐƯỜNG ĐẾN ĐÓ NGAY", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ── Nút XÁC NHẬN ĐÃ NHẬN CỨU HỘ ──────────────────────
                        if (!isAcknowledged) {
                            Button(
                                onClick = {
                                    isAckLoading = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        pairingManager.acknowledgeSOSSignal(inputCode)
                                        launch(Dispatchers.Main) {
                                            isAckLoading = false
                                            isAcknowledged = true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                enabled = !isAckLoading
                            ) {
                                if (isAckLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFFD32F2F),
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFFD32F2F)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "ĐÃ NHẬN CỨU HỘ — XÁC NHẬN",
                                        color = Color(0xFFD32F2F),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Nhấn để thông báo người khiếm thị biết bạn đã nhận được",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            // Đã xác nhận rồi
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Đã gửi xác nhận đến người khiếm thị",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                    } else {
                        // ── TRẠNG THÁI: AN TOÀN ────────────────────────────────
                        Text(
                            "✅  AN TOÀN",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Đang theo dõi mã: $inputCode",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}