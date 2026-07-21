package com.example.aivisionassistant.utils

import android.content.Context
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class PairingManager(private val context: Context) {

    // Khởi tạo kết nối với máy chủ Firebase Realtime Database
    private val database = FirebaseDatabase.getInstance().reference

    /**
     * Hàm 1: Lấy hoặc tạo mã kết nối 6 số CỐ ĐỊNH gắn với tài khoản người dùng (uid).
     *
     * Logic:
     * - Kiểm tra Firebase tại: users/{uid}/pairingCode
     * - NẾU đã tồn tại → trả về mã cũ (người thân giữ nguyên mã, không cần nhập lại)
     * - NẾU chưa có → sinh mã mới, lưu vào Firebase, trả về mã mới
     *
     * Đảm bảo mỗi tài khoản chỉ có MỘT mã duy nhất, không đổi theo phiên.
     */
    suspend fun getOrCreatePairingCode(uid: String): String {
        return try {
            val userRef = database.child("users").child(uid)
            val snapshot = userRef.child("pairingCode").get().await()

            if (snapshot.exists()) {
                // Mã đã tồn tại → tái sử dụng
                snapshot.getValue(String::class.java) ?: generateAndSaveCode(uid)
            } else {
                // Chưa có mã → sinh mới và lưu
                generateAndSaveCode(uid)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: sinh mã tạm thời nếu mạng lỗi (không lưu được lên server)
            Random.nextInt(100000, 999999).toString()
        }
    }

    /**
     * Hàm nội bộ: Sinh mã mới, lưu vào Firebase và trả về mã đó
     */
    private suspend fun generateAndSaveCode(uid: String): String {
        val newCode = Random.nextInt(100000, 999999).toString()
        database.child("users").child(uid).child("pairingCode").setValue(newCode).await()
        return newCode
    }

    /**
     * Hàm 2: Tạo/cập nhật "phòng kết nối" trên Firebase theo mã 6 số đó.
     * Sử dụng setValue chỉ khi phòng chưa tồn tại (dùng updateChildren nếu đã có)
     * để tránh mất dữ liệu cũ khi mở lại app.
     */
    suspend fun createPairingSession(code: String): Boolean {
        return try {
            val ref = database.child("pairings").child(code)
            val snapshot = ref.get().await()

            if (!snapshot.exists()) {
                // Phòng chưa tồn tại → tạo mới với trạng thái mặc định
                val initialData = mapOf(
                    "status" to "STANDBY",
                    "latitude" to 0.0,
                    "longitude" to 0.0,
                    "address" to "",
                    "timestamp" to System.currentTimeMillis(),
                    "name" to DeviceHelper.getDeviceName(),
                    "battery" to DeviceHelper.getBatteryPercentage(context),
                    "network" to DeviceHelper.getNetworkType(context)
                )
                ref.setValue(initialData).await()
            } else {
                // Phòng đã tồn tại → reset về STANDBY và cập nhật lại thông tin thiết bị
                val updateData = mapOf(
                    "status" to "STANDBY",
                    "name" to DeviceHelper.getDeviceName(),
                    "battery" to DeviceHelper.getBatteryPercentage(context),
                    "network" to DeviceHelper.getNetworkType(context)
                )
                ref.updateChildren(updateData).await()
            }
            true
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
                "timestamp" to System.currentTimeMillis(),
                "name" to DeviceHelper.getDeviceName(),
                "battery" to DeviceHelper.getBatteryPercentage(context),
                "network" to DeviceHelper.getNetworkType(context)
            )

            // Cập nhật dữ liệu vào đúng cái mã ghép đôi đó
            database.child("pairings").child(code).updateChildren(sosData).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Hàm 4: Người thân xác nhận đã nhận được tín hiệu SOS.
     * Ghi acknowledged=true lên Firebase → SosScreen sẽ nhận được thông báo realtime.
     */
    suspend fun acknowledgeSOSSignal(code: String): Boolean {
        return try {
            val ackData = mapOf(
                "acknowledged" to true,
                "acknowledgedAt" to System.currentTimeMillis()
            )
            database.child("pairings").child(code).updateChildren(ackData).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Hàm 5: Reset trạng thái xác nhận trước khi gửi SOS mới
     * (tránh SosScreen nghĩ đã được xác nhận ngay khi vừa bắt đầu gửi)
     */
    suspend fun resetAcknowledge(code: String) {
        try {
            database.child("pairings").child(code).child("acknowledged").setValue(false).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Hàm 6: Tăng bộ đếm số lần đã gửi SOS cho thống kê cá nhân
     */
    suspend fun incrementSosCount(uid: String) {
        try {
            val ref = database.child("users").child(uid).child("sosSentCount")
            val snapshot = ref.get().await()
            val currentCount = snapshot.getValue(Int::class.java) ?: 0
            ref.setValue(currentCount + 1).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
