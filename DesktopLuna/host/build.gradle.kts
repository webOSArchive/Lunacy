import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Luna DE's host: LunaCore on a desktop JVM, serving apps, the bus and the page's native
 * interface to DesktopLuna's Electron shell over a local socket (Docs/luna-de.md).
 * `./gradlew :DesktopLuna:host:installDist` puts a runnable copy in build/install/host.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

application {
    mainClass.set("org.webosarchive.lunacy.desktop.MainKt")
    applicationName = "lunade-host"
}

dependencies {
    implementation(project(":LunaCore"))
    // The same org.json LunaCore compiles against, which Android's platform provides there.
    implementation("com.vaadin.external.google:android-json:0.0.20131108.vaadin1")
}
