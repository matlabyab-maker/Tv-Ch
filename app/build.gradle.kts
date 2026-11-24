plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Shallow project layout: Kotlin and manifest live directly under app/src/main.
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main")
            manifest.srcFile("src/main/AndroidManifest.xml")
            res.srcDirs("res")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.tvb.player"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.tvb.player"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.5.1")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
