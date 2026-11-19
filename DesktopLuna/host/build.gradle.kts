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
    applicationName = "luna-de-host"
}

dependencies {
    implementation(project(":LunaCore"))
    // The same org.json LunaCore compiles against, which Android's platform provides there.
    implementation("com.vaadin.external.google:android-json:0.0.20131108.vaadin1")
    // db8's and WebSQL's SQLite (LunaCore's JdbcSql finds the driver through java.sql), and ICU's
    // collation, which is what Android's java.text.Collator is (LunaCore's Collation).
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("com.ibm.icu:icu4j:75.1")
}

/**
 * Text Assist's corrector is LunaKeyboard's own (Corrector.kt, plain Kotlin, adapted from Pre
 * Keyboard under Apache 2.0: LunaKeyboard/NOTICE), compiled into the host as it is, so Luna DE's
 * keyboard corrects as Lunacy's does. LunaKeyboard depends on nothing in Lunacy and stays so: the
 * file is copied in at build time, not shared as a module.
 */
val keyboardCorrector = tasks.register<Copy>("keyboardCorrector") {
    // With the keymap it measures keys' nearness on (plain Kotlin too).
    from(rootProject.file("LunaKeyboard/src/main/java/org/webosarchive/keyboard/Corrector.kt"),
         rootProject.file("LunaKeyboard/src/main/java/org/webosarchive/keyboard/Keymap.kt"))
    into(layout.buildDirectory.dir("generated/keyboard/org/webosarchive/keyboard"))
}
kotlin.sourceSets["main"].kotlin.srcDir(layout.buildDirectory.dir("generated/keyboard"))
tasks.named("compileKotlin") { dependsOn(keyboardCorrector) }
