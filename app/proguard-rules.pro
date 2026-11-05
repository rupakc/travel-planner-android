# R8 full mode (gradle.properties). Libraries ship their own consumer rules
# (kotlinx.serialization, Retrofit, OkHttp, Hilt, Room, WorkManager).

# Type-safe navigation routes are looked up by their generated serializers.
-keep @kotlinx.serialization.Serializable class io.github.rupakc.travelplanner.core.model.navigation.** { *; }

# Keep line numbers for crash reports; hide original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
