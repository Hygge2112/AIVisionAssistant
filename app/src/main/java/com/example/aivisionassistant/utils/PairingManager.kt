package com.example.aivisionassistant.utils

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class PairingManager {

    // Khởi tạo kết nối với máy chủ Firebase Realtime Database
    private val database = FirebaseDatabase.getInstance().reference

    /**
     * Hàm 1: Sinh ra một mã code 6 chữ số ngẫu nhiên cho người khiếm thị
     * Ví dụ: "842910"
     */
    fun generatePairingCode(): String {
        return Random.nextInt(100000, 999999).toString()
    }

    /**
     * Hàm 2: Tạo một "phòng kết nối" trên Firebase bằng mã 6 số đó
     * Khi người nhà nhập đúng số này, họ sẽ vào được chung "phòng"
     */
    suspend fun createPairingSession(code: String): Boolean {
        return try {
            // Tạo cấu trúc dữ liệu mặc định ban đầu (Chưa có SOS)
            val initialData = mapOf(
                "status" to "STANDBY",
                "latitude" to 0.0,
                "longitude" to 0.0,
                "address" to "",
                "timestamp" to System.currentTimeMillis()
            )

            // Lưu lên Firebase tại đường dẫn: pairings/{code}
            database.child("pairings").child(code).setValue(initialData).await()
            true // Trả về true nếu lưu thành công
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Hàm 3: Bắn tín hiệu SOS (Địa chỉ + Tọa độ) lên Firebase
     */
    suspend fun sendSosSignal(code: String, lat: Double, lng: Double, address: String): Boolean {
        return try {
            val sosData = mapOf(
                "status" to "SOS_ACTIVE",
                "latitude" to lat,
                "longitude" to lng,
                "address" to address,
                "timestamp" to System.currentTimeMillis()
            )

            // Cập nhật dữ liệu vào đúng cái mã ghép đôi đó
            database.child("pairings").child(code).updateChildren(sosData).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}