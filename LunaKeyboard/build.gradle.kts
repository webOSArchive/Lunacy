plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * The keyboard ships with Lunacy, so it carries the shell's version: the same build number
 * (the repository's commit count, as AndroidLuna/build.gradle.kts counts it, kept here so the
 * keyboard still depends on nothing in Lunacy) and the same version name, changed by hand
 * alongside the shell's.
 */
val buildNumber: Int = runCatching {
    val git = ProcessBuilder("git", "rev-list", "--count", "HEAD").directory(rootDir).start()
    git.inputStream.bufferedReader().readText().trim().toInt()
}.getOrDefault(1)

android {
    namespace = "org.webosarchive.keyboard"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.webosarchive.keyboard"
        minSdk = 21
        // The oldest target newer Android will install: Android 14 refuses below 23 and
        // Android 15 below 24. minSdk keeps Android 5, which ignores a target above its
        // own level. What 23 and 24 change on newer devices is in Docs/architecture.md.
        targetSdk = 24
        versionCode = buildNumber
        versionName = "0.8.0"
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
