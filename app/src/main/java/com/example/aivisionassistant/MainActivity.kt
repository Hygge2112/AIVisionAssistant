package com.example.aivisionassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aivisionassistant.ui.components.*
import com.example.aivisionassistant.ui.screens.*
import com.example.aivisionassistant.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIVisionAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AIVisionApp()
                }
            }
        }
    }
}

@Composable
fun AIVisionApp() {
    var selectedTabIndex by remember { mutableIntStateOf(2) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        topBar = { TopBarUI() },
        bottomBar = {
            BottomNavigationBarUI(
                selectedIndex = selectedTabIndex,
                onItemSelected = { selectedTabIndex = it }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Lớp Camera ở dưới cùng
            CameraContent()

            // Lớp phủ tối mờ Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CameraOverlay)
            )

            // Lớp hiển thị UI theo Tab
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                when (selectedTabIndex) {
                    1 -> VoiceRecognitionScreen()
                    2 -> VisionInfoCard()
                }
            }
        }
    }
}