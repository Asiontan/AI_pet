plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pet.pet.service"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-eventbus"))
    implementation(project(":pet:pet-float"))
    implementation(project(":pet:pet-behavior"))
    implementation(project(":pet:pet-render"))
    implementation(project(":algorithm:algorithm-rl"))
    implementation(project(":algorithm:algorithm-sentiment"))
    implementation(project(":algorithm:algorithm-cv"))
    implementation(project(":algorithm:algorithm-path"))
    implementation(project(":algorithm:algorithm-prediction"))
    
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
