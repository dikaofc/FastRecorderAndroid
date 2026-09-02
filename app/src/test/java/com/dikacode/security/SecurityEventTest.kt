// @dikaacode
package com.dikacode.security

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for SecurityEvent and SecuritySignal data classes.
 * Validates event schema, signal detail strings, and data integrity.
 */
class SecurityEventTest {

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 1: SecurityEvent schema
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `SecurityEvent has unique eventId`() {
        val event1 = SecurityEvent(
            buildId = "test",
            verificationType = VerificationType.STARTUP,
            signalDomain = RiskAssessment.SignalDomain.IDENTITY,
            riskDelta = 5,
            resultingRiskScore = 5,
            resultingState = TrustState.TRUSTED
        )
        val event2 = SecurityEvent(
            buildId = "test",
            verificationType = VerificationType.STARTUP,
            signalDomain = RiskAssessment.SignalDomain.IDENTITY,
            riskDelta = 5,
            resultingRiskScore = 5,
            resultingState = TrustState.TRUSTED
        )
        assertNotEquals("Events should have unique IDs", event1.eventId, event2.eventId)
    }

    @Test
    fun `SecurityEvent stores privacy-safe data only`() {
        val event = SecurityEvent(
            buildId = "build-123",
            verificationType = VerificationType.STARTUP,
            signalDomain = RiskAssessment.SignalDomain.SIGNATURE,
            riskDelta = 60,
            resultingRiskScore = 60,
            resultingState = TrustState.HIGH_RISK,
            detail = "Certificate mismatch"
        )

        // Should not contain sensitive data
        assertFalse("Should not contain private keys", event.detail.lowercase().contains("private"))
        assertFalse("Should not contain passwords", event.detail.lowercase().contains("password"))
        assertFalse("Should not contain tokens", event.detail.lowercase().contains("token"))
        assertFalse("Should not contain secrets", event.detail.lowercase().contains("secret"))
    }

    @Test
    fun `SecurityEvent timestamp is reasonable`() {
        val before = System.currentTimeMillis()
        val event = SecurityEvent(
            buildId = "test",
            verificationType = VerificationType.PERIODIC,
            signalDomain = RiskAssessment.SignalDomain.RUNTIME,
            riskDelta = 25,
            resultingRiskScore = 25,
            resultingState = TrustState.LOW_RISK
        )
        val after = System.currentTimeMillis()

        assertTrue("Timestamp should be after 'before'", event.timestamp >= before)
        assertTrue("Timestamp should be before 'after'", event.timestamp <= after)
    }

    @Test
    fun `SecurityEvent can represent all verification types`() {
        for (type in VerificationType.entries) {
            val event = SecurityEvent(
                buildId = "test",
                verificationType = type,
                signalDomain = RiskAssessment.SignalDomain.BUILD,
                riskDelta = 0,
                resultingRiskScore = 0,
                resultingState = TrustState.TRUSTED
            )
            assertEquals(type, event.verificationType)
        }
    }

    @Test
    fun `SecurityEvent can represent all trust states`() {
        for (state in TrustState.entries) {
            val event = SecurityEvent(
                buildId = "test",
                verificationType = VerificationType.STARTUP,
                signalDomain = RiskAssessment.SignalDomain.IDENTITY,
                riskDelta = 0,
                resultingRiskScore = 0,
                resultingState = state
            )
            assertEquals(state, event.resultingState)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 2: SecuritySignal detail strings
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `SignalPackageNameChanged has descriptive detail`() {
        val signal = SecuritySignal.SignalPackageNameChanged("com.dika.fastrecorder", "com.attacker.app")
        assertTrue(signal.detail.contains("com.dika.fastrecorder"))
        assertTrue(signal.detail.contains("com.attacker.app"))
    }

    @Test
    fun `SignalCertificateMismatch has hash preview in detail`() {
        val signal = SecuritySignal.SignalCertificateMismatch(
            "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD",
            "11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44"
        )
        assertTrue("Should contain truncated expected hash", signal.detail.contains("AA:BB:CC:DD"))
    }

    @Test
    fun `SignalHookingFrameworkDetected has framework name`() {
        val signal = SecuritySignal.SignalHookingFrameworkDetected("frida")
        assertTrue(signal.detail.contains("frida"))
    }

    @Test
    fun `SignalNativeLibraryMismatch has library name`() {
        val signal = SecuritySignal.SignalNativeLibraryMismatch("libsecurity.so", "a", "b")
        assertTrue(signal.detail.contains("libsecurity.so"))
    }

    @Test
    fun `all signals have non-empty detail`() {
        val allSignals = listOf(
            SecuritySignal.SignalPackageNameChanged("a", "b"),
            SecuritySignal.SignalLabelChanged("a", "b"),
            SecuritySignal.SignalUnexpectedBuildMetadata("x"),
            SecuritySignal.SignalCertificateMismatch("a", "b"),
            SecuritySignal.SignalApkResigned("x"),
            SecuritySignal.SignalDexMismatch("a", "b"),
            SecuritySignal.SignalNativeLibraryMismatch("lib.so", "a", "b"),
            SecuritySignal.SignalIntegrityManifestMismatch("x"),
            SecuritySignal.SignalManifestIntegrityMismatch("x"),
            SecuritySignal.SignalAttributionSignatureInvalid("x"),
            SecuritySignal.SignalAttributionRecordMissing("x"),
            SecuritySignal.SignalDebuggerAttached(),
            SecuritySignal.SignalHookingFrameworkDetected("frida"),
            SecuritySignal.SignalInjectedLibraryDetected("lib.so"),
            SecuritySignal.SignalDebugBuildDetected(),
            SecuritySignal.SignalUnofficialBuild("x"),
            SecuritySignal.SignalEmulatorDetected(),
            SecuritySignal.SignalRootDetected(),
            SecuritySignal.SignalBackendUnofficialBuild("x")
        )

        for (signal in allSignals) {
            assertTrue("Signal ${signal::class.java.simpleName} should have non-empty detail",
                signal.detail.isNotEmpty())
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 3: RiskAssessment.RiskResult structure
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `RiskResult contains all required fields`() {
        val result = RiskAssessment.calculateRisk(emptyList())
        assertNotNull(result.riskScore)
        assertNotNull(result.trustState)
        assertNotNull(result.confidence)
        assertNotNull(result.domainContributions)
        assertNotNull(result.activeSignals)
        assertNotNull(result.correlationClusters)
    }

    @Test
    fun `RiskResult domain contributions map is consistent with active signals`() {
        val signals = listOf(
            SecuritySignal.SignalDebuggerAttached(),
            SecuritySignal.SignalRootDetected()
        )
        val result = RiskAssessment.calculateRisk(signals)

        // Domains with contributions should match signal domains
        val signalDomains = signals.map { it.domain }.toSet()
        val contributionDomains = result.domainContributions.keys

        for (domain in contributionDomains) {
            assertTrue("Domain $domain with contribution should have signals",
                domain in signalDomains)
        }
    }

    @Test
    fun `CorrelationCluster has valid structure`() {
        val signals = listOf(
            SecuritySignal.SignalPackageNameChanged("a", "b"),
            SecuritySignal.SignalLabelChanged("a", "b")
        )
        val result = RiskAssessment.calculateRisk(signals)

        for (cluster in result.correlationClusters) {
            assertTrue("Cluster ID should not be blank", cluster.clusterId.isNotBlank())
            assertTrue("Cluster should have at least 2 signals", cluster.signals.size >= 2)
            assertTrue("Root cause should not be blank", cluster.rootCause.isNotBlank())
        }
    }
}
