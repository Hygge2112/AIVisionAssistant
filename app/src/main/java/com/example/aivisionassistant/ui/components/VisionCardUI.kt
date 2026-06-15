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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun VisionInfoCard(detectedObject: String, detectedDistance: String, isDanger: Boolean) {

    val translatedObject = if (detectedObject == "Đang quét...") {
        "Đang quét..."
    } else {
        detectedObject.split(",").map { obj ->
            // ĐÃ FIX 1: Tự động viết hoa chữ cái đầu để từ điển của bạn "hiểu" được nhãn của YOLO (person -> Person)
            val formattedObj = obj.trim().replaceFirstChar { it.uppercase() }

            when(formattedObj) {
                // Người và các bộ phận
                "Person", "Man", "Woman", "Human", "Boy", "Girl", "Face", "Head", "Clothing",
                "Hand", "Arm", "Leg", "Sitting", "Standing", "Hair", "Smile", "Skin", "Selfie", "Portrait", "Glasses" -> "Người"

                // ĐÃ FIX 2: Bổ sung phương tiện giao thông đường phố cho YOLO
                "Car", "Auto" -> "Ô tô"
                "Motorcycle", "Motorbike" -> "Xe máy"
                "Bicycle", "Bike" -> "Xe đạp"
                "Bus" -> "Xe buýt"
                "Truck" -> "Xe tải"

                // Đồ đạc
                "Chair", "Couch", "Sofa" -> "Cái ghế"
                "Monitor", "Screen" -> "Màn hình"
                "Desk", "Table", "Dining table" -> "Cái bàn"
                "Television", "Tv" -> "Tivi"
                "Computer keyboard", "Keyboard" -> "Bàn phím"
                "Laptop", "Computer" -> "Máy tính"
                "Mobile phone", "Phone", "Smartphone", "Cell phone" -> "Điện thoại"
                "Coffee cup", "Cup", "Mug", "Glass" -> "Cái cốc"
                "Bottle", "Water bottle" -> "Chai nước"

                // Môi trường
                "Door" -> "Cánh cửa"
                "Wall" -> "Bức tường"
                "Room", "Interior design", "Building" -> "Căn phòng"
                "Plant", "Tree", "Flower", "Potted plant" -> "Cây cối"

                else -> formattedObj
            }
        }
            .distinct() // Xóa các chữ bị lặp lại
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

                    // ĐÃ FIX 3: Chia tỷ lệ không gian 1.5 (Bên trái chiếm nhiều chỗ hơn)
                    Column(modifier = Modifier.weight(1.5f)) {
                        // Hạ font xuống titleMedium để không bị tràn
                        Text("CẢNH BÁO KHẨN CẤP", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                        Text("Có vật cản ngay phía trước", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }

                    // ĐÃ FIX 4: Chia tỷ lệ không gian 1.0 (Bên phải nếu quá dài sẽ tự rớt xuống dòng, không ép lề trái nữa)
                    Text(
                        text = detectedDistance,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
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

        // ĐÃ FIX 5: Áp dụng chia tỷ lệ tương tự cho thẻ Thông tin bên dưới
        Column(modifier = Modifier.weight(1.5f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = distance,
            style = MaterialTheme.typography.titleMedium,
            color = iconTint,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}