plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.velo.browser"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.velo.browser"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // One fixed key, kept in the repository, so every build can be installed as an update over
        // the previous one. Anyone with this key can sign an APK that Android accepts as Velo,
        // so swap it for a private key before publishing the app anywhere.
        create("release") {
            storeFile = rootProject.file("keystore/velo-release.jks")
            storePassword = "velo-release"
            keyAlias = "velo"
            keyPassword = "velo-release"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
            // GraalVM native-image metadata from the Anthropic SDK: megabytes that Android never reads.
            excludes += "META-INF/native-image/**"
        }
    }

    testOptions {
        unitTests {
            // The interface tests run the real activity on the JVM and need the app's resources.
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                it.systemProperty("roborazzi.test.record", "true")
            }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.webkit:webkit:1.18.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Claude, through Anthropic's official SDK. OkHttp (which the SDK already brings in) is also
    // used directly for the OpenAI and Gemini APIs.
    implementation("com.anthropic:anthropic-java:3.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.78.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.78.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
