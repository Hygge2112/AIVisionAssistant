package com.example.aivisionassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun VisionInfoCard(detectedObject: String, detectedDistance: String, isDanger: Boolean) {

    val translatedObject = if (detectedObject == "Đang quét...") {
        "Đang quét..."
    } else {
        detectedObject.split(",").map { obj ->
            when(obj.trim()) {
                // ĐÃ FIX: Gom toàn bộ các từ chỉ bộ phận, hành động vào chữ "Người"
                "Person", "Man", "Woman", "Human", "Boy", "Girl", "Face", "Head", "Clothing",
                "Hand", "Arm", "Leg", "Sitting", "Standing", "Hair", "Smile", "Skin", "Selfie", "Portrait", "Glasses" -> "Người"

                // Đồ đạc
                "Chair", "Couch", "Sofa" -> "Cái ghế"
                "Monitor", "Screen" -> "Màn hình"
                "Desk", "Table" -> "Cái bàn"
                "Television", "Tv" -> "Tivi"
                "Computer keyboard", "Keyboard" -> "Bàn phím"
                "Laptop", "Computer" -> "Máy tính"
                "Mobile phone", "Phone", "Smartphone" -> "Điện thoại"
                "Coffee cup", "Cup", "Mug", "Glass" -> "Cái cốc"
                "Bottle", "Water bottle" -> "Chai nước"

                // Môi trường
                "Door" -> "Cánh cửa"
                "Wall" -> "Bức tường"
                "Room", "Interior design", "Building" -> "Căn phòng"
                "Plant", "Tree", "Flower" -> "Cây cối"

                else -> obj.trim()
            }
        }
            .distinct() // Xóa các chữ "Người" bị lặp lại (VD: Nhận diện thấy Hand và Face thì chỉ ghi "Người" 1 lần)
            .take(3)
            .joinToString(", ")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isDanger) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Cảnh báo", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CẢNH BÁO KHẨN CẤP", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                        Text("Có vật cản ngay phía trước", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(detectedDistance, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineMedium)
                }
            }

            InfoRowItem(
                icon = Icons.Default.CenterFocusStrong,
                title = "Phát hiện: $translatedObject",
                description = if (isDanger) "Vật thể ở quá gần!" else "Những vật đang trong tầm nhìn",
                distance = detectedDistance,
                iconTint = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun InfoRowItem(icon: ImageVector, title: String, description: String, distance: String, iconTint: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(iconTint.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(distance, style = MaterialTheme.typography.headlineMedium, color = iconTint)
    }
}