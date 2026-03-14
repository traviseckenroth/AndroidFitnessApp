# Add project specific ProGuard rules here.

# --- JSOUP & RE2J ---
-keep class com.google.re2j.** { *; }
-dontwarn com.google.re2j.**

# --- AI & NATIVE (Sherpa-ONNX / ONNX Runtime) ---
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }

-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }

# Keep all native method declarations and their classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- VOSK & JNA PROTECTION ---
-keep class com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn org.vosk.**

# --- SQLCipher PROTECTION ---
# Required for encrypted database stability
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# --- AWS SDK PROTECTION ---
-keep class aws.sdk.kotlin.** { *; }
-keep class aws.smithy.kotlin.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# --- KOTLINX SERIALIZATION ---
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembernames class * {
    @kotlinx.serialization.SerialName <fields>;
}

# --- GUAVA PROTECTION ---
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# --- GENERAL APP STABILITY ---
-keepattributes SourceFile, LineNumberTable
-keepattributes Exceptions
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# --- SQLCIPHER DATABASE PROTECTION ---
# Prevents R8 from renaming database handles required by C++
-keep class net.sqlcipher.** { *; }
-keepclassmembers class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keepclassmembers class net.sqlcipher.database.** { *; }
