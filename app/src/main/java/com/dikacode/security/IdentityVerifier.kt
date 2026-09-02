// @dikaacode
package com.dikacode.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.dikacode.BuildConfig
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Cryptographic application identity system.
 * Verifies signing certificate, package identity, and build identity.
 */
object IdentityVerifier {
    private const val TAG = "IdentityVerifier"

    // ─── Trusted identity ─────────────────────────────────────────
    // These are the ORIGINAL values — any deviation indicates tampering.

    const val TRUSTED_PACKAGE_NAME = "com.dika.fastrecorder"
    const val TRUSTED_APP_LABEL = "FastRecorder"
    const val TRUSTED_BUILD_TYPE = "release"
    const val TRUSTED_VERSION_CODE = 1

    // Trusted signing certificate SHA-256 fingerprints (colon-separated hex)
    // Set this after first official build
    private var TRUSTED_CERT_HASHES = mutableSetOf<String>()

    // ─── Certificate rotation ─────────────────────────────────────
    // Previous trusted certificates (for rotation support)
    private val ROTATION_CERT_HASHES = mutableSetOf<String>()

    // ─── Identity cache ───────────────────────────────────────────
    private val identityCache = ConcurrentHashMap<String, Any>()

    /**
     * Initialize with trusted certificate hashes.
     */
    fun initialize(trustedHashes: Set<String>, rotationHashes: Set<String> = emptySet()) {
        TRUSTED_CERT_HASHES.addAll(trustedHashes)
        ROTATION_CERT_HASHES.addAll(rotationHashes)
        Log.i(TAG, "Identity initialized with ${trustedHashes.size} trusted, ${rotationHashes.size} rotation certs")
    }

    /**
     * Add a trusted certificate hash after official build verification.
     */
    fun addTrustedCertificate(hash: String) {
        TRUSTED_CERT_HASHES.add(hash)
    }

    /**
     * Perform full identity verification.
     * Returns list of signals if any anomalies detected.
     */
    fun verifyIdentity(context: Context): List<SecuritySignal> {
        val signals = mutableListOf<SecuritySignal>()

        // 1. Package name verification
        signals.addAll(verifyPackageName(context))

        // 2. Application label verification
        signals.addAll(verifyAppLabel(context))

        // 3. Signing certificate verification
        signals.addAll(verifySigningCertificate(context))

        // 4. Build metadata verification
        signals.addAll(verifyBuildMetadata(context))

        return signals
    }

    /**
     * Verify package name matches trusted value.
     */
    private fun verifyPackageName(context: Context): List<SecuritySignal> {
        val currentPackage = context.packageName
        return if (currentPackage != TRUSTED_PACKAGE_NAME) {
            listOf(SecuritySignal.SignalPackageNameChanged(
                expected = TRUSTED_PACKAGE_NAME,
                actual = currentPackage
            ))
        } else {
            emptyList()
        }
    }

    /**
     * Verify application label.
     */
    private fun verifyAppLabel(context: Context): List<SecuritySignal> {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            val label = context.packageManager.getApplicationLabel(appInfo).toString()
            if (label != TRUSTED_APP_LABEL) {
                listOf(SecuritySignal.SignalLabelChanged(
                    expected = TRUSTED_APP_LABEL,
                    actual = label
                ))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Verify signing certificate against trusted hashes.
     */
    private fun verifySigningCertificate(context: Context): List<SecuritySignal> {
        if (TRUSTED_CERT_HASHES.isEmpty()) {
            // First build — record the current certificate as trusted
            val currentHash = getSigningCertificateHash(context)
            if (currentHash != null) {
                TRUSTED_CERT_HASHES.add(currentHash)
                Log.i(TAG, "First build — recording certificate hash: ${currentHash.take(16)}...")
            }
            return emptyList()
        }

        val currentHash = getSigningCertificateHash(context) ?: run {
            return listOf(SecuritySignal.SignalApkResigned("Unable to read signing certificate"))
        }

        val isTrusted = TRUSTED_CERT_HASHES.contains(currentHash) ||
                ROTATION_CERT_HASHES.contains(currentHash)

        return if (!isTrusted) {
            listOf(SecuritySignal.SignalCertificateMismatch(
                expectedHash = TRUSTED_CERT_HASHES.first(),
                actualHash = currentHash
            ))
        } else {
            emptyList()
        }
    }

    /**
     * Get SHA-256 hash of APK signing certificate.
     */
    fun getSigningCertificateHash(context: Context): String? {
        return try {
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

            val sig = signatures?.firstOrNull() ?: return null
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(sig.toByteArray())
            hash.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get signing certificate hash", e)
            null
        }
    }

    /**
     * Verify build metadata.
     */
    private fun verifyBuildMetadata(context: Context): List<SecuritySignal> {
        val signals = mutableListOf<SecuritySignal>()

        try {
            val buildConfigClass = Class.forName("com.dikacode.BuildConfig")

            // Verify build type
            val buildType = buildConfigClass.getField("BUILD_TYPE").get(null) as? String
            val isDebuggable = (context.applicationInfo.flags and
                    android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

            if (isDebuggable && buildType == "release") {
                signals.add(SecuritySignal.SignalUnexpectedBuildMetadata(
                    "Debuggable flag set but build type is release"
                ))
            }

            // Verify version code
            val versionCode = buildConfigClass.getField("VERSION_CODE").get(null) as? Int
            if (versionCode != null && versionCode != TRUSTED_VERSION_CODE) {
                signals.add(SecuritySignal.SignalUnexpectedBuildMetadata(
                    "Version code mismatch: expected=$TRUSTED_VERSION_CODE, actual=$versionCode"
                ))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Build metadata verification failed", e)
        }

        return signals
    }

    /**
     * Get current application identity for logging/display.
     */
    fun getApplicationIdentity(context: Context): ApplicationIdentity {
        return ApplicationIdentity(
            packageName = context.packageName,
            appLabel = try {
                val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
                context.packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) { "Unknown" },
            versionCode = TRUSTED_VERSION_CODE,
            buildType = BuildConfig.BUILD_TYPE,
            certificateHash = getSigningCertificateHash(context) ?: "Unknown",
            isTrusted = verifySigningCertificate(context).isEmpty()
        )
    }

    data class ApplicationIdentity(
        val packageName: String,
        val appLabel: String,
        val versionCode: Int,
        val buildType: String,
        val certificateHash: String,
        val isTrusted: Boolean
    )
}
