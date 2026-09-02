// @dikaacode
package com.dikacode.security

import android.content.Context

/**
 * Legacy security manager — delegates to SecurityEngine.
 * Kept for backward compatibility with existing code.
 */
object SecurityManager {

    const val DEVELOPER_CREDIT = "@dikaacode"
    const val APP_AUTHORITY = "dikaacode"
    const val SECURITY_VERSION = 2

    /**
     * Legacy full check — delegates to SecurityEngine.
     */
    fun performFullCheck(context: Context): SecurityResult {
        val report = SecurityEngine.verify(context)
        return SecurityResult(
            allChecksPassed = report.trustState == TrustState.TRUSTED,
            isTampered = report.trustState.ordinal >= TrustState.HIGH_RISK.ordinal,
            checks = report.signals.map { signal ->
                CheckResult(
                    name = signal.domain.name,
                    passed = true,
                    isCritical = signal.domain == RiskAssessment.SignalDomain.SIGNATURE ||
                            signal.domain == RiskAssessment.SignalDomain.BINARY_INTEGRITY,
                    detail = signal.detail
                )
            }
        )
    }
}

data class SecurityResult(
    val allChecksPassed: Boolean,
    val isTampered: Boolean,
    val checks: List<CheckResult>
)

data class CheckResult(
    val name: String,
    val passed: Boolean,
    val isCritical: Boolean,
    val detail: String
)
