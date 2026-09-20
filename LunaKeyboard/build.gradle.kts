plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.webosarchive.keyboard"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.webosarchive.keyboard"
        minSdk = 21
        targetSdk = 21
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // One lint policy for both modules (../lint.xml): NewApi is fatal because minSdk 21 has
    // to hold, and the expired-targetSdk warning is deliberate - Lunacy is sideloaded.
    lint { lintConfig = rootProject.file("lint.xml") }
    // The key art and the Prelude fonts are read at their own pixel sizes and scaled by the
    // keyboard itself, so they must not go through Android's density folders.
    androidResources { noCompress += listOf("png", "ttf") }
}
