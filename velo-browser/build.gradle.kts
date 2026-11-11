buildscript {
    dependencies {
        // AGP's built-in Kotlin support uses whatever Kotlin Gradle plugin is on the classpath.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
