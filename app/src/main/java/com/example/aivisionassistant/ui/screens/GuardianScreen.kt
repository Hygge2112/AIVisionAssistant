package com.example.aivisionassistant.ui.screens

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianScreen() {
    val context = LocalContext.current

    var inputCode by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }

    // Dữ liệu nạn nhân
    var victimStatus by remember { mutableStateOf("Chưa kết nối") }
    var victimAddress by remember { mutableStateOf("...") }
    var victimLat by remember { mutableStateOf(0.0) }
    var victimLng by remember { mutableStateOf(0.0) }

    // Công cụ báo động (Rung và Chuông)
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

    // Lắng nghe Firebase theo thời gian thực
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

                        // NẾU CÓ SOS -> BÁO ĐỘNG NGAY LẬP TỨC!
                        if (victimStatus == "SOS_ACTIVE") {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0)) // Rung liên tục
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(longArrayOf(0, 500, 500), 0)
                            }
                            if (!ringtone.isPlaying) ringtone.play()
                        } else {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (victimStatus == "SOS_ACTIVE") Color(0xFFFFEBEE) else MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // TIÊU ĐỀ
        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("CHẾ ĐỘ NGƯỜI THÂN", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))

        // Ô NHẬP MÃ 6 SỐ
        OutlinedTextField(
            value = inputCode,
            onValueChange = { if (it.length <= 6) inputCode = it },
            label = { Text("Nhập mã 6 số của người khiếm thị") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnected
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { isConnected = !isConnected },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isConnected) Color.Gray else MaterialTheme.colorScheme.primary)
        ) {
            Text(if (isConnected) "NGẮT KẾT NỐI" else "KẾT NỐI GIÁM SÁT", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(40.dp))

        // KHU VỰC HIỂN THỊ TRẠNG THÁI NẠN NHÂN
        if (isConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (victimStatus == "SOS_ACTIVE") Color(0xFFD32F2F) else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (victimStatus == "SOS_ACTIVE") {
                        Icon(Icons.Default.CrisisAlert, contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp))
                        Text("PHÁT HIỆN TÍN HIỆU SOS!", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        if (victimLat != 0.0) {
                            val victimPosition = LatLng(victimLat, victimLng)
                            val cameraPositionState = rememberCameraPositionState {
                                position = CameraPosition.fromLatLngZoom(victimPosition, 16f)
                            }

                            // Tự động di chuyển camera khi vị trí thay đổi
                            LaunchedEffect(victimLat, victimLng) {
                                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                                    LatLng(victimLat, victimLng), 16f
                                )
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth().height(250.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                GoogleMap(
                                    modifier = Modifier.fillMaxSize(),
                                    cameraPositionState = cameraPositionState
                                ) {
                                    Marker(
                                        state = MarkerState(position = victimPosition),
                                        title = "Vị trí người thân",
                                        snippet = victimAddress
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "📍 $victimAddress",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = Color.White
                            )
                        } else {
                            Text("Vị trí cứu hộ khẩn cấp:", color = Color.White)
                            Text(victimAddress, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        // NÚT MỞ GOOGLE MAPS
                        Button(
                            onClick = {
                                val gmmIntentUri = Uri.parse("geo:$victimLat,$victimLng?q=$victimLat,$victimLng($victimAddress)")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                mapIntent.setPackage("com.google.android.apps.maps")
                                context.startActivity(mapIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = Color.Red)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("DẪN ĐƯỜNG ĐẾN ĐÓ NGAY", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Trạng thái: AN TOÀN", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Đang theo dõi thiết bị: $inputCode", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}