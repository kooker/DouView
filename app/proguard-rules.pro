# Add project specific ProGuard rules here.
# Preserve source line numbers for diagnostics without exposing source paths.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room persistence entities, DAOs and generated database implementations
-keep class com.example.data.** { *; }
-keep class com.example.model.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Media3 / ExoPlayer reflection & decoder discovery
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Kotlin Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
