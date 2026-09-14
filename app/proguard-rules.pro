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
