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

/** Clears out/ along with the modules' own build folders. */
tasks.register<Delete>("clean") {
    delete(outDir)
}
