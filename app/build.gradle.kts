import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

val releaseKeystorePath = providers.environmentVariable("OPENSTREAM_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("OPENSTREAM_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("OPENSTREAM_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("OPENSTREAM_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.ivor.openstream"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ivor.openstream"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "2.2"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        val tmdbApiKey = listOfNotNull(
            localProperties.getProperty("TMDB_API_KEY"),
            providers.environmentVariable("TMDB_API_KEY").orNull
        )
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .ifBlank { "DEMO_KEY" }
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val vidkingApiBaseUrl = localProperties
            .getProperty("VIDKING_API_BASE_URL", "https://api.speedracelight.com")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "VIDKING_API_BASE_URL", "\"$vidkingApiBaseUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Strip dead library resources (AppCompat/Material/Cast/ExoPlayer
            // layouts, drawables and strings the app never uses).
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
    // Keep only the locales the app actually translates (values/ + values-XX).
    // Without this, every library (AppCompat, Material, GMS, ExoPlayer) ships
    // ~95 extra languages into resources.arsc (~1MB waste).
    androidResources {
        localeFilters += listOf(
            "en", "ar", "de", "es", "fr", "hi", "it", "ja",
            "ko", "pt", "pt-rBR", "ru", "uk", "zh-rCN"
        )
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Drop duplicated license/version files every library jar brings.
            excludes += "META-INF/**/LICENSE.txt"
            excludes += "META-INF/**/*.version"
            excludes += "META-INF/**/*.md"
            excludes += "META-INF/**/CHANGES"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose BOM & UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.material)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    // Debug-only: verbose HTTP logging. Release uses a no-op (see
    // src/debug/.../LoggingInterceptorFactory.kt vs src/release/...).
    debugImplementation(libs.okhttp.logging.interceptor)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.kotlinx.serialization.json)

    // Local Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.cast)
    implementation(libs.androidx.media3.session)

    // SmoothMotion (Video Frame Interpolation)
    implementation(libs.smoothmotion.media3)
    implementation(libs.smoothmotion.ui)

    // Graphics Shapes
    implementation(libs.androidx.graphics.shapes)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
