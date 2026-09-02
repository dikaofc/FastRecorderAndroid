// @dikaacode
package com.dikacode.security

import java.util.UUID

/**
 * Security event schema — privacy-safe audit trail.
 * Never stores secrets, tokens, or sensitive user data.
 */
data class SecurityEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val buildId: String,
    val verificationType: VerificationType,
    val signalDomain: SignalDomain,
    val riskDelta: Int,
    val resultingRiskScore: Int,
    val resultingState: TrustState,
    val detail: String = ""
)

enum class VerificationType {
    STARTUP,
    PERIODIC,
    SENSITIVE_ACTION,
    BACKEND_REQUEST,
    IDENTITY_CHECK
}

/**
 * Trust state — represents the security posture of the running app.
 */
enum class TrustState {
    TRUSTED,        // R0: no threats detected
    LOW_RISK,       // R1: minor anomalies
    SUSPICIOUS,     // R2: multiple indicators of manipulation
    HIGH_RISK,      // R3: strong evidence of tampering
    UNTRUSTED       // R4: build is very likely untrusted
}

/**
 * Attribution integrity state.
 */
enum class AttributionState {
    VERIFIED,
    MISSING,
    TAMPERED,
    UNVERIFIED
}
