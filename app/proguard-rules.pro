# Keep OkHttp, Ktor & Coroutines metadata
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**

# Keep WebRTC JNI & native callbacks intact
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Keep Kotlinx Serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.duinch.agentremote.**$$serializer { *; }
-keepclassmembers class app.duinch.agentremote.** {
    *** Companion;
}
-keepclasseswithmembers class app.duinch.agentremote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Strip verbose/debug/info logs from release builds to prevent logcat scraping
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Repackage obfuscated classes into a flat package to hinder decompilation navigation
-repackageclasses 'o'
-allowaccessmodification
