package com.example.aivisionassistant.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import java.util.Locale

class LocationHelper(private val context: Context) {

    // Khởi tạo công cụ dò vị trí của Google
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationAndAddress(): Pair<Location?, String> {
        return try {
            // Lấy tọa độ GPS mới nhất
            val location = fusedLocationClient.lastLocation.await()

            var addressText = "Đang tìm địa chỉ..."

            if (location != null) {
                // Sử dụng Geocoder để dịch Tọa độ -> Tên đường bằng tiếng Việt
                val geocoder = Geocoder(context, Locale("vi", "VN"))

                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

                if (!addresses.isNullOrEmpty()) {
                    // Lấy dòng địa chỉ chi tiết đầu tiên
                    addressText = addresses[0].getAddressLine(0) ?: "Không rõ địa chỉ"
                }
            } else {
                addressText = "Không thể lấy GPS. Hãy bật Vị trí trên điện thoại."
            }

            // Trả về cả cục Tọa độ (để gửi lên bản đồ) và Dòng chữ địa chỉ (để hiển thị)
            Pair(location, addressText)

        } catch (e: Exception) {
            e.printStackTrace()
            Pair(null, "Lỗi lấy vị trí: ${e.localizedMessage}")
        }
    }
}