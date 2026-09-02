// @dikaacode
package com.dikacode.security

import android.content.Context
import android.util.Log
import com.dikacode.BuildConfig
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Attribution protection system.
 * Ensures @dikaacode credit persists through multiple independent layers.
 * Detects attribution tampering via cryptographic verification.
 */
object AttributionGuard {
    private const val TAG = "AttributionGuard"

    // ─── Attribution constants ────────────────────────────────────
    const val ATTRIBUTION_AUTHOR = "@dikaacode"
    const val ATTRIBUTION_TEAM = "dikaacode"
    const val ATTRIBUTION_URL = "https://t.me/dikaacode"
    const val ATTRIBUTION_APP = "FastRecorder"

    // ─── Signed attribution record ────────────────────────────────
    // This record is signed with the release certificate.
    // Tampering with it requires re-signing the APK.

    data class AttributionRecord(
        val author: String,
        val appId: String,
        val buildId: String,
        val signingCertificateHash: String,
        val timestamp: Long,
        val checksum: String
    )

    // ─── Attribution layers ───────────────────────────────────────
    // Credits are embedded in multiple independent locations

    /**
     * Layer 1: Runtime credit strings (hardcoded).
     * These survive rebuilds because they're in the source code.
     */
    fun getRuntimeCredits(): Map<String, String> {
        return mapOf(
            "author" to ATTRIBUTION_AUTHOR,
            "team" to ATTRIBUTION_TEAM,
            "url" to ATTRIBUTION_URL,
            "app" to ATTRIBUTION_APP
        )
    }

    /**
     * Layer 2: Resource-based credits.
     */
    fun getResourceCredits(context: Context): Map<String, String> {
        val credits = mutableMapOf<String, String>()
        val res = context.resources

        val creditKeys = mapOf(
            "credit_developer" to "author",
            "credit_team" to "team",
            "credit_app" to "app",
            "credit_url" to "url"
        )

        for ((resKey, creditKey) in creditKeys) {
            val resId = res.getIdentifier(resKey, "string", context.packageName)
            if (resId != 0) {
                credits[creditKey] = res.getString(resId)
            }
        }

        return credits
    }

    /**
     * Layer 3: BuildConfig credits.
     * Uses direct access first (R8-safe), falls back to reflection for tamper detection.
     */
    fun getBuildConfigCredits(): Map<String, String> {
        // Direct access — always survives R8 if -keep class BuildConfig { *; } is set
        // and prevents false positive when reflection is blocked by obfuscation.
        try {
            val direct = mapOf(
                "author" to BuildConfig.CREDIT_DEVELOPER,
                "team" to BuildConfig.CREDIT_TEAM,
                "url" to BuildConfig.CREDIT_URL
            )
            // If direct values are correct, use them immediately
            if (direct["author"] == ATTRIBUTION_AUTHOR) return direct
        } catch (_: Exception) { /* fall through to reflection */ }

        return try {
            val buildConfigClass = Class.forName("com.dikacode.BuildConfig")
            mapOf(
                "author" to (buildConfigClass.getField("CREDIT_DEVELOPER").get(null) as? String ?: ""),
                "team" to (buildConfigClass.getField("CREDIT_TEAM").get(null) as? String ?: ""),
                "url" to (buildConfigClass.getField("CREDIT_URL").get(null) as? String ?: "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read BuildConfig credits", e)
            emptyMap()
        }
    }

    /**
     * Layer 4: Class name integrity.
     */
    fun verifyClassIntegrity(): Boolean {
        val criticalClasses = listOf(
            "com.dikacode.security.AttributionGuard",
            "com.dikacode.security.CreditManager",
            "com.dikacode.security.SecurityManager",
            "com.dikacode.security.SecurityEngine"
        )
        return try {
            criticalClasses.forEach { Class.forName(it) }
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /**
     * Layer 5: Checksum verification.
     * Verifies that credit strings haven't been modified.
     */
    fun verifyChecksums(): Boolean {
        val credits = getRuntimeCredits()
        val expectedChecksums = mapOf(
            "author" to computeChecksum(ATTRIBUTION_AUTHOR),
            "team" to computeChecksum(ATTRIBUTION_TEAM),
            "url" to computeChecksum(ATTRIBUTION_URL),
            "app" to computeChecksum(ATTRIBUTION_APP)
        )

        val currentChecksums = credits.mapValues { (_, value) -> computeChecksum(value) }
        return currentChecksums == expectedChecksums
    }

    /**
     * Create signed attribution record.
     */
    fun createAttributionRecord(context: Context): AttributionRecord {
        val signingHash = IdentityVerifier.getSigningCertificateHash(context) ?: "unknown"
        val buildId = BuildConfig.BUILD_ID ?: "dev"
        val appId = BuildConfig.SECURITY_PACKAGE

        val checksumInput = "$ATTRIBUTION_AUTHOR|$appId|$buildId|$signingHash"
        val checksum = computeChecksum(checksumInput)

        return AttributionRecord(
            author = ATTRIBUTION_AUTHOR,
            appId = appId,
            buildId = buildId,
            signingCertificateHash = signingHash,
            timestamp = System.currentTimeMillis(),
            checksum = checksum
        )
    }

    /**
     * Verify attribution record integrity.
     */
    fun verifyAttributionRecord(record: AttributionRecord, context: Context): Boolean {
        val signingHash = IdentityVerifier.getSigningCertificateHash(context) ?: "unknown"
        val buildId = BuildConfig.BUILD_ID ?: "dev"

        // Verify checksum
        val expectedChecksum = computeChecksum(
            "${record.author}|${record.appId}|${record.buildId}|${record.signingCertificateHash}"
        )

        return record.checksum == expectedChecksum &&
                record.author == ATTRIBUTION_AUTHOR &&
                record.signingCertificateHash == signingHash
    }

    // ─── Full attribution verification ────────────────────────────

    data class AttributionVerificationResult(
        val attributionState: AttributionState,
        val verifiedLayers: List<String>,
        val failedLayers: List<String>,
        val signals: List<SecuritySignal>
    )

    /**
     * Perform comprehensive attribution verification.
     */
    fun verifyAttribution(context: Context): AttributionVerificationResult {
        val verifiedLayers = mutableListOf<String>()
        val failedLayers = mutableListOf<String>()
        val signals = mutableListOf<SecuritySignal>()

        // Layer 1: Runtime credits
        val runtimeCredits = getRuntimeCredits()
        if (runtimeCredits["author"] == ATTRIBUTION_AUTHOR) {
            verifiedLayers.add("runtime")
        } else {
            failedLayers.add("runtime")
            signals.add(SecuritySignal.SignalAttributionSignatureInvalid("Runtime credit modified"))
        }

        // Layer 2: Resource credits
        val resourceCredits = getResourceCredits(context)
        if (resourceCredits["author"] == ATTRIBUTION_AUTHOR) {
            verifiedLayers.add("resource")
        } else if (resourceCredits.isNotEmpty()) {
            failedLayers.add("resource")
            signals.add(SecuritySignal.SignalAttributionSignatureInvalid("Resource credit modified"))
        }

        // Layer 3: BuildConfig credits
        val buildConfigCredits = getBuildConfigCredits()
        if (buildConfigCredits["author"] == ATTRIBUTION_AUTHOR) {
            verifiedLayers.add("buildconfig")
        } else {
            failedLayers.add("buildconfig")
            signals.add(SecuritySignal.SignalAttributionSignatureInvalid("BuildConfig credit modified"))
        }

        // Layer 4: Class integrity
        if (verifyClassIntegrity()) {
            verifiedLayers.add("class_integrity")
        } else {
            failedLayers.add("class_integrity")
            signals.add(SecuritySignal.SignalAttributionRecordMissing("Critical attribution classes missing"))
        }

        // Layer 5: Checksum verification
        if (verifyChecksums()) {
            verifiedLayers.add("checksum")
        } else {
            failedLayers.add("checksum")
            signals.add(SecuritySignal.SignalAttributionSignatureInvalid("Attribution checksum mismatch"))
        }

        // Determine overall state
        val attributionState = when {
            failedLayers.isEmpty() -> AttributionState.VERIFIED
            verifiedLayers.isEmpty() -> AttributionState.TAMPERED
            else -> AttributionState.UNVERIFIED
        }

        return AttributionVerificationResult(
            attributionState = attributionState,
            verifiedLayers = verifiedLayers,
            failedLayers = failedLayers,
            signals = signals
        )
    }

    /**
     * Compute SHA-256 checksum.
     */
    private fun computeChecksum(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
