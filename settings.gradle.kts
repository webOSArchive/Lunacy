pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "lunacy"
// The shell, and the optional companion keyboard. Neither depends on the other; the keyboard
// is here so it builds with the same toolchain, not because Lunacy needs it.
include(":AndroidLuna")
include(":LunaKeyboard")
