plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pet.pet.behavior"
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
    implementation(project(":core:core-eventbus"))
    implementation(project(":pet:pet-render"))
    implementation(project(":algorithm:algorithm-rl"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
