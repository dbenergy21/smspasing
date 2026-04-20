# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.sms2notion.app.**$$serializer { *; }
-keepclassmembers class com.sms2notion.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.sms2notion.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# MediaPipe
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
