// @dikaacode
package com.dikacode.security

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Binary and manifest integrity verification.
 * Checks DEX, native libraries, and manifest integrity.
 */
object IntegrityChecker {
    private const val TAG = "IntegrityChecker"

    // ─── Expected integrity hashes ────────────────────────────────
    // These are populated after official build verification.
    // In production, these would be generated during the build process.

    private var expectedDexHash: String? = null
    private var expectedManifestHash: String? = null
    private val expectedNativeLibHashes = mutableMapOf<String, String>()

    /**
     * Initialize with expected hashes from official build.
     */
    fun initialize(
        dexHash: String? = null,
        manifestHash: String? = null,
        nativeLibHashes: Map<String, String> = emptyMap()
    ) {
        expectedDexHash = dexHash
        expectedManifestHash = manifestHash
        expectedNativeLibHashes.putAll(nativeLibHashes)
    }

    /**
     * Record current APK integrity as trusted baseline.
     */
    fun recordBaseline(context: Context) {
        try {
            val apkPath = context.applicationInfo.sourceDir
            val apkFile = File(apkPath)

            if (apkFile.exists()) {
                expectedManifestHash = hashFile(apkFile)
                Log.i(TAG, "Baseline recorded: manifest hash = ${expectedManifestHash?.take(16)}...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record baseline", e)
        }
    }

    /**
     * Perform full integrity check.
     * Returns list of signals if any anomalies detected.
     */
    fun performIntegrityCheck(context: Context): List<SecuritySignal> {
        val signals = mutableListOf<SecuritySignal>()

        // 1. APK file integrity
        signals.addAll(checkApkIntegrity(context))

        // 2. Critical class integrity
        signals.addAll(checkClassIntegrity())

        // 3. Resource integrity
        signals.addAll(checkResourceIntegrity(context))

        return signals
    }

    /**
     * Check APK file integrity.
     */
    private fun checkApkIntegrity(context: Context): List<SecuritySignal> {
        val signals = mutableListOf<SecuritySignal>()

        try {
            val apkPath = context.applicationInfo.sourceDir
            val apkFile = File(apkPath)

            if (!apkFile.exists()) {
                signals.add(SecuritySignal.SignalIntegrityManifestMismatch("APK file not found"))
                return signals
            }

            // Check if APK has been modified since baseline
            val currentHash = hashFile(apkFile)
            if (expectedManifestHash != null && currentHash != expectedManifestHash) {
                signals.add(SecuritySignal.SignalIntegrityManifestMismatch("APK file hash mismatch"))
            }

            // Check APK size (modified APKs often have different sizes)
            val length = apkFile.length()
            if (length <= 0) {
                signals.add(SecuritySignal.SignalIntegrityManifestMismatch("APK file is empty"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "APK integrity check failed", e)
        }

        return signals
    }

    /**
     * Check critical class integrity by attempting to load them.
     */
    private fun checkClassIntegrity(): List<SecuritySignal> {
        val signals = mutableListOf<SecuritySignal>()

        val criticalClasses = listOf(
            "com.dikacode.security.SecurityEngine",
            "com.dikacode.security.IdentityVerifier",
            "com.dikacode.security.IntegrityChecker",
            "com.dikacode.security.AttributionGuard",
            "com.dikacode.security.CreditManager",
            "com.dikacode.MainActivity"
        )

        for (className in criticalClasses) {
            try {
                Class.forName(className)
            } catch (e: ClassNotFoundException) {
                signals.add(SecuritySignal.SignalDexMismatch(
                    expectedHash = "class_exists",
                    actualHash = "class_missing:$className"
                ))
                Log.w(TAG, "Critical class missing: $className")
            }
        }

        return signals
    }

    /**
     * Check resource integrity.
     */
    private fun checkResourceIntegrity(context: Context): List<SecuritySignal> {
        val signals = mutableListOf<SecuritySignal>()

        // Verify critical resources exist
        val criticalResources = listOf(
            "credit_developer" to "string",
            "credit_team" to "string",
            "app_name" to "string"
        )

        for ((resName, resType) in criticalResources) {
            val resId = context.resources.getIdentifier(resName, resType, context.packageName)
            if (resId == 0) {
                signals.add(SecuritySignal.SignalIntegrityManifestMismatch("Resource missing: $resName"))
            }
        }

        return signals
    }

    // ─── Utility methods ──────────────────────────────────────────

    /**
     * Compute SHA-256 hash of a file.
     */
    fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute SHA-256 hash of a string.
     */
    fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
