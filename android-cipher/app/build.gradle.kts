/**
 * Client-side Firebase values, read from the environment at build time.
 *
 * These are the same four values a `google-services.json` would carry — public
 * client identifiers, safe inside an APK — so keeping them in the environment
 * only avoids committing a config file, it is not a secret store. A real
 * environment variable wins; the project's `.env` is the fallback.
 */
val dotenv: Map<String, String> = rootProject.file(".env")
    .takeIf { it.exists() }
    ?.readLines()
    ?.mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
        val split = trimmed.indexOf('=')
        if (split <= 0) return@mapNotNull null
        trimmed.substring(0, split) to trimmed.substring(split + 1).trim().trim('"')
    }
    ?.toMap()
    .orEmpty()

fun firebaseValue(name: String): String = System.getenv(name) ?: dotenv[name] ?: ""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rork.cipher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rork.cipher"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            "\"${firebaseValue("RORK_PUBLIC_FIREBASE_PROJECT_ID")}\""
        )
        buildConfigField(
            "String",
            "FIREBASE_APP_ID",
            "\"${firebaseValue("RORK_PUBLIC_FIREBASE_APP_ID")}\""
        )
        buildConfigField(
            "String",
            "FIREBASE_API_KEY",
            "\"${firebaseValue("RORK_PUBLIC_FIREBASE_API_KEY")}\""
        )
        buildConfigField(
            "String",
            "FIREBASE_SENDER_ID",
            "\"${firebaseValue("RORK_PUBLIC_FIREBASE_SENDER_ID")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Firebase is configured in code from environment values, so there is no
    // google-services.json in the repository and no plugin to apply.
    implementation(libs.firebase.messaging)
    debugImplementation(libs.androidx.ui.tooling)
}
