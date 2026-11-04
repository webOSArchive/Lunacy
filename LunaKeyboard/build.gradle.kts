import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * The keyboard ships with Lunacy, so it carries the shell's version: the same build number
 * (the repository's commit count, as AndroidLuna/build.gradle.kts counts it, kept here so the
 * keyboard still depends on nothing in Lunacy) and the same version name, the one in
 * gradle.properties at the top of the repository.
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
        // The newest Android it is built for (codepoet, 2026-11-04: an old target counts against
        // a sideloaded keyboard with Play Protect). minSdk keeps Android 5, which ignores a
        // target above its own level. What a new target changes for a keyboard, and what was
        // done about it, is in README.md ("Built for the newest Android").
        targetSdk = 35
        versionCode = buildNumber
        versionName = property("versionName") as String
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // One lint policy for both modules (../lint.xml): NewApi is fatal because minSdk 21 has
    // to hold, and the expired-targetSdk warning is deliberate - Lunacy is sideloaded.
    lint { lintConfig = rootProject.file("lint.xml") }
    // The key art and the Prelude fonts are read at their own pixel sizes and scaled by the
    // keyboard itself, so they must not go through Android's density folders.
    androidResources { noCompress += listOf("png", "ttf") }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
