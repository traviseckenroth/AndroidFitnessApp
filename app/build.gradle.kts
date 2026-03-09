@file:Suppress("DEPRECATION")

import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)

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
    androidResources {
        noCompress.addAll(listOf("onnx", "bin", "txt"))
    }
    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // --- AWS CONFIG ---
        val awsRegion = localProperties.getProperty("AWS_REGION")?.trim('\"', '\'') ?: "us-east-1"
        val cognitoPoolId = localProperties.getProperty("COGNITO_IDENTITY_POOL_ID")?.trim('\"', '\'') ?: ""

        buildConfigField("String", "AWS_REGION", "\"$awsRegion\"")
        buildConfigField("String", "COGNITO_IDENTITY_POOL_ID", "\"$cognitoPoolId\"")
        buildConfigField("String", "COGNITO_USER_POOL_ID", "\"${localProperties.getProperty("COGNITO_USER_POOL_ID")?.trim('\"', '\'')}\"")
        buildConfigField("String", "COGNITO_CLIENT_ID", "\"${localProperties.getProperty("COGNITO_CLIENT_ID")?.trim('\"', '\'')}\"")

        // --- ELEVENLABS CONFIG ---
        val elevenLabsApiKey = localProperties.getProperty("ELEVENLABS_API_KEY")?.trim('\"', '\'') ?: ""
        buildConfigField("String", "ELEVENLABS_API_KEY", "\"$elevenLabsApiKey\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = true // Removes unused code
            isShrinkResources = true // Removes unused images/layouts
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Ignore manual jniLibs to avoid conflicts with AAR native libs
    sourceSets {
        getByName("main") {
            jniLibs.setSrcDirs(emptyList<String>())
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
    implementation("aws.sdk.kotlin:cognitoidentityprovider:1.0.41")

    // --- Compose ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation("org.jsoup:jsoup:1.22.1")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // --- AWS SDK for Kotlin ---
    implementation(platform(libs.aws.sdk.kotlin.bom))
    implementation("aws.sdk.kotlin:s3") // <-- Add this new line
    implementation(libs.aws.bedrock.runtime)

    // --- Architecture & Navigation ---
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("com.google.firebase:firebase-firestore")

    // --- Room Database ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- Database Security ---
    implementation(libs.android.database.sqlcipher)
    implementation(libs.androidx.security.crypto)

    // --- Biometric Authentication ---
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.concurrent.futures)

    // --- Hilt (Dependency Injection) ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")

    // --- WorkManager ---
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(files("libs/sherpa-onnx-1.12.26.aar"))
    // --- Health Connect ---
    implementation("androidx.health.connect:connect-client:1.1.0")

    // --- AWS SDK for Kotlin ---
    implementation(platform(libs.aws.sdk.kotlin.bom))
    implementation(libs.aws.bedrock.runtime)
    implementation("aws.sdk.kotlin:transcribestreaming:1.0.41")
    implementation(libs.aws.cognito.identity)
    implementation(libs.http.client.engine.okhttp)

    // --- Serialization ---
    implementation(libs.kotlinx.serialization.json)

    // --- Preferences ---
    implementation(libs.androidx.datastore.preferences)
    implementation("com.google.guava:guava:33.5.0-android")

    // --- Firebase ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.common.ktx)
    implementation(libs.firebase.config)
    implementation("com.google.firebase:firebase-config-ktx")
    implementation(libs.play.services.coroutines)

    // --- Image Loading ---
    implementation(libs.coil.compose)

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
    }
}
