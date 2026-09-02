# ===== OkHttp =====
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ===== Kotlin Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ===== Keep R class references for resources =====
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ===== Keep data classes =====
-keep class com.dikacode.recorder.** { *; }
-keep class com.dikacode.service.** { *; }
-keep class com.dikacode.ui.components.** { *; }
-keep class com.dikacode.ui.theme.** { *; }

# ======================================================================
# SECURITY: Protect critical security classes — @dikaacode
# DO NOT REMOVE OR MODIFY THESE RULES
# These rules protect the attribution and security system from
# obfuscation and ensure @dikaacode credits survive release builds.
# ======================================================================

# Keep all security classes completely
-keep class com.dikacode.security.** { *; }

# Keep MainActivity (contains security initialization)
-keep class com.dikacode.MainActivity { *; }

# Keep all security class members
-keepclassmembers class com.dikacode.security.** {
    <fields>;
    <methods>;
    <init>(...);
}

# Keep specific critical classes and their members
-keepclassmembers class com.dikacode.security.SecurityEngine {
    *;
}
-keepclassmembers class com.dikacode.security.SecurityManager {
    *;
}
-keepclassmembers class com.dikacode.security.CreditManager {
    *;
}
-keepclassmembers class com.dikacode.security.AttributionGuard {
    *;
}
-keepclassmembers class com.dikacode.security.IdentityVerifier {
    *;
}
-keepclassmembers class com.dikacode.security.IntegrityChecker {
    *;
}
-keepclassmembers class com.dikacode.security.RuntimeDetector {
    *;
}
-keepclassmembers class com.dikacode.security.SecurityPolicy {
    *;
}
-keepclassmembers class com.dikacode.security.RiskAssessment {
    *;
}

# Protect BuildConfig credit and security fields
-keepclassmembers class com.dikacode.BuildConfig {
    public static final String CREDIT_*;
    public static final String SECURITY_*;
    public static final String BUILD_ID;
    public static final String RELEASE_ID;
    public static final String ATTRIBUTION_ID;
    public static final int SECURITY_VERSION;
}

# Protect SecuritySignal sealed class hierarchy
-keep class com.dikacode.security.SecuritySignal { *; }
-keep class com.dikacode.security.SecuritySignal$* { *; }

# Protect TrustState and AttributionState enums
-keep class com.dikacode.security.TrustState { *; }
-keep class com.dikacode.security.AttributionState { *; }

# Protect SecurityEvent and RiskAssessment data classes
-keep class com.dikacode.security.SecurityEvent { *; }
-keep class com.dikacode.security.RiskAssessment$RiskResult { *; }
-keep class com.dikacode.security.RiskAssessment$SignalWeight { *; }
-keep class com.dikacode.security.RiskAssessment$CorrelationCluster { *; }

# Protect credit string resources from removal
-keepclassmembers class **.R$string {
    public static *credit_*;
    public static *app_name;
}

# ======================================================================
# Obfuscation rules for security
# ======================================================================

# Repackage security classes to obfuscate structure
-repackageclasses com.dikacode.security
-allowaccessmodification

# Optimize but keep security-critical code intact
-optimizations !code/simplification/variable
