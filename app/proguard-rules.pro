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

# Jsoup optional dependency on re2j
-dontwarn com.google.re2j.**

# --- AI COACH PROTECTION ---
# Keep all Sherpa-ONNX C++ bindings completely intact
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }

# --- AWS & CLOUD PROTECTION ---
# Keep AWS SDK classes from being stripped so downloads don't crash
-keep class aws.sdk.kotlin.** { *; }
-keep class aws.smithy.kotlin.** { *; }

# --- DATA & JSON PROTECTION ---
# Prevent R8 from breaking your database models
-keepattributes *Annotation*
-keepattributes Signature

# --- ONNX RUNTIME PROTECTION (CRITICAL) ---
# Prevents the C++ ML engine from crashing on startup
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }

# --- JNI SAFETIES ---
# Ensure any native C++ methods are never renamed
-keepclasseswithmembernames class * {
    native <methods>;
}