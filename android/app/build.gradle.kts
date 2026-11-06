plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.aura.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.aura.launcher"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Signing: a personal key kept OUT of git. Put it at app/aura.jks (or point AURA_KEYSTORE at it)
    // and set AURA_KEY_PASSWORD. Using the same key every time lets a new APK install over the old
    // one. Without it, builds fall back to the debug key (fine for a first install).
    val keyFile = file(System.getenv("AURA_KEYSTORE") ?: "aura.jks")
    val keyPassword = System.getenv("AURA_KEY_PASSWORD")
    val hasKey = keyFile.exists() && !keyPassword.isNullOrEmpty()
    signingConfigs {
        if (hasKey) create("aura") {
            storeFile = keyFile
            storePassword = keyPassword
            keyAlias = System.getenv("AURA_KEY_ALIAS") ?: "aura"
            this.keyPassword = keyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasKey) signingConfigs.getByName("aura") else signingConfigs.getByName("debug")
        }
        debug {
            if (hasKey) signingConfig = signingConfigs.getByName("aura")
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
