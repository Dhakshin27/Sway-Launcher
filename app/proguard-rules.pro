# Fix IllegalAccessError when R8 repackages Kotlin collection subclasses
-keeppackagenames kotlin.**
-keep class kotlin.collections.** { *; }
-keep class kotlin.Metadata { *; }

# Keep AndroidX Startup & Lifecycle classes intact without triggering IDE resolution warnings
-keep class androidx.startup.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.profileinstaller.** { *; }
-dontwarn androidx.startup.**
-dontwarn androidx.lifecycle.**

# Keep AndroidSVG (used by IconResolver for Lawnicons)
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**