plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.mango.os"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.mango.os"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Signing: a personal key kept OUT of git. Put it at app/mango.jks (or point MANGO_KEYSTORE at it)
    // and set MANGO_KEY_PASSWORD. Using the same key every time lets a new APK install over the old
    // one. Without it, builds fall back to the debug key (fine for a first install).
    val keyFile = file(System.getenv("MANGO_KEYSTORE") ?: "mango.jks")
    val keyPassword = System.getenv("MANGO_KEY_PASSWORD")
    val hasKey = keyFile.exists() && !keyPassword.isNullOrEmpty()
    signingConfigs {
        if (hasKey) create("mango") {
            storeFile = keyFile
            storePassword = keyPassword
            keyAlias = System.getenv("MANGO_KEY_ALIAS") ?: "aura"
            this.keyPassword = keyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasKey) signingConfigs.getByName("mango") else signingConfigs.getByName("debug")
        }
        debug {
            if (hasKey) signingConfig = signingConfigs.getByName("mango")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
