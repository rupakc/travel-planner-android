# R8 full mode (gradle.properties). Libraries ship their own consumer rules
# (kotlinx.serialization, Retrofit, OkHttp, Hilt, Room, WorkManager).

# Type-safe navigation routes are looked up by their generated serializers.
-keep @kotlinx.serialization.Serializable class io.github.rupakc.travelplanner.core.model.navigation.** { *; }

# Keep line numbers for crash reports; hide original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization models (review M8) ---------------------------------------------
# Retrofit resolves response types from generic signatures (e.g. Continuation<List<Plan>>) and asks
# kotlinx.serialization for their serializer reflectively; R8 cannot see those uses and would strip
# models no screen touches yet. Keep every core:model @Serializable class with its serializers
# (names may still be obfuscated). `verifyReleaseKeepRules` checks usage.txt after minification.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep,allowobfuscation @kotlinx.serialization.Serializable class io.github.rupakc.travelplanner.core.model.** { *; }
-keep,allowobfuscation class io.github.rupakc.travelplanner.core.model.**$$serializer { *; }
-keepclassmembers class io.github.rupakc.travelplanner.core.model.** {
    *** Companion;
}
-keepclassmembers class io.github.rupakc.travelplanner.core.model.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
# Retrofit API interfaces are created by reflection.
-keep,allowobfuscation interface io.github.rupakc.travelplanner.core.network.api.** { *; }
