plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.webosarchive.lunacy.spike"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.webosarchive.lunacy.spike"
        minSdk = 21
        targetSdk = 21
        versionCode = 1
        versionName = "0.0.1-spike"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += listOf("js", "css", "html", "json", "png") }
}
