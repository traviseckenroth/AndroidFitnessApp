buildscript {
    repositories {
        google()
        mavenCentral()
    }
    // REMOVED: dependencies { classpath("com.google.gms:google-services:4.4.2") }
}

plugins {
    // 1. Android Application
    alias(libs.plugins.android.application) apply false

    // 2. Kotlin
    alias(libs.plugins.kotlin.android) apply false

    // 3. KSP
    alias(libs.plugins.ksp) apply false

    // 4. Hilt
    alias(libs.plugins.hilt.android) apply false

    // 5. Compose Compiler
    alias(libs.plugins.compose.compiler) apply false

    // 6. Serialization
    alias(libs.plugins.kotlin.serialization) apply false
    
    // 7. Google Services
    alias(libs.plugins.google.services) apply false
}
