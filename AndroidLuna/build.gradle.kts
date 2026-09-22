plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * The build number counts itself: it is the number of commits behind this build. Every
 * commit is a new build number, and any checkout of the same commit gets the same one.
 * Without git (a source drop) it is 1. The version name is codepoet's to change, by hand, below.
 */
val buildNumber: Int = runCatching {
    val git = ProcessBuilder("git", "rev-list", "--count", "HEAD").directory(rootDir).start()
    git.inputStream.bufferedReader().readText().trim().toInt()
}.getOrDefault(1)

android {
    namespace = "org.webosarchive.lunacy"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.webosarchive.lunacy"
        minSdk = 21
        targetSdk = 21
        versionCode = buildNumber
        versionName = "0.2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // One lint policy for both modules (../lint.xml): NewApi is fatal because minSdk 21 has
    // to hold, and the expired-targetSdk warning is deliberate - Lunacy is sideloaded.
    lint { lintConfig = rootProject.file("lint.xml") }
    androidResources { noCompress += listOf("js", "css", "html", "json", "png", "ttf", "jpg") }
    // Assets that can't be committed (frameworks and apps under review, HP fonts and
    // wallpapers) are populated by fetch-assets.sh into local-assets/, which is gitignored.
    sourceSets["main"].assets.srcDirs("src/main/assets", "local-assets")
    // Node for JS services (fetch-assets.sh): 32-bit ARM, the Android 5 test devices' ABI.
    sourceSets["main"].jniLibs.srcDirs("local-jni")
    packaging { jniLibs.useLegacyPackaging = true }  // installed as files, so Node can run
}
