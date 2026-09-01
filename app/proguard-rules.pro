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
-keep class com.example.recorder.** { *; }
-keep class com.example.service.** { *; }
-keep class com.example.ui.components.** { *; }
-keep class com.example.ui.theme.** { *; }
