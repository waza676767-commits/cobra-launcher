# Vosk talks to native code through JNA, which needs these by name.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
# The Claude SDK ships its own R8 rules; these just silence warnings for JVM-only classes it can reference.
-dontwarn java.awt.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn reactor.blockhound.**
# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.google.auto.value.AutoValue# Be conservative with the Claude SDK: keep its classes and Jackson unrenamed so JSON mapping can't break.
-keep,allowshrinking class com.anthropic.** { *; }
-keep,allowshrinking class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
# kotlin-reflect (used by the SDK's JSON layer) looks up Kotlin stdlib classes by name at runtime.
# Without this the shrunk app crashes with NoClassDefFoundError: kotlin/Unit when calling the AI.
-keep class kotlin.** { *; }
-dontwarn org.jetbrains.annotations.**
