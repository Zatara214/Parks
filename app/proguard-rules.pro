# kotlinx.serialization: keep the generated serializers R8 cannot see referenced.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class contact.kaufman.parks.** {
    *** Companion;
}
-keepclasseswithmembers class contact.kaufman.parks.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class contact.kaufman.parks.**$$serializer { *; }

# Ktor + OkHttp
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Strip debug and verbose logging from release builds.
# The location diagnostics print GPS coordinates, which have no business surviving into a
# shipped build of an app whose whole point is not hoarding that kind of thing. Warnings
# and errors stay, because those are about real failures.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
