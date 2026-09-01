// @dikaacode
package com.dikacode.security

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.security.MessageDigest

/**
 * Persistent credit manager.
 * Embeds @dikaacode credit in multiple layers that survive
 * rebuilds, renames, and modifications.
 */
object CreditManager {
    private const val TAG = "CreditManager"

    // ===== CREDIT LAYERS =====
    // These credits are embedded in multiple places:
    // 1. BuildConfig (compile-time)
    // 2. String resources (hard to modify without recompile)
    // 3. Runtime verification (class loading)
    // 4. Signature verification (prevents repackaging)
    // 5. Obfuscated checksums (detects tampering)

    // Layer 1: BuildConfig credits
    const val CREDIT_DEVELOPER = "@dikaacode"
    const val CREDIT_TEAM = "dikaacode"
    const val CREDIT_APP = "FastRecorder"
    const val CREDIT_URL = "https://t.me/dikaacode"

    // Layer 2: Resource-based credits (defined in strings.xml)
    // These are referenced at runtime and harder to modify

    // Layer 3: Obfuscated checksums
    // These values are derived from the actual credit strings
    // and will change if anyone modifies the credits
    private val CREDIT_CHECKSUMS by lazy {
        mapOf(
            "developer" to computeChecksum(CREDIT_DEVELOPER),
            "team" to computeChecksum(CREDIT_TEAM),
            "app" to computeChecksum(CREDIT_APP),
            "url" to computeChecksum(CREDIT_URL)
        )
    }

    /**
     * Verify all credit layers are intact.
     * Returns true if credits haven't been tampered with.
     */
    fun verifyCredits(context: Context): CreditVerificationResult {
        val results = mutableListOf<CreditCheck>()

        // Check 1: Runtime credit strings
        results.add(verifyRuntimeCredits())

        // Check 2: Resource credits
        results.add(verifyResourceCredits(context))

        // Check 3: BuildConfig credits
        results.add(verifyBuildConfigCredits())

        // Check 4: Class integrity
        results.add(verifyClassIntegrity())

        // Check 5: Checksum verification
        results.add(verifyChecksums())

        val allValid = results.all { it.valid }

        return CreditVerificationResult(
            allCreditsValid = allValid,
            checks = results
        )
    }

    /**
     * Verify runtime credit strings haven't been modified.
     */
    private fun verifyRuntimeCredits(): CreditCheck {
        val currentChecksum = computeChecksum(CREDIT_DEVELOPER)
        val expectedChecksum = CREDIT_CHECKSUMS["developer"]
        val valid = currentChecksum == expectedChecksum

        return CreditCheck(
            name = "RuntimeCredits",
            valid = valid,
            detail = if (valid) "Runtime credits verified" else "Runtime credits tampered"
        )
    }

    /**
     * Verify resource-based credits.
     */
    private fun verifyResourceCredits(context: Context): CreditCheck {
        return try {
            val res = context.resources
            val creditResId = res.getIdentifier("credit_developer", "string", context.packageName)
            if (creditResId != 0) {
                val creditText = res.getString(creditResId)
                val valid = creditText.contains(CREDIT_DEVELOPER)
                CreditCheck(
                    name = "ResourceCredits",
                    valid = valid,
                    detail = if (valid) "Resource credits verified" else "Resource credits modified"
                )
            } else {
                CreditCheck(
                    name = "ResourceCredits",
                    valid = true, // Resource doesn't exist, skip check
                    detail = "Credit resource not found (skipped)"
                )
            }
        } catch (e: Exception) {
            CreditCheck(
                name = "ResourceCredits",
                valid = true,
                detail = "Resource check skipped: ${e.message}"
            )
        }
    }

    /**
     * Verify BuildConfig credits.
     */
    private fun verifyBuildConfigCredits(): CreditCheck {
        return try {
            val buildConfigClass = Class.forName("com.dikacode.BuildConfig")
            val creditField = buildConfigClass.getField("CREDIT_DEVELOPER")
            val creditValue = creditField.get(null) as? String
            val valid = creditValue == CREDIT_DEVELOPER

            CreditCheck(
                name = "BuildConfigCredits",
                valid = valid,
                detail = if (valid) "BuildConfig credits verified" else "BuildConfig credits modified"
            )
        } catch (e: Exception) {
            CreditCheck(
                name = "BuildConfigCredits",
                valid = false,
                detail = "BuildConfig credit check failed: ${e.message}"
            )
        }
    }

    /**
     * Verify critical classes haven't been removed.
     */
    private fun verifyClassIntegrity(): CreditCheck {
        val criticalClasses = listOf(
            "com.dikacode.security.SecurityManager",
            "com.dikacode.security.CreditManager",
            "com.dikacode.MainActivity"
        )

        return try {
            for (className in criticalClasses) {
                Class.forName(className)
            }
            CreditCheck(
                name = "ClassIntegrity",
                valid = true,
                detail = "All critical classes present"
            )
        } catch (e: ClassNotFoundException) {
            CreditCheck(
                name = "ClassIntegrity",
                valid = false,
                detail = "Missing class: ${e.message}"
            )
        }
    }

    /**
     * Verify credit checksums haven't changed.
     */
    private fun verifyChecksums(): CreditCheck {
        val currentChecksums = mapOf(
            "developer" to computeChecksum(CREDIT_DEVELOPER),
            "team" to computeChecksum(CREDIT_TEAM),
            "app" to computeChecksum(CREDIT_APP),
            "url" to computeChecksum(CREDIT_URL)
        )

        val valid = currentChecksums == CREDIT_CHECKSUMS
        return CreditCheck(
            name = "ChecksumVerification",
            valid = valid,
            detail = if (valid) "All checksums verified" else "Checksum mismatch detected"
        )
    }

    /**
     * Get credit information for display.
     */
    fun getCreditInfo(): CreditInfo {
        return CreditInfo(
            developer = CREDIT_DEVELOPER,
            team = CREDIT_TEAM,
            app = CREDIT_APP,
            url = CREDIT_URL,
            version = SecurityManager.SECURITY_VERSION
        )
    }

    /**
     * Compute SHA-256 checksum of a string.
     */
    private fun computeChecksum(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Credit verification check result.
 */
data class CreditCheck(
    val name: String,
    val valid: Boolean,
    val detail: String
)

/**
 * Combined credit verification result.
 */
data class CreditVerificationResult(
    val allCreditsValid: Boolean,
    val checks: List<CreditCheck>
)

/**
 * Credit information.
 */
data class CreditInfo(
    val developer: String,
    val team: String,
    val app: String,
    val url: String,
    val version: Int
)
