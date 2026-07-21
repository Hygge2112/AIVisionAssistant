package com.example.aivisionassistant.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build

object DeviceHelper {

    /**
     * Lấy tên phần cứng của thiết bị (ví dụ: 'Samsung Galaxy S23', 'Google Pixel 7')
     */
    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER ?: ""
        val model = Build.MODEL ?: ""
        
        if (model.lowercase().startsWith(manufacturer.lowercase())) {
            return capitalize(model)
        }
        return capitalize(manufacturer) + " " + model
    }

    private fun capitalize(s: String): String {
        if (s.isEmpty()) return ""
        val first = s[0]
        if (first.isUpperCase()) return s
        return first.uppercaseChar() + s.substring(1)
    }

    /**
     * Lấy phần trăm pin hiện tại (0 - 100)
     */
    fun getBatteryPercentage(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            -1 // Lỗi không lấy được pin
        }
    }

    /**
     * Lấy trạng thái mạng hiện hành (WiFi, 4G, v.v.)
     */
    fun getNetworkType(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Offline"
            
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "4G/5G"
                else -> "Khác"
            }
        } catch (e: Exception) {
            "Không rõ"
        }
    }
}
