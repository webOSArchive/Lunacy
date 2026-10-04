import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

/**
 * Every module's APKs are copied to out/ at the top of the repo, so there is one place to
 * find something to install rather than two paths several folders deep. The APKs Gradle
 * writes are already named after their module and variant (AndroidLuna-debug.apk), so they
 * are copied as they are.
 */
val outDir = rootProject.layout.projectDirectory.dir("out")

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                val name = variant.name.replaceFirstChar { it.uppercase() }
                val copy = tasks.register<Copy>("copy${name}ApkToOut") {
                    description = "Copies the $name APK to out/."
                    from(variant.artifacts.get(SingleArtifact.APK)) { include("*.apk") }
                    into(outDir)
                }
                afterEvaluate { tasks.named("assemble$name") { finalizedBy(copy) } }
            }
        }
    }
}

/**
 * Release builds are signed with codepoet's key, named in keystore.properties at the top of the
 * repo, which is git-ignored and stays on the machine that builds releases:
 *
 *     storeFile=/path/to/keystore.jks
 *     storePasswordFile=/path/to/file-holding-the-store-password
 *     keyAlias=upload
 *     keyPasswordFile=/path/to/file   (optional: the store's password otherwise)
 *
 * The passwords are read from their files, so neither is written here or in the properties.
 * Without the file a release build is left unsigned and Android won't install it; the debug
 * build is signed with the SDK's debug key as before.
 */
val signing = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { f ->
    java.util.Properties().apply { f.inputStream().use { load(it) } }
}
subprojects {
    plugins.withId("com.android.application") {
        if (signing == null) return@withId
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            fun secret(key: String) = file(signing.getProperty(key)).readText().trimEnd('\n', '\r')
            val release = signingConfigs.create("lunacy") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = secret("storePasswordFile")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = secret(if (signing.getProperty("keyPasswordFile") != null) "keyPasswordFile" else "storePasswordFile")
            }
            buildTypes.getByName("release").signingConfig = release
        }
    }
}

/** Clears out/ along with the modules' own build folders. */
tasks.register<Delete>("clean") {
    delete(outDir)
}
