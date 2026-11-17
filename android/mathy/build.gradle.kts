plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.mango.mathy"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.mango.mathy"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Same private key as Mango OS (app/mango.jks): Mango only reads custom icons from a Mathy
    // signed by the same key, so nobody else can feed it fake icons.
    val keyFile = file(System.getenv("MANGO_KEYSTORE") ?: "../app/mango.jks")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
