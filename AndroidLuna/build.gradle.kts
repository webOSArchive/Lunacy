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
        // The oldest target newer Android will install: Android 14 refuses below 23 and
        // Android 15 below 24. minSdk keeps Android 5, which ignores a target above its
        // own level. What 23 and 24 change on newer devices is in Docs/architecture.md.
        targetSdk = 24
        versionCode = buildNumber
        versionName = "0.3.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // One lint policy for both modules (../lint.xml): NewApi is fatal because minSdk 21 has
    // to hold, and the expired-targetSdk warning is deliberate - Lunacy is sideloaded.
    lint { lintConfig = rootProject.file("lint.xml") }
    // Everything in the APK is deflated except what is read through a file descriptor: the
    // sounds (Sounds.kt's openFd). Storing text and fonts uncompressed, as an earlier build
    // did, cost 21 MB of APK; AssetManager.open inflates a file as it is read, which the app
    // server streams anyway. (aapt leaves jpg, png and the audio formats stored by itself.)
    androidResources { noCompress += listOf("wav", "mp3") }
    buildTypes {
        // The debug APK is the one codepoet hands out, so it is the one R8 shrinks: without it
        // the whole Kotlin stdlib ships (2.3 MB of dex for 0.9 MB of Lunacy). Obfuscation
        // is off (proguard-rules.pro) so stack traces and chrome://inspect stay readable.
        debug { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
        release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    // Assets that can't be committed (the frameworks, Palm's other apps, the rootfs) are
    // populated by fetch-assets.sh into local-assets/, which is gitignored. The fonts and
    // wallpapers are committed under src/main/assets/luna with their NOTICE.
    sourceSets["main"].assets.srcDirs("src/main/assets", "local-assets")
    // Test apps users shouldn't get go in only when asked for: ./gradlew assembleDebug
    // -PtestApps. Releases leave them out (codepoet). test-apps/ is Lunacy's own (Notify
    // Test); local-test-apps/ is fetch-assets.sh's (Glimpse, the Enyo samples).
    if (project.hasProperty("testApps")) sourceSets["main"].assets.srcDirs("test-apps", "local-test-apps")
    // Node for JS services (fetch-assets.sh): 32-bit ARM, the Android 5 test devices' ABI.
    sourceSets["main"].jniLibs.srcDirs("local-jni")
    packaging { jniLibs.useLegacyPackaging = true }  // installed as files, so Node can run
}
