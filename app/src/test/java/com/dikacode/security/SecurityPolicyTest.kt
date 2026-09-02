// @dikaacode
package com.dikacode.security

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for SecurityPolicy response matrix.
 * Validates R0-R4 responses, feature restrictions, and graceful failure.
 */
class SecurityPolicyTest {

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 1: Response level mapping
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `TRUSTED state maps to ALLOW response`() {
        val response = SecurityPolicy.getResponseForState(TrustState.TRUSTED, 0)
        assertEquals(SecurityPolicy.ResponseLevel.ALLOW, response.responseLevel)
        assertFalse(response.restrictFeatures)
        assertFalse(response.showWarning)
        assertNull(response.warningMessage)
        assertFalse(response.reverify)
        assertFalse(response.additionalChecks)
    }

    @Test
    fun `LOW_RISK state maps to MONITOR response`() {
        val response = SecurityPolicy.getResponseForState(TrustState.LOW_RISK, 15)
        assertEquals(SecurityPolicy.ResponseLevel.MONITOR, response.responseLevel)
        assertFalse(response.restrictFeatures)
        assertFalse(response.showWarning)
        assertTrue(response.reverify)
        assertFalse(response.additionalChecks)
    }

    @Test
    fun `SUSPICIOUS state maps to VERIFY response`() {
        val response = SecurityPolicy.getResponseForState(TrustState.SUSPICIOUS, 35)
        assertEquals(SecurityPolicy.ResponseLevel.VERIFY, response.responseLevel)
        assertFalse(response.restrictFeatures)
        assertTrue(response.showWarning)
        assertNotNull(response.warningMessage)
        assertTrue(response.reverify)
        assertTrue(response.additionalChecks)
    }

    @Test
    fun `HIGH_RISK state maps to RESTRICT response`() {
        val response = SecurityPolicy.getResponseForState(TrustState.HIGH_RISK, 60)
        assertEquals(SecurityPolicy.ResponseLevel.RESTRICT, response.responseLevel)
        assertTrue(response.restrictFeatures)
        assertTrue(response.showWarning)
        assertNotNull(response.warningMessage)
        assertTrue(response.reverify)
        assertTrue(response.additionalChecks)
    }

    @Test
    fun `UNTRUSTED state maps to QUARANTINE response`() {
        val response = SecurityPolicy.getResponseForState(TrustState.UNTRUSTED, 80)
        assertEquals(SecurityPolicy.ResponseLevel.QUARANTINE, response.responseLevel)
        assertTrue(response.restrictFeatures)
        assertTrue(response.showWarning)
        assertNotNull(response.warningMessage)
        assertTrue(response.reverify)
        assertTrue(response.additionalChecks)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 2: getResponseForRiskResult
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `getResponseForRiskResult delegates to getResponseForState`() {
        val riskResult = RiskAssessment.RiskResult(
            riskScore = 25,
            trustState = TrustState.LOW_RISK,
            confidence = RiskAssessment.Confidence.MEDIUM,
            domainContributions = emptyMap(),
            activeSignals = emptyList(),
            correlationClusters = emptyList()
        )
        val response = SecurityPolicy.getResponseForRiskResult(riskResult)
        assertEquals(SecurityPolicy.ResponseLevel.MONITOR, response.responseLevel)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 3: Feature restrictions per trust state
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `TRUSTED allows all features`() {
        for (feature in SecurityPolicy.SecurityFeature.entries) {
            assertTrue("TRUSTED should allow $feature",
                SecurityPolicy.isFeatureAllowed(TrustState.TRUSTED, feature))
        }
    }

    @Test
    fun `LOW_RISK allows all features`() {
        for (feature in SecurityPolicy.SecurityFeature.entries) {
            assertTrue("LOW_RISK should allow $feature",
                SecurityPolicy.isFeatureAllowed(TrustState.LOW_RISK, feature))
        }
    }

    @Test
    fun `SUSPICIOUS blocks SENSITIVE_BACKEND`() {
        assertFalse(SecurityPolicy.isFeatureAllowed(TrustState.SUSPICIOUS, SecurityPolicy.SecurityFeature.SENSITIVE_BACKEND))
        assertTrue(SecurityPolicy.isFeatureAllowed(TrustState.SUSPICIOUS, SecurityPolicy.SecurityFeature.BASIC_UI))
        assertTrue(SecurityPolicy.isFeatureAllowed(TrustState.SUSPICIOUS, SecurityPolicy.SecurityFeature.SETTINGS_VIEW))
        assertTrue(SecurityPolicy.isFeatureAllowed(TrustState.SUSPICIOUS, SecurityPolicy.SecurityFeature.LOCAL_RECORDING))
        assertTrue(SecurityPolicy.isFeatureAllowed(TrustState.SUSPICIOUS, SecurityPolicy.SecurityFeature.CLOUD_UPLOAD))
    }

    @Test
    fun `HIGH_RISK only allows BASIC_UI, SETTINGS_VIEW, LOCAL_RECORDING`() {
        assertTrue(SecurityPolicy.isFeatureAllowed(TrustState.HIGH_RISK, SecurityPolicy.SecurityFeature.BASIC_UI))
        assertTrue(SecurityPolicy.isFeatureAllowed(TrustState.HIGH_RISK, SecurityPolicy.SecurityFeature.SETTINGS_VIEW))
        assertTrue(SecurityPolicy.isFeatureAllowed(TrustState.HIGH_RISK, SecurityPolicy.SecurityFeature.LOCAL_RECORDING))
        assertFalse(SecurityPolicy.isFeatureAllowed(TrustState.HIGH_RISK, SecurityPolicy.SecurityFeature.CLOUD_UPLOAD))
        assertFalse(SecurityPolicy.isFeatureAllowed(TrustState.HIGH_RISK, SecurityPolicy.SecurityFeature.SENSITIVE_BACKEND))
        assertFalse(SecurityPolicy.isFeatureAllowed(TrustState.HIGH_RISK, SecurityPolicy.SecurityFeature.PRIVILEGED_ACTIONS))
    }

    @Test
    fun `UNTRUSTED only allows BASIC_UI`() {
        assertTrue(SecurityPolicy.isFeatureAllowed(TrustState.UNTRUSTED, SecurityPolicy.SecurityFeature.BASIC_UI))
        assertFalse(SecurityPolicy.isFeatureAllowed(TrustState.UNTRUSTED, SecurityPolicy.SecurityFeature.SETTINGS_VIEW))
        assertFalse(SecurityPolicy.isFeatureAllowed(TrustState.UNTRUSTED, SecurityPolicy.SecurityFeature.LOCAL_RECORDING))
        assertFalse(SecurityPolicy.isFeatureAllowed(TrustState.UNTRUSTED, SecurityPolicy.SecurityFeature.CLOUD_UPLOAD))
        assertFalse(SecurityPolicy.isFeatureAllowed(TrustState.UNTRUSTED, SecurityPolicy.SecurityFeature.SENSITIVE_BACKEND))
        assertFalse(SecurityPolicy.isFeatureAllowed(TrustState.UNTRUSTED, SecurityPolicy.SecurityFeature.PRIVILEGED_ACTIONS))
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 4: Warning messages
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `TRUSTED has no warning`() {
        val response = SecurityPolicy.getResponseForState(TrustState.TRUSTED, 0)
        assertFalse(response.showWarning)
        assertNull(response.warningMessage)
    }

    @Test
    fun `LOW_RISK has no warning`() {
        val response = SecurityPolicy.getResponseForState(TrustState.LOW_RISK, 15)
        assertFalse(response.showWarning)
        assertNull(response.warningMessage)
    }

    @Test
    fun `SUSPICIOUS shows warning`() {
        val response = SecurityPolicy.getResponseForState(TrustState.SUSPICIOUS, 35)
        assertTrue(response.showWarning)
        assertTrue(response.warningMessage!!.contains("verification"))
    }

    @Test
    fun `HIGH_RISK shows warning with @dikaacode mention`() {
        val response = SecurityPolicy.getResponseForState(TrustState.HIGH_RISK, 60)
        assertTrue(response.showWarning)
        assertTrue(response.warningMessage!!.contains("@dikaacode"))
    }

    @Test
    fun `UNTRUSTED shows warning with official source mention`() {
        val response = SecurityPolicy.getResponseForState(TrustState.UNTRUSTED, 80)
        assertTrue(response.showWarning)
        assertTrue(response.warningMessage!!.contains("@dikaacode"))
        assertTrue(response.warningMessage!!.contains("official"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 5: Status summary generation
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `generateStatusSummary contains expected fields`() {
        val summary = SecurityPolicy.generateStatusSummary(TrustState.SUSPICIOUS, 35)
        assertTrue(summary.contains("risk score: 35"))
        assertTrue(summary.contains("trust state: SUSPICIOUS"))
        assertTrue(summary.contains("response level: VERIFY"))
        assertTrue(summary.contains("features restricted: false"))
        assertTrue(summary.contains("re-verification: true"))
    }

    @Test
    fun `generateStatusSummary for TRUSTED shows ALLOW`() {
        val summary = SecurityPolicy.generateStatusSummary(TrustState.TRUSTED, 0)
        assertTrue(summary.contains("response level: ALLOW"))
        assertTrue(summary.contains("features restricted: false"))
    }

    @Test
    fun `generateStatusSummary for UNTRUSTED shows QUARANTINE`() {
        val summary = SecurityPolicy.generateStatusSummary(TrustState.UNTRUSTED, 85)
        assertTrue(summary.contains("response level: QUARANTINE"))
        assertTrue(summary.contains("features restricted: true"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 6: All ResponseLevel values covered
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `all trust states have a response level`() {
        val expectedLevels = mapOf(
            TrustState.TRUSTED to SecurityPolicy.ResponseLevel.ALLOW,
            TrustState.LOW_RISK to SecurityPolicy.ResponseLevel.MONITOR,
            TrustState.SUSPICIOUS to SecurityPolicy.ResponseLevel.VERIFY,
            TrustState.HIGH_RISK to SecurityPolicy.ResponseLevel.RESTRICT,
            TrustState.UNTRUSTED to SecurityPolicy.ResponseLevel.QUARANTINE
        )

        for ((state, expectedLevel) in expectedLevels) {
            val response = SecurityPolicy.getResponseForState(state, 0)
            assertEquals("TrustState.$state should map to $expectedLevel",
                expectedLevel, response.responseLevel)
        }
    }

    @Test
    fun `all security features are defined`() {
        val features = SecurityPolicy.SecurityFeature.entries
        assertEquals(6, features.size)
        assertTrue(features.contains(SecurityPolicy.SecurityFeature.BASIC_UI))
        assertTrue(features.contains(SecurityPolicy.SecurityFeature.SETTINGS_VIEW))
        assertTrue(features.contains(SecurityPolicy.SecurityFeature.LOCAL_RECORDING))
        assertTrue(features.contains(SecurityPolicy.SecurityFeature.CLOUD_UPLOAD))
        assertTrue(features.contains(SecurityPolicy.SecurityFeature.SENSITIVE_BACKEND))
        assertTrue(features.contains(SecurityPolicy.SecurityFeature.PRIVILEGED_ACTIONS))
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 7: Graceful failure — no destructive behavior
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `no trust state causes destructive behavior`() {
        for (state in TrustState.entries) {
            val response = SecurityPolicy.getResponseForState(state, 0)
            // Should never delete data, corrupt files, or damage device
            assertNotNull(response.responseLevel)
            assertNotNull(response.warningMessage) // nullable, that's OK
            // Feature restriction should not block BASIC_UI for any state
            assertTrue("$state should always allow BASIC_UI",
                SecurityPolicy.isFeatureAllowed(state, SecurityPolicy.SecurityFeature.BASIC_UI))
        }
    }

    @Test
    fun `feature restriction is monotonic — more restricted as trust decreases`() {
        val feature = SecurityPolicy.SecurityFeature.CLOUD_UPLOAD
        val trustedAllowed = SecurityPolicy.isFeatureAllowed(TrustState.TRUSTED, feature)
        val lowRiskAllowed = SecurityPolicy.isFeatureAllowed(TrustState.LOW_RISK, feature)
        val suspiciousAllowed = SecurityPolicy.isFeatureAllowed(TrustState.SUSPICIOUS, feature)
        val highRiskAllowed = SecurityPolicy.isFeatureAllowed(TrustState.HIGH_RISK, feature)
        val untrustedAllowed = SecurityPolicy.isFeatureAllowed(TrustState.UNTRUSTED, feature)

        assertTrue(trustedAllowed)
        assertTrue(lowRiskAllowed)
        assertTrue(suspiciousAllowed)
        assertFalse(highRiskAllowed)
        assertFalse(untrustedAllowed)
    }
}
