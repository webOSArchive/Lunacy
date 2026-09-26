# R8 for both build types (build.gradle.kts). Shrinking only: names stay as written, so a
# stack trace in logcat and a class in chrome://inspect read as the source does.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# The JavaScript bridge: every @JavascriptInterface method is called by name from the page
# (the default rules keep them too; stated here so it is not left to them).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
