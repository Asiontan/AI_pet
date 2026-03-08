plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.pet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.pet"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true
    }

    // 避免将不兼容16KB页面大小的JNI库打进APK（如某些第三方库可能携带的TFLite .so）
    packaging {
        jniLibs {
            excludes += listOf(
                "**/libtensorflowlite_jni.so",
                "**/libimage_processing_util_jni.so"
            )
        }
    }
}

dependencies {
    // Core modules
    implementation(project(":core:core-common"))
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-eventbus"))
    
    // Pet feature modules
    implementation(project(":pet:pet-float"))
    implementation(project(":pet:pet-render"))
    implementation(project(":pet:pet-behavior"))
    implementation(project(":pet:pet-service"))
    
    // Algorithm modules
    implementation(project(":algorithm:algorithm-rl"))
    implementation(project(":algorithm:algorithm-sentiment"))
    implementation(project(":algorithm:algorithm-cv"))
    implementation(project(":algorithm:algorithm-path"))
    implementation(project(":algorithm:algorithm-prediction"))
    
    // AndroidX libraries
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
