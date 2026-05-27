package com.example.aivisionassistant.ui.theme
// Lưu ý: Thay đổi tên package phía trên cho khớp với dự án của bạn nếu cần

import androidx.compose.ui.graphics.Color

// 1. Màu chủ đạo (Primary) - Xanh lam đậm dùng cho Nút bấm, Icon active, Viền
val BluePrimary = Color(0xFF0062FF)
val BlueLight = Color(0xFFE8F0FE) // Màu nền xanh nhạt cho các item được chọn

// 2. Màu nền và Bề mặt (Background & Surface)
val BackgroundLight = Color(0xFFF5F7FA) // Màu xám rất nhạt làm nền app
val SurfaceWhite = Color(0xFFFFFFFF) // Màu trắng tinh cho các thẻ (Cards), Bottom Sheet

// 3. Màu chữ (Typography)
val TextPrimary = Color(0xFF202124) // Đen xám đậm cho Tiêu đề, văn bản chính
val TextSecondary = Color(0xFF5F6368) // Xám nhạt cho chú thích, subtitle

// 4. Màu Cảnh báo nguy hiểm (Alert/Danger) - Cho chức năng phát hiện chướng ngại vật
val DangerRed = Color(0xFFD93025) // Đỏ đậm cho Icon/Chữ cảnh báo
val DangerBackground = Color(0xFFFCE8E6) // Nền đỏ nhạt cho khung cảnh báo

// 5. Màu Thành công (Success) - Cho chức năng dịch thuật
val SuccessGreen = Color(0xFF1E8E3E)

// 6. Màu phụ trợ cho Camera
val CameraOverlay = Color(0x66000000) // Đen mờ phủ lên camera khi cần