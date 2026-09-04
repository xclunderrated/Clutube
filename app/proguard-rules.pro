# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep JavaScript bridges and their annotated callbacks available to WebView.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Player callbacks are looked up by WebView/Chromium rather than direct Kotlin calls.
-keep class com.example.util.PlayerViewManager { *; }
-keep class com.example.util.StreamAdBlocker { *; }

# NewPipe Extractor uses Rhino for a few YouTube parsing paths.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter { *; }
-dontwarn org.mozilla.javascript.tools.**
# These Rhino integrations are JVM-only optional paths and are not present on
# Android. Keep R8 from treating their references as required app classes.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# SettingsManager uses Moshi's reflection adapter for the app-owned models.
# Keep their Kotlin metadata, field names, and constructors intact in release
# builds so startup history migration remains safe after R8 obfuscation.
-keep class com.example.model.** { *; }

# Room entities, DAOs, and Torrent models
-keep class com.example.data.local.** { *; }
-keep class com.example.data.model.** { *; }
-keep class com.example.data.torrent.** { *; }

# libtorrent4j JNI and native bindings
-keep class org.libtorrent4j.swig.libtorrent_jni { *; }
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**
