plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.glassplayer.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.glassplayer.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 22
        versionName = "1.9.13"
        val repo = System.getenv("GITHUB_REPOSITORY") ?: "ahlanisa71-svg/musik1"
        buildConfigField("String", "UPDATE_REPO", "\"$repo\"")
    }

    val ksFile = file("musix.keystore")
    signingConfigs {
        if (ksFile.exists()) {
            create("musix") {
                storeFile = ksFile
                storePassword = "musix123"
                keyAlias = "musix"
                keyPassword = "musix123"
            }
        }
    }

    buildTypes {
        debug {
            if (ksFile.exists()) {
                signingConfig = signingConfigs.getByName("musix")
            }
        }
        release {
            isMinifyEnabled = false
            if (ksFile.exists()) {
                signingConfig = signingConfigs.getByName("musix")
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.media3.common.util.UnstableApi"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("com.google.guava:guava:32.1.3-android")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.1")
}
