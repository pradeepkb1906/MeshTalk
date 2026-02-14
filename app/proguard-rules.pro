# MeshTalk ProGuard Rules

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.meshtalk.app.**$$serializer { *; }
-keepclassmembers class com.meshtalk.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.meshtalk.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities
-keep class com.meshtalk.app.data.model.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Google Play Services
-keep class com.google.android.gms.** { *; }

