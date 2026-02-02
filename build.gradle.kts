plugins {
    // 1. Android Application
    alias(libs.plugins.android.application) apply false

    // 2. Kotlin (Fixes the "already requested" error)
    alias(libs.plugins.kotlin.android) apply false

    // 3. KSP (Annotation Processing)
    alias(libs.plugins.ksp) apply false

    // 4. Hilt (Dependency Injection)
    alias(libs.plugins.hilt.android) apply false

    // 5. Compose Compiler
    alias(libs.plugins.compose.compiler) apply false

    // 6. Serialization (If you are using it)
    alias(libs.plugins.kotlin.serialization) apply false
}