import java.util.Properties

// Đọc local.properties an toàn — file này đã có trong .gitignore
val localProperties = Properties().also { props ->
    val file = rootProject.file("local.properties")
    if (file.exists()) props.load(file.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.aivisionassistant"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.aivisionassistant"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Truyền API Key sang AndroidManifest.xml (dành cho Google Maps)
        manifestPlaceholders["mapsApiKey"] = localProperties["MAPS_API_KEY"]?.toString() ?: ""

        // Đưa API Key vào BuildConfig để dùng trong code — KHÔNG bao giờ hardcode key trong .kt
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties["GEMINI_API_KEY"] ?: ""}\""
        )
        
        // Thêm WEB_CLIENT_ID cho Firebase Auth (Google Sign-In)
        buildConfigField(
            "String",
            "WEB_CLIENT_ID",
            "\"${localProperties["WEB_CLIENT_ID"] ?: ""}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true   // Bật để dùng BuildConfig.GEMINI_API_KEY trong code
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.firebase.database)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    
    // Thư viện Firebase Auth & Google Sign-In
    implementation("com.google.firebase:firebase-auth-ktx:23.0.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Thư viện OpenStreetMap
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Thư viện CameraX
    val camerax_version = "1.3.2"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // Thư viện xử lý quyền (Permissions) cho Jetpack Compose
    implementation("com.google.accompanist:accompanist-permissions:0.35.0-alpha")
    // Thư viện bổ sung bộ Icon đầy đủ cho Compose
    implementation("androidx.compose.material:material-icons-extended")
    // Thư viện Google Gemini AI bản mới nhất
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    // Thư viện Google ML Kit để nhận diện vật cản thời gian thực (Offline)
    implementation("com.google.mlkit:object-detection:17.0.2")
    // Thư viện phân loại chính xác hàng trăm đồ vật (Nâng cấp)
    implementation("com.google.mlkit:image-labeling:17.0.9")
    // Thư viện lấy tọa độ GPS của Google
    implementation("com.google.android.gms:play-services-location:21.0.1")
    // Thư viện cầu nối giúp Coroutine gọi được các dịch vụ của Google
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    // Thư viện quét vật thể tốc độ cao của TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    implementation("com.google.mlkit:text-recognition:16.0.1")
}