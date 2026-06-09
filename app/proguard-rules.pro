# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# DataStore uses reflection for preferences serialization
-keep class androidx.datastore.** { *; }

# ExifInterface
-keep class androidx.exifinterface.media.** { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
