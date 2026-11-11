# The Anthropic SDK reads and writes its request and response types through Jackson's Kotlin module,
# which finds constructors, property names and nested classes by reflection at runtime. A shrinker
# can't see those uses, so the SDK and the reflection stack under it are left exactly as published:
# nothing removed, renamed or rearranged. This costs APK size but keeps Claude chat from failing on
# replies the shrinker couldn't have known about.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Compile-time-only or desktop-only classes referenced by the SDK's dependencies; never loaded on Android.
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.AutoValue$*
-dontwarn java.beans.**
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedParameterizedType

# Keep readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
