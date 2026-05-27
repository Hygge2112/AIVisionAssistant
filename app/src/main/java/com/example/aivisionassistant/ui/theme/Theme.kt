package com.example.aivisionassistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = SurfaceWhite,
    primaryContainer = BlueLight,
    onPrimaryContainer = BluePrimary,

    background = BackgroundLight,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,

    error = DangerRed,
    onError = SurfaceWhite,
    errorContainer = DangerBackground,
    onErrorContainer = DangerRed
)

@Composable
fun AIVisionAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Theo yêu cầu của bạn là nền trắng, chúng ta sẽ ép buộc dùng LightColorScheme
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Chỉnh màu thanh trạng thái (Pin, Sóng) thành màu nền app
            window.statusBarColor = colorScheme.background.toArgb()
            // Chỉnh các icon trên thanh trạng thái thành màu tối để dễ nhìn trên nền trắng
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}