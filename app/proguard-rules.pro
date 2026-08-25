# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nekomimi.assistant.**$$serializer { *; }
-keepclassmembers class com.nekomimi.assistant.** {
    *** Companion;
}
-keepclasseswithmembers class com.nekomimi.assistant.** {
    kotlinx.serialization.KSerializer serializer(...);
}
