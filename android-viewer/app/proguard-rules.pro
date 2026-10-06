# ProGuard rules for AI CAMs Viewer

# Keep main application classes
-keep class com.aicams.viewer.** { *; }
-keep interface com.aicams.viewer.** { *; }

# Preserve Jetpack Compose
-keep class androidx.compose.** { *; }

# Preserve lifecycle components
-keep class androidx.lifecycle.** { *; }

# Preserve navigation components
-keep class androidx.navigation.** { *; }

# Preserve Room database
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Database class * { *; }

# Preserve data store
-keep class androidx.datastore.** { *; }

# Preserve Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Preserve Retrofit
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Preserve Gson
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }

# Preserve WebRTC
-keep class org.webrtc.** { *; }
-keep interface org.webrtc.** { *; }

# Preserve data classes
-keep class com.aicams.viewer.data.models.** { *; }
-keepclassmembers class com.aicams.viewer.data.models.** { *; }

# Preserve Kotlin metadata
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class kotlin.Metadata { *; }

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Optimize
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5
