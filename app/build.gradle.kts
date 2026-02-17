@file:Suppress("DEPRECATION")

import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.compose.compiler)
    id("com.google.gms.google-services")
}

// --- Load Secrets from local.properties ---
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // --- FIX: ADD COGNITO KEYS HERE ---
        val awsRegion = localProperties.getProperty("AWS_REGION") ?: "us-east-1"
        val cognitoPoolId = localProperties.getProperty("COGNITO_IDENTITY_POOL_ID") ?: ""

        buildConfigField("String", "AWS_REGION", "\"$awsRegion\"")
        buildConfigField("String", "COGNITO_IDENTITY_POOL_ID", "\"$cognitoPoolId\"")
        buildConfigField("String", "COGNITO_USER_POOL_ID", "\"${localProperties.getProperty("COGNITO_USER_POOL_ID")}\"")
        buildConfigField("String", "COGNITO_CLIENT_ID", "\"${localProperties.getProperty("COGNITO_CLIENT_ID")}\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true // Required for the buildConfigField above to work
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // --- Core Android ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation("aws.sdk.kotlin:cognitoidentityprovider:1.0.41") // or matching your other aws versions
    // --- Compose ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons)

    // --- Architecture & Navigation ---
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.compose)

    // --- Room Database ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.compose.foundation.layout)
    ksp(libs.androidx.room.compiler)
    val room_version = "2.6.1" // Update this to the latest stable version
    implementation("androidx.room:room-runtime:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // --- Hilt (Dependency Injection) ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07") // Check for latest version
    // --- AWS SDK for Kotlin ---
    implementation(platform(libs.aws.sdk.kotlin.bom))

    // 1. Bedrock Runtime (for AI)
    implementation(libs.aws.bedrock.runtime)
    implementation("aws.sdk.kotlin:transcribestreaming:1.0.41")
    // 2. Cognito Identity (for Guest Credentials) -> THIS REPLACES 'auth'
    implementation(libs.aws.cognito.identity)

    // 3. HTTP Client Engine
    implementation(libs.http.client.engine.okhttp)

    // --- Serialization ---
    implementation(libs.kotlinx.serialization.json)

    // --- Preferences ---
    implementation(libs.androidx.datastore.preferences)
    implementation("com.google.guava:guava:31.1-android")

    // --- Firebase ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.common.ktx)
    implementation(libs.firebase.config)
    implementation("com.google.firebase:firebase-config-ktx")
    implementation(libs.play.services.coroutines)


    // --- CameraX ---
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // --- ML Kit ---
    implementation("com.google.mlkit:pose-detection-accurate:18.0.0-beta5")
    implementation("androidx.compose.material:material-icons-extended")
    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
    }
}
