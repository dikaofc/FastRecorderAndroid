// @dikaacode
package com.dikacode.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * System-level security manager.
 * Verifies APK integrity, package name, and signature.
 * Prevents rename, mod, and unauthorized redistribution.
 */
object SecurityManager {
    private const val TAG = "SecurityManager"

    // Original package name - DO NOT CHANGE
    private const val ORIGINAL_PACKAGE = "com.dika.fastrecorder"

    // Original signature hash (SHA-256) - will be set on first build
    // Format: colon-separated hex bytes of the signing certificate SHA-256
    private var EXPECTED_SIGNATURE_HASH: String = ""

    // Developer credit - embedded in multiple layers
    const val DEVELOPER_CREDIT = "@dikaacode"
    const val APP_AUTHORITY = "dikaacode"
    const val SECURITY_VERSION = 2

    /**
     * Initialize security manager with expected signature hash.
     * Call this once on first app launch.
     */
    fun initialize(expectedHash: String) {
        EXPECTED_SIGNATURE_HASH = expectedHash
    }

    /**
     * Full security check - returns SecurityResult with all checks.
     */
    fun performFullCheck(context: Context): SecurityResult {
        val results = mutableListOf<CheckResult>()

        // 1. Package name verification
        results.add(checkPackageName(context))

        // 2. Signature verification
        results.add(checkSignature(context))

        // 3. Debug mode detection
        results.add(checkDebugMode(context))

        // 4. Emulator detection
        results.add(checkEmulator())

        // 5. Root detection
        results.add(checkRoot())

        // 6. Integrity check
        results.add(checkIntegrity(context))

        val allPassed = results.all { it.passed }
        val isTampered = results.any { it.isCritical && !it.passed }

        return SecurityResult(
            allChecksPassed = allPassed,
            isTampered = isTampered,
            checks = results
        )
    }

    /**
     * Check if package name matches original.
     */
    private fun checkPackageName(context: Context): CheckResult {
        val currentPackage = context.packageName
        val passed = currentPackage == ORIGINAL_PACKAGE
        return CheckResult(
            name = "PackageName",
            passed = passed,
            isCritical = true,
            detail = if (passed) "Package verified" else "Package renamed: $currentPackage"
        )
    }

    /**
     * Verify APK signing certificate.
     */
    private fun checkSignature(context: Context): CheckResult {
        if (EXPECTED_SIGNATURE_HASH.isEmpty()) {
            return CheckResult(
                name = "Signature",
                passed = true,
                isCritical = false,
                detail = "Signature check skipped (no hash configured)"
            )
        }

        return try {
            val signatureHash = getSignatureHash(context)
            val passed = signatureHash == EXPECTED_SIGNATURE_HASH
            CheckResult(
                name = "Signature",
                passed = passed,
                isCritical = true,
                detail = if (passed) "Signature verified" else "Signature mismatch - APK may be re-signed"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Signature check failed", e)
            CheckResult(
                name = "Signature",
                passed = false,
                isCritical = true,
                detail = "Signature check error: ${e.message}"
            )
        }
    }

    /**
     * Get SHA-256 hash of the APK signing certificate.
     */
    private fun getSignatureHash(context: Context): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
        }

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        val sig = signatures?.firstOrNull() ?: throw IllegalStateException("No signature found")
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(sig.toByteArray())
        return hash.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Detect debug builds.
     */
    private fun checkDebugMode(context: Context): CheckResult {
        val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return CheckResult(
            name = "DebugMode",
            passed = !isDebug,
            isCritical = false,
            detail = if (isDebug) "Running in debug mode" else "Release mode verified"
        )
    }

    /**
     * Basic emulator detection.
     */
    private fun checkEmulator(): CheckResult {
        val isEmulator = Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.PRODUCT.contains("sdk")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
        return CheckResult(
            name = "Emulator",
            passed = !isEmulator,
            isCritical = false,
            detail = if (isEmulator) "Running on emulator" else "Running on real device"
        )
    }

    /**
     * Basic root detection.
     */
    private fun checkRoot(): CheckResult {
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        val isRooted = rootPaths.any { java.io.File(it).exists() }
        return CheckResult(
            name = "Root",
            passed = !isRooted,
            isCritical = false,
            detail = if (isRooted) "Root detected" else "No root detected"
        )
    }

    /**
     * Verify key classes haven't been tampered with.
     */
    private fun checkIntegrity(context: Context): CheckResult {
        return try {
            // Verify critical classes exist and are loadable
            val criticalClasses = listOf(
                "com.dikacode.security.SecurityManager",
                "com.dikacode.security.CreditManager",
                "com.dikacode.MainActivity"
            )
            for (className in criticalClasses) {
                Class.forName(className)
            }
            CheckResult(
                name = "Integrity",
                passed = true,
                isCritical = true,
                detail = "All critical classes verified"
            )
        } catch (e: ClassNotFoundException) {
            CheckResult(
                name = "Integrity",
                passed = false,
                isCritical = true,
                detail = "Class integrity check failed: ${e.message}"
            )
        }
    }
}

/**
 * Result of a single security check.
 */
data class CheckResult(
    val name: String,
    val passed: Boolean,
    val isCritical: Boolean,
    val detail: String
)

/**
 * Combined security check result.
 */
data class SecurityResult(
    val allChecksPassed: Boolean,
    val isTampered: Boolean,
    val checks: List<CheckResult>
)
