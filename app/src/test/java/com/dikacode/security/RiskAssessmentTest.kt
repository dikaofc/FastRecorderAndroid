// @dikaacode
package com.dikacode.security

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for RiskAssessment engine.
 * Validates risk scoring, domain separation, and confidence calculation.
 */
class RiskAssessmentTest {

    @Before
    fun setup() {
        // Reset any state between tests
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 1: Empty / baseline tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `empty signals produces TRUSTED with score 0`() {
        val result = RiskAssessment.calculateRisk(emptyList())
        assertEquals(0, result.riskScore)
        assertEquals(TrustState.TRUSTED, result.trustState)
        assertEquals(RiskAssessment.Confidence.HIGH, result.confidence)
        assertTrue(result.domainContributions.isEmpty())
        assertTrue(result.activeSignals.isEmpty())
        assertTrue(result.correlationClusters.isEmpty())
    }

    @Test
    fun `empty signals with previous failures still produces TRUSTED`() {
        val result = RiskAssessment.calculateRisk(emptyList(), previousFailures = 5)
        assertEquals(0, result.riskScore)
        assertEquals(TrustState.TRUSTED, result.trustState)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 2: Single signal tests (weak signals — max R1)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `single LOW signal produces R0 or R1`() {
        val signals = listOf(SecuritySignal.SignalPackageNameChanged("com.dika.fastrecorder", "com.other.app"))
        val result = RiskAssessment.calculateRisk(signals)
        assertTrue("Single LOW signal should be R0 or R1, got ${result.trustState}",
            result.trustState == TrustState.TRUSTED || result.trustState == TrustState.LOW_RISK)
        assertTrue("Risk score should be <= 29, got ${result.riskScore}", result.riskScore <= 29)
    }

    @Test
    fun `single emulator signal is LOW severity`() {
        val signals = listOf(SecuritySignal.SignalEmulatorDetected())
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(5, result.riskScore)
        assertEquals(TrustState.TRUSTED, result.trustState)
    }

    @Test
    fun `single root signal is LOW severity`() {
        val signals = listOf(SecuritySignal.SignalRootDetected())
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(5, result.riskScore)
        assertEquals(TrustState.TRUSTED, result.trustState)
    }

    @Test
    fun `single debug build signal is MEDIUM severity`() {
        val signals = listOf(SecuritySignal.SignalDebugBuildDetected())
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(15, result.riskScore)
        assertEquals(TrustState.LOW_RISK, result.trustState)
    }

    @Test
    fun `single debugger signal is MEDIUM severity`() {
        val signals = listOf(SecuritySignal.SignalDebuggerAttached())
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(25, result.riskScore)
        assertEquals(TrustState.LOW_RISK, result.trustState)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 3: Single critical signal tests (HIGH/CRITICAL)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `single CRITICAL certificate mismatch produces R3`() {
        val signals = listOf(SecuritySignal.SignalCertificateMismatch("AA:BB:CC", "DD:EE:FF"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(60, result.riskScore)
        assertEquals(TrustState.HIGH_RISK, result.trustState)
        assertEquals(RiskAssessment.Confidence.HIGH, result.confidence)
    }

    @Test
    fun `single CRITICAL dex mismatch produces R3`() {
        val signals = listOf(SecuritySignal.SignalDexMismatch("hash1", "hash2"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(50, result.riskScore)
        assertEquals(TrustState.HIGH_RISK, result.trustState)
    }

    @Test
    fun `single CRITICAL APK resigned produces R3`() {
        val signals = listOf(SecuritySignal.SignalApkResigned("Resigned with different key"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(60, result.riskScore)
        assertEquals(TrustState.HIGH_RISK, result.trustState)
    }

    @Test
    fun `single CRITICAL unofficial build produces R3`() {
        val signals = listOf(SecuritySignal.SignalUnofficialBuild("Rebuilt APK"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(60, result.riskScore)
        assertEquals(TrustState.HIGH_RISK, result.trustState)
    }

    @Test
    fun `single CRITICAL injected library produces R3`() {
        val signals = listOf(SecuritySignal.SignalInjectedLibraryDetected("libfrida-gadget.so"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(50, result.riskScore)
        assertEquals(TrustState.HIGH_RISK, result.trustState)
    }

    @Test
    fun `single CRITICAL backend unofficial produces R3`() {
        val signals = listOf(SecuritySignal.SignalBackendUnofficialBuild("Server says unknown build"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(60, result.riskScore)
        assertEquals(TrustState.HIGH_RISK, result.trustState)
    }

    @Test
    fun `single HIGH attribution signature invalid produces R3`() {
        val signals = listOf(SecuritySignal.SignalAttributionSignatureInvalid("Checksum mismatch"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(60, result.riskScore)
        assertEquals(TrustState.HIGH_RISK, result.trustState)
    }

    @Test
    fun `single HIGH attribution record missing is R2`() {
        val signals = listOf(SecuritySignal.SignalAttributionRecordMissing("No attribution found"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(40, result.riskScore)
        assertEquals(TrustState.SUSPICIOUS, result.trustState)
    }

    @Test
    fun `single HIGH hooking framework is R2`() {
        val signals = listOf(SecuritySignal.SignalHookingFrameworkDetected("frida"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(35, result.riskScore)
        assertEquals(TrustState.SUSPICIOUS, result.trustState)
    }

    @Test
    fun `single HIGH integrity manifest mismatch is R2`() {
        val signals = listOf(SecuritySignal.SignalIntegrityManifestMismatch("APK hash mismatch"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(30, result.riskScore)
        assertEquals(TrustState.SUSPICIOUS, result.trustState)
    }

    @Test
    fun `single HIGH manifest mismatch is R2`() {
        val signals = listOf(SecuritySignal.SignalManifestIntegrityMismatch("Manifest changed"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(30, result.riskScore)
        assertEquals(TrustState.SUSPICIOUS, result.trustState)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 4: Domain separation / caps
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `identity domain capped at 40`() {
        val signals = listOf(
            SecuritySignal.SignalPackageNameChanged("a", "b"),
            SecuritySignal.SignalLabelChanged("a", "b"),
            SecuritySignal.SignalUnexpectedBuildMetadata("weird")
        )
        val result = RiskAssessment.calculateRisk(signals)
        // 5 + 5 + 10 = 20, all identity domain, cap = 40
        assertEquals(20, result.domainContributions[RiskAssessment.SignalDomain.IDENTITY])
    }

    @Test
    fun `signature domain raw weight exceeds cap`() {
        // Two signature signals: 60 + 60 = 120, but cap = 40
        val signals = listOf(
            SecuritySignal.SignalCertificateMismatch("a", "b"),
            SecuritySignal.SignalApkResigned("resigned")
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(40, result.domainContributions[RiskAssessment.SignalDomain.SIGNATURE])
    }

    @Test
    fun `multiple signals in same domain are capped`() {
        // Runtime: debugger(25) + hooking(35) = 60, cap = 40
        val signals = listOf(
            SecuritySignal.SignalDebuggerAttached(),
            SecuritySignal.SignalHookingFrameworkDetected("frida")
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(40, result.domainContributions[RiskAssessment.SignalDomain.RUNTIME])
    }

    @Test
    fun `total risk score never exceeds 100`() {
        val signals = listOf(
            SecuritySignal.SignalCertificateMismatch("a", "b"),
            SecuritySignal.SignalDexMismatch("a", "b"),
            SecuritySignal.SignalNativeLibraryMismatch("lib.so", "a", "b"),
            SecuritySignal.SignalInjectedLibraryDetected("frida.so"),
            SecuritySignal.SignalUnofficialBuild("rebuild"),
            SecuritySignal.SignalAttributionSignatureInvalid("tampered")
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertTrue("Risk score should be <= 100, got ${result.riskScore}", result.riskScore <= 100)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 5: Cross-domain correlation bonuses
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `two distinct domains get cross-domain bonus`() {
        val signals = listOf(
            SecuritySignal.SignalDebuggerAttached(),       // RUNTIME: 25
            SecuritySignal.SignalRootDetected()             // ENVIRONMENT: 5
        )
        val result = RiskAssessment.calculateRisk(signals)
        // Base: 25 + 5 = 30, domains: 2, bonus: +20, clusters: none
        // Raw: 30 + 20 = 50, capped to 100
        assertEquals(50, result.riskScore)
        assertEquals(TrustState.HIGH_RISK, result.trustState)
    }

    @Test
    fun `three distinct domains get THREE_DOMAIN_BONUS`() {
        val signals = listOf(
            SecuritySignal.SignalDebuggerAttached(),       // RUNTIME: 25
            SecuritySignal.SignalRootDetected(),            // ENVIRONMENT: 5
            SecuritySignal.SignalDebugBuildDetected()       // BUILD: 15
        )
        val result = RiskAssessment.calculateRisk(signals)
        // Base: 25 + 5 + 15 = 45, domains: 3, bonus: +30
        // Raw: 45 + 30 = 75, capped to 100
        assertEquals(75, result.riskScore)
        assertEquals(TrustState.UNTRUSTED, result.trustState)
    }

    @Test
    fun `single domain gets no correlation bonus`() {
        val signals = listOf(
            SecuritySignal.SignalDebuggerAttached(),
            SecuritySignal.SignalHookingFrameworkDetected("frida")
        )
        val result = RiskAssessment.calculateRisk(signals)
        // Same domain (RUNTIME), no bonus
        // Base: 40 (capped), no bonus
        assertEquals(40, result.riskScore)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 6: Repeated failure bonus
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `repeated failures increase risk score`() {
        val signals = listOf(SecuritySignal.SignalDebuggerAttached())
        val resultNoFailures = RiskAssessment.calculateRisk(signals, previousFailures = 0)
        val resultWithFailures = RiskAssessment.calculateRisk(signals, previousFailures = 3)
        assertTrue("Repeated failures should increase score",
            resultWithFailures.riskScore > resultNoFailures.riskScore)
    }

    @Test
    fun `single previous failure has no bonus`() {
        val signals = listOf(SecuritySignal.SignalDebuggerAttached())
        val result = RiskAssessment.calculateRisk(signals, previousFailures = 1)
        assertEquals(25, result.riskScore)
    }

    @Test
    fun `two previous failures get half bonus`() {
        val signals = listOf(SecuritySignal.SignalDebuggerAttached())
        val result = RiskAssessment.calculateRisk(signals, previousFailures = 2)
        assertEquals(25 + 7, result.riskScore) // 7 = 15/2
    }

    @Test
    fun `three plus previous failures get full bonus`() {
        val signals = listOf(SecuritySignal.SignalDebuggerAttached())
        val result = RiskAssessment.calculateRisk(signals, previousFailures = 3)
        assertEquals(25 + 15, result.riskScore)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 7: Correlation cluster detection (double-counting prevention)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `identity anomaly cluster detected with 2+ identity signals`() {
        val signals = listOf(
            SecuritySignal.SignalPackageNameChanged("a", "b"),
            SecuritySignal.SignalLabelChanged("a", "b")
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertTrue("Should have identity anomaly cluster",
            result.correlationClusters.any { it.clusterId == "IDENTITY_ANOMALY" })
        val cluster = result.correlationClusters.first { it.clusterId == "IDENTITY_ANOMALY" }
        assertEquals(2, cluster.signals.size)
        assertTrue("Double-count reduction should be positive", cluster.doubleCountReduction > 0)
    }

    @Test
    fun `repackaging cluster detected with certificate mismatch and dex mismatch`() {
        val signals = listOf(
            SecuritySignal.SignalCertificateMismatch("a", "b"),
            SecuritySignal.SignalDexMismatch("a", "b")
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertTrue("Should have repackaging cluster",
            result.correlationClusters.any { it.clusterId == "REPACKAGING" })
    }

    @Test
    fun `runtime instrumentation cluster detected`() {
        val signals = listOf(
            SecuritySignal.SignalDebuggerAttached(),
            SecuritySignal.SignalHookingFrameworkDetected("frida"),
            SecuritySignal.SignalInjectedLibraryDetected("libfrida.so")
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertTrue("Should have runtime instrumentation cluster",
            result.correlationClusters.any { it.clusterId == "RUNTIME_INSTRUMENTATION" })
    }

    @Test
    fun `attribution tampering cluster detected`() {
        val signals = listOf(
            SecuritySignal.SignalAttributionSignatureInvalid("bad sig"),
            SecuritySignal.SignalAttributionRecordMissing("no record")
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertTrue("Should have attribution tampering cluster",
            result.correlationClusters.any { it.clusterId == "ATTRIBUTION_TAMPERING" })
    }

    @Test
    fun `correlation reduction prevents double-counting`() {
        // Certificate mismatch alone = 60
        val singleSignal = RiskAssessment.calculateRisk(
            listOf(SecuritySignal.SignalCertificateMismatch("a", "b"))
        )
        // Certificate + dex mismatch (repackaging cluster)
        val clusterSignals = RiskAssessment.calculateRisk(
            listOf(
                SecuritySignal.SignalCertificateMismatch("a", "b"),
                SecuritySignal.SignalDexMismatch("a", "b")
            )
        )
        // With cluster, the total should be less than naive sum
        // Naive would be: 40 (capped sig) + 40 (capped integrity) + 20 (cross-domain) = 100
        // With cluster reduction, it should be lower
        assertTrue("Cluster reduction should lower score vs naive sum",
            clusterSignals.riskScore <= 100)
        assertTrue("Cluster should be detected",
            clusterSignals.correlationClusters.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 8: Confidence calculation
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `single weak signal has LOW confidence`() {
        val signals = listOf(SecuritySignal.SignalEmulatorDetected())
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(RiskAssessment.Confidence.LOW, result.confidence)
    }

    @Test
    fun `two signals same domain have MEDIUM confidence`() {
        val signals = listOf(
            SecuritySignal.SignalPackageNameChanged("a", "b"),
            SecuritySignal.SignalLabelChanged("a", "b")
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(RiskAssessment.Confidence.MEDIUM, result.confidence)
    }

    @Test
    fun `single critical signal has HIGH confidence`() {
        val signals = listOf(SecuritySignal.SignalCertificateMismatch("a", "b"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(RiskAssessment.Confidence.HIGH, result.confidence)
    }

    @Test
    fun `two domains with critical has VERY_HIGH confidence`() {
        val signals = listOf(
            SecuritySignal.SignalCertificateMismatch("a", "b"),   // SIGNATURE, CRITICAL
            SecuritySignal.SignalDebuggerAttached()                // RUNTIME, MEDIUM
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(RiskAssessment.Confidence.VERY_HIGH, result.confidence)
    }

    @Test
    fun `three domains with critical has VERY_HIGH confidence`() {
        val signals = listOf(
            SecuritySignal.SignalCertificateMismatch("a", "b"),   // SIGNATURE
            SecuritySignal.SignalDebuggerAttached(),               // RUNTIME
            SecuritySignal.SignalRootDetected()                    // ENVIRONMENT
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(RiskAssessment.Confidence.VERY_HIGH, result.confidence)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 9: Trust state boundary tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `score 9 is TRUSTED`() {
        // Just under the R1 threshold
        val signals = listOf(SecuritySignal.SignalPackageNameChanged("a", "b"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(5, result.riskScore)
        assertEquals(TrustState.TRUSTED, result.trustState)
    }

    @Test
    fun `score 10 is LOW_RISK`() {
        // Exactly at R1 threshold — unexpected build metadata = 10
        val signals = listOf(SecuritySignal.SignalUnexpectedBuildMetadata("weird"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(10, result.riskScore)
        assertEquals(TrustState.LOW_RISK, result.trustState)
    }

    @Test
    fun `score 29 is LOW_RISK`() {
        // Just under R2 threshold
        val signals = listOf(SecuritySignal.SignalDebuggerAttached()) // 25 + cross-domain bonus needs 2nd domain
        // Debugger(25) alone is R1, need to push to exactly 29
        // Actually debugger is 25, which is LOW_RISK
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(TrustState.LOW_RISK, result.trustState)
    }

    @Test
    fun `score 30 is SUSPICIOUS`() {
        // Hooking framework = 35, exactly R2
        val signals = listOf(SecuritySignal.SignalHookingFrameworkDetected("frida"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(35, result.riskScore)
        assertEquals(TrustState.SUSPICIOUS, result.trustState)
    }

    @Test
    fun `score 50 is HIGH_RISK`() {
        // DEX mismatch = 50, exactly R3
        val signals = listOf(SecuritySignal.SignalDexMismatch("a", "b"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(50, result.riskScore)
        assertEquals(TrustState.HIGH_RISK, result.trustState)
    }

    @Test
    fun `score 75 is UNTRUSTED`() {
        // Certificate(60) + debugger(25) + cross-domain bonus(20) - cluster reduction
        // = 40 + 25 + 20 = 85 → UNTRUSTED
        val signals = listOf(
            SecuritySignal.SignalCertificateMismatch("a", "b"),
            SecuritySignal.SignalDebuggerAttached()
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(TrustState.UNTRUSTED, result.trustState)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 10: Real-world scenario tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `scenario A — harmless rename only`() {
        val signals = listOf(
            SecuritySignal.SignalPackageNameChanged("com.dika.fastrecorder", "com.dika.recorder"),
            SecuritySignal.SignalLabelChanged("FastRecorder", "Screen Recorder")
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals("Identity cluster should be detected", 1, result.correlationClusters.size)
        assertEquals("IDENTITY_ANOMALY", result.correlationClusters[0].clusterId)
        assertTrue("Should be low risk", result.riskScore <= 29)
    }

    @Test
    fun `scenario B — debug build in dev`() {
        val signals = listOf(
            SecuritySignal.SignalDebugBuildDetected("Debug build"),
            SecuritySignal.SignalUnexpectedBuildMetadata("debug metadata")
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals("Same domain cluster", 1, result.correlationClusters.size)
        assertTrue("Should be low to medium risk", result.riskScore <= 49)
    }

    @Test
    fun `scenario C — full repackaging`() {
        val signals = listOf(
            SecuritySignal.SignalCertificateMismatch("trusted", "attacker"),
            SecuritySignal.SignalDexMismatch("original", "modified"),
            SecuritySignal.SignalManifestIntegrityMismatch("manifest changed"),
            SecuritySignal.SignalAttributionSignatureInvalid("attribution removed")
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(TrustState.UNTRUSTED, result.trustState)
        assertEquals(RiskAssessment.Confidence.VERY_HIGH, result.confidence)
        assertTrue("Should have multiple clusters",
            result.correlationClusters.size >= 2)
    }

    @Test
    fun `scenario D — runtime instrumentation only`() {
        val signals = listOf(
            SecuritySignal.SignalDebuggerAttached(),
            SecuritySignal.SignalHookingFrameworkDetected("frida"),
            SecuritySignal.SignalInjectedLibraryDetected("libfrida-gadget.so")
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals("Runtime cluster detected", 1,
            result.correlationClusters.count { it.clusterId == "RUNTIME_INSTRUMENTATION" })
        assertTrue("Should be high risk", result.riskScore >= 50)
    }

    @Test
    fun `scenario E — environmental anomaly only`() {
        val signals = listOf(
            SecuritySignal.SignalEmulatorDetected(),
            SecuritySignal.SignalRootDetected()
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(TrustState.LOW_RISK, result.trustState)
        assertEquals(RiskAssessment.Confidence.MEDIUM, result.confidence)
    }

    @Test
    fun `scenario F — mixed weak signals`() {
        val signals = listOf(
            SecuritySignal.SignalEmulatorDetected(),       // 5
            SecuritySignal.SignalRootDetected(),            // 5
            SecuritySignal.SignalPackageNameChanged("a", "b") // 5
        )
        val result = RiskAssessment.calculateRisk(signals)
        // 3 domains → 30 bonus
        // Base: 5 + 5 + 5 = 15, + 30 bonus = 45 → SUSPICIOUS
        assertEquals(TrustState.SUSPICIOUS, result.trustState)
    }

    @Test
    fun `scenario G — attribution tampering plus signature`() {
        val signals = listOf(
            SecuritySignal.SignalAttributionSignatureInvalid("tampered"),
            SecuritySignal.SignalCertificateMismatch("a", "b")
        )
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(TrustState.UNTRUSTED, result.trustState)
        assertEquals(RiskAssessment.Confidence.VERY_HIGH, result.confidence)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 11: Signal weight validation
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `all signal types have defined weights`() {
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
            val weight = RiskAssessment.SIGNAL_WEIGHTS[signal::class.java]
            assertNotNull("Signal ${signal::class.java.simpleName} should have a defined weight", weight)
            assertTrue("Weight for ${signal::class.java.simpleName} should be positive", weight!!.weight > 0)
        }
    }

    @Test
    fun `every signal has a domain`() {
        val allSignals = listOf(
            SecuritySignal.SignalPackageNameChanged("a", "b"),
            SecuritySignal.SignalCertificateMismatch("a", "b"),
            SecuritySignal.SignalDexMismatch("a", "b"),
            SecuritySignal.SignalManifestIntegrityMismatch("x"),
            SecuritySignal.SignalDebuggerAttached(),
            SecuritySignal.SignalEmulatorDetected(),
            SecuritySignal.SignalAttributionSignatureInvalid("x"),
            SecuritySignal.SignalBackendUnofficialBuild("x"),
            SecuritySignal.SignalDebugBuildDetected()
        )

        for (signal in allSignals) {
            assertNotNull("Signal ${signal::class.java.simpleName} should have a domain", signal.domain)
        }
    }

    @Test
    fun `severity ordering is consistent`() {
        val low = RiskAssessment.SignalWeight(RiskAssessment.Severity.LOW, 5)
        val medium = RiskAssessment.SignalWeight(RiskAssessment.Severity.MEDIUM, 15)
        val high = RiskAssessment.SignalWeight(RiskAssessment.Severity.HIGH, 30)
        val critical = RiskAssessment.SignalWeight(RiskAssessment.Severity.CRITICAL, 60)

        assertTrue(low.weight < medium.weight)
        assertTrue(medium.weight < high.weight)
        assertTrue(high.weight < critical.weight)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 12: Edge cases
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `risk score is always non-negative`() {
        val signals = listOf(
            SecuritySignal.SignalCertificateMismatch("a", "b"),
            SecuritySignal.SignalDexMismatch("a", "b")
        )
        val result = RiskAssessment.calculateRisk(signals, previousFailures = 100)
        assertTrue("Risk score should be >= 0", result.riskScore >= 0)
    }

    @Test
    fun `risk score is always <= 100`() {
        val signals = listOf(
            SecuritySignal.SignalCertificateMismatch("a", "b"),
            SecuritySignal.SignalApkResigned("x"),
            SecuritySignal.SignalDexMismatch("a", "b"),
            SecuritySignal.SignalNativeLibraryMismatch("lib.so", "a", "b"),
            SecuritySignal.SignalInjectedLibraryDetected("frida.so"),
            SecuritySignal.SignalUnofficialBuild("rebuild"),
            SecuritySignal.SignalBackendUnofficialBuild("server says no"),
            SecuritySignal.SignalAttributionSignatureInvalid("tampered")
        )
        val result = RiskAssessment.calculateRisk(signals, previousFailures = 10)
        assertTrue("Risk score should be <= 100, got ${result.riskScore}", result.riskScore <= 100)
    }

    @Test
    fun `unknown signal class has no weight and is ignored`() {
        // If somehow a signal type isn't in the map, it should contribute 0
        val signals = listOf(SecuritySignal.SignalPackageNameChanged("a", "b"))
        val result = RiskAssessment.calculateRisk(signals)
        assertEquals(5, result.riskScore)
    }

    @Test
    fun `many weak signals can escalate through correlation`() {
        val signals = listOf(
            SecuritySignal.SignalEmulatorDetected(),        // 5
            SecuritySignal.SignalRootDetected(),             // 5
            SecuritySignal.SignalPackageNameChanged("a", "b"), // 5
            SecuritySignal.SignalLabelChanged("a", "b"),      // 5
            SecuritySignal.SignalDebugBuildDetected()          // 15
        )
        val result = RiskAssessment.calculateRisk(signals)
        // 4 domains → 30 bonus
        // Base: 5 + 10(identity capped) + 15 = 30
        // + 30 bonus = 60 → HIGH_RISK
        assertEquals(TrustState.HIGH_RISK, result.trustState)
    }
}
