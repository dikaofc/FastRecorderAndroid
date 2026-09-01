# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep R class references for resources
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep data classes
-keep class com.dikacode.recorder.** { *; }
-keep class com.dikacode.service.** { *; }
-keep class com.dikacode.ui.components.** { *; }
-keep class com.dikacode.ui.theme.** { *; }

# ===== SECURITY: Protect critical security classes =====
# DO NOT REMOVE OR MODIFY THESE RULES
-keep class com.dikacode.security.** { *; }
-keep class com.dikacode.MainActivity { *; }
-keepclassmembers class com.dikacode.security.** {
    <fields>;
    <methods>;
}
-keepclassmembers class com.dikacode.security.SecurityManager {
    *;
}
-keepclassmembers class com.dikacode.security.CreditManager {
    *;
}
# Protect BuildConfig credit fields
-keepclassmembers class com.dikacode.BuildConfig {
    public static final String CREDIT_*;
    public static final String SECURITY_*;
}
# Obfuscate security package to make reverse engineering harder
-repackageclasses com.dikacode.security
-allowaccessmodification
