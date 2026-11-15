import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * LunaCore: Lunacy's platform-neutral core, shared by the Android shell (AndroidLuna) and Luna DE
 * (DesktopLuna/host). Plain Kotlin on the JVM, no Android classes; what it needs from a platform
 * comes through the interfaces in org.webosarchive.lunacy.core (Docs/luna-de.md, "LunaCore").
 * Moved classes keep their packages, so AndroidLuna's code that uses them is unchanged.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("ru.vyarus.animalsniffer")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

/**
 * The API 21 gate. A JVM module compiles against JDK 17, where java.time, java.nio.file and
 * the rest build, and would fail only on Android 5. Every class LunaCore compiles is checked
 * against Android 5.0.1's API (Animal Sniffer with the androidscents signature), and the build
 * fails on anything newer, as AndroidLuna's NewApi lint does.
 */
animalsniffer {
    sourceSets = listOf(project.sourceSets["main"])
}

dependencies {
    signature("net.sf.androidscents.signature:android-api-level-21:5.0.1_r2@signature")
    // Android's own org.json (AOSP's, as published by Vaadin): on Android the platform's always
    // wins, so LunaCore compiles against the same code and the desktop runs it. The Maven
    // org.json orders keys differently, and db8 compares JSON strings (Docs/luna-de.md).
    compileOnly("com.vaadin.external.google:android-json:0.0.20131108.vaadin1")
    testImplementation("com.vaadin.external.google:android-json:0.0.20131108.vaadin1")
    testImplementation("junit:junit:4.13.2")
}
