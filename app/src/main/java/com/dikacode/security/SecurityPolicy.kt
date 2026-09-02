// @dikaacode
package com.dikacode.security

import android.util.Log

/**
 * Security policy engine — R0-R4 response matrix.
 * Defines what happens at each trust level.
 *
 * Response levels:
 *   R0: allow      — normal operation
 *   R1: monitor    — silent re-verification, no user disruption
 *   R2: verify     — additional verification rounds
 *   R3: restrict   — disable security-sensitive features
 *   R4: quarantine — untrusted/restricted mode
 */
object SecurityPolicy {
    private const val TAG = "SecurityPolicy"

    /**
     * Policy response for a given trust state.
     */
    data class PolicyResponse(
        val responseLevel: ResponseLevel,
        val restrictFeatures: Boolean,
        val showWarning: Boolean,
        val warningMessage: String?,
        val reverify: Boolean,
        val additionalChecks: Boolean
    )

    enum class ResponseLevel {
        ALLOW,      // R0
        MONITOR,    // R1
        VERIFY,     // R2
        RESTRICT,   // R3
        QUARANTINE  // R4
    }

    /**
     * Determine the policy response for a given trust state.
     */
    fun getResponseForState(trustState: TrustState, riskScore: Int): PolicyResponse {
        return when (trustState) {
            TrustState.TRUSTED -> PolicyResponse(
                responseLevel = ResponseLevel.ALLOW,
                restrictFeatures = false,
                showWarning = false,
                warningMessage = null,
                reverify = false,
                additionalChecks = false
            )

            TrustState.LOW_RISK -> PolicyResponse(
                responseLevel = ResponseLevel.MONITOR,
                restrictFeatures = false,
                showWarning = false,
                warningMessage = null,
                reverify = true,
                additionalChecks = false
            )

            TrustState.SUSPICIOUS -> PolicyResponse(
                responseLevel = ResponseLevel.VERIFY,
                restrictFeatures = false,
                showWarning = true,
                warningMessage = "Security notice: Additional verification active.",
                reverify = true,
                additionalChecks = true
            )

            TrustState.HIGH_RISK -> PolicyResponse(
                responseLevel = ResponseLevel.RESTRICT,
                restrictFeatures = true,
                showWarning = true,
                warningMessage = "This build is not recognized as an official @dikaacode release. Some features are restricted.",
                reverify = true,
                additionalChecks = true
            )

            TrustState.UNTRUSTED -> PolicyResponse(
                responseLevel = ResponseLevel.QUARANTINE,
                restrictFeatures = true,
                showWarning = true,
                warningMessage = "Official attribution could not be verified. This build is not recognized as an official @dikaacode release. Please download from the official source.",
                reverify = true,
                additionalChecks = true
            )
        }
    }

    /**
     * Determine the policy response for a given risk result.
     */
    fun getResponseForRiskResult(riskResult: RiskAssessment.RiskResult): PolicyResponse {
        return getResponseForState(riskResult.trustState, riskResult.riskScore)
    }

    /**
     * Check if a specific feature is allowed under current policy.
     */
    fun isFeatureAllowed(trustState: TrustState, feature: SecurityFeature): Boolean {
        return when (trustState) {
            TrustState.TRUSTED -> true
            TrustState.LOW_RISK -> true
            TrustState.SUSPICIOUS -> feature != SecurityFeature.SENSITIVE_BACKEND
            TrustState.HIGH_RISK -> feature in setOf(
                SecurityFeature.BASIC_UI,
                SecurityFeature.SETTINGS_VIEW,
                SecurityFeature.LOCAL_RECORDING
            )
            TrustState.UNTRUSTED -> feature == SecurityFeature.BASIC_UI
        }
    }

    enum class SecurityFeature {
        BASIC_UI,
        SETTINGS_VIEW,
        LOCAL_RECORDING,
        CLOUD_UPLOAD,
        SENSITIVE_BACKEND,
        PRIVILEGED_ACTIONS
    }

    /**
     * Generate a user-friendly summary of the current security status.
     */
    fun generateStatusSummary(trustState: TrustState, riskScore: Int): String {
        val policy = getResponseForState(trustState, riskScore)
        return buildString {
            appendLine("SECURITY POLICY STATUS")
            appendLine("─────────────────────────────")
            appendLine("risk score: $riskScore")
            appendLine("trust state: $trustState")
            appendLine("response level: ${policy.responseLevel}")
            appendLine("features restricted: ${policy.restrictFeatures}")
            appendLine("re-verification: ${policy.reverify}")
            appendLine("additional checks: ${policy.additionalChecks}")
            if (policy.warningMessage != null) {
                appendLine("user warning: YES")
            }
        }
    }

    /**
     * Log a security policy decision (privacy-safe).
     */
    fun logPolicyDecision(trustState: TrustState, riskScore: Int, decision: String) {
        Log.i(TAG, "Policy decision: state=$trustState, risk=$riskScore, decision=$decision")
    }
}
