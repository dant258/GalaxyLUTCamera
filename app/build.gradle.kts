plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.galaxylut"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.galaxylut"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-alpha"
    }
}
