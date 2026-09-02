// @dikaacode
package com.dikacode.security

/**
 * Centralized risk assessment engine.
 * Collects security signals, applies correlation rules, and produces a risk score.
 *
 * Risk levels:
 *   R0 (0–9):   TRUSTED     — no threat indicators
 *   R1 (10–29): LOW_RISK    — minor anomalies
 *   R2 (30–49): SUSPICIOUS  — several manipulation indicators
 *   R3 (50–74): HIGH_RISK   — strong evidence of modification
 *   R4 (75–100): UNTRUSTED  — build very likely untrusted
 */
object RiskAssessment {

    // ─── Signal weights ───────────────────────────────────────────────
    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

    data class SignalWeight(val severity: Severity, val weight: Int)

    val SIGNAL_WEIGHTS: Map<Class<out SecuritySignal>, SignalWeight> = mapOf(
        // Identity domain
        SecuritySignal.SignalPackageNameChanged::class.java to SignalWeight(Severity.LOW, 5),
        SecuritySignal.SignalLabelChanged::class.java to SignalWeight(Severity.LOW, 5),
        SecuritySignal.SignalUnexpectedBuildMetadata::class.java to SignalWeight(Severity.LOW, 10),

        // Signature domain
        SecuritySignal.SignalCertificateMismatch::class.java to SignalWeight(Severity.CRITICAL, 60),
        SecuritySignal.SignalApkResigned::class.java to SignalWeight(Severity.CRITICAL, 60),

        // Binary integrity domain
        SecuritySignal.SignalDexMismatch::class.java to SignalWeight(Severity.CRITICAL, 50),
        SecuritySignal.SignalNativeLibraryMismatch::class.java to SignalWeight(Severity.CRITICAL, 50),
        SecuritySignal.SignalIntegrityManifestMismatch::class.java to SignalWeight(Severity.HIGH, 30),

        // Manifest domain
        SecuritySignal.SignalManifestIntegrityMismatch::class.java to SignalWeight(Severity.HIGH, 30),

        // Attribution domain
        SecuritySignal.SignalAttributionSignatureInvalid::class.java to SignalWeight(Severity.CRITICAL, 60),
        SecuritySignal.SignalAttributionRecordMissing::class.java to SignalWeight(Severity.HIGH, 40),

        // Runtime domain
        SecuritySignal.SignalDebuggerAttached::class.java to SignalWeight(Severity.MEDIUM, 25),
        SecuritySignal.SignalHookingFrameworkDetected::class.java to SignalWeight(Severity.HIGH, 35),
        SecuritySignal.SignalInjectedLibraryDetected::class.java to SignalWeight(Severity.CRITICAL, 50),

        // Build domain
        SecuritySignal.SignalDebugBuildDetected::class.java to SignalWeight(Severity.MEDIUM, 15),
        SecuritySignal.SignalUnofficialBuild::class.java to SignalWeight(Severity.CRITICAL, 60),

        // Environment domain
        SecuritySignal.SignalEmulatorDetected::class.java to SignalWeight(Severity.LOW, 5),
        SecuritySignal.SignalRootDetected::class.java to SignalWeight(Severity.LOW, 5),

        // Backend domain
        SecuritySignal.SignalBackendUnofficialBuild::class.java to SignalWeight(Severity.CRITICAL, 60)
    )

    // ─── Domain separation ────────────────────────────────────────────

    enum class SignalDomain {
        IDENTITY, SIGNATURE, BINARY_INTEGRITY, MANIFEST,
        RUNTIME, ENVIRONMENT, ATTRIBUTION, BACKEND, BUILD
    }

    private const val MAX_DOMAIN_CONTRIBUTION = 40
    private const val CROSS_DOMAIN_BONUS = 20
    private const val THREE_DOMAIN_BONUS = 30
    private const val REPEATED_FAILURE_BONUS = 15

    // ─── Risk calculation ─────────────────────────────────────────────

    data class RiskResult(
        val riskScore: Int,
        val trustState: TrustState,
        val confidence: Confidence,
        val domainContributions: Map<SignalDomain, Int>,
        val activeSignals: List<SecuritySignal>,
        val correlationClusters: List<CorrelationCluster>
    )

    enum class Confidence { LOW, MEDIUM, HIGH, VERY_HIGH }

    fun calculateRisk(
        signals: List<SecuritySignal>,
        previousFailures: Int = 0
    ): RiskResult {
        if (signals.isEmpty()) {
            return RiskResult(0, TrustState.TRUSTED, Confidence.HIGH, emptyMap(), emptyList(), emptyList())
        }

        val domainGroups = signals.groupBy { it.domain }

        val domainContributions = mutableMapOf<SignalDomain, Int>()
        for ((domain, domainSignals) in domainGroups) {
            val rawWeight = domainSignals.sumOf { signal ->
                SIGNAL_WEIGHTS[signal::class.java]?.weight ?: 0
            }
            domainContributions[domain] = rawWeight.coerceAtMost(MAX_DOMAIN_CONTRIBUTION)
        }

        val baseScore = domainContributions.values.sum().coerceAtMost(100)
        val domainsWithEvidence = domainGroups.keys.size
        val correlationBonus = when {
            domainsWithEvidence >= 3 -> THREE_DOMAIN_BONUS
            domainsWithEvidence >= 2 -> CROSS_DOMAIN_BONUS
            else -> 0
        }
        val repeatedBonus = when {
            previousFailures >= 3 -> REPEATED_FAILURE_BONUS
            previousFailures >= 2 -> REPEATED_FAILURE_BONUS / 2
            else -> 0
        }

        val clusters = detectCorrelationClusters(signals)
        val clusterReduction = clusters.sumOf { it.doubleCountReduction }

        val rawScore = baseScore + correlationBonus + repeatedBonus - clusterReduction
        val finalScore = rawScore.coerceIn(0, 100)

        val trustState = when {
            finalScore >= 75 -> TrustState.UNTRUSTED
            finalScore >= 50 -> TrustState.HIGH_RISK
            finalScore >= 30 -> TrustState.SUSPICIOUS
            finalScore >= 10 -> TrustState.LOW_RISK
            else -> TrustState.TRUSTED
        }

        val confidence = calculateConfidence(domainsWithEvidence, signals,
            signals.any { SIGNAL_WEIGHTS[it::class.java]?.severity == Severity.CRITICAL })

        return RiskResult(finalScore, trustState, confidence, domainContributions, signals, clusters)
    }

    private fun calculateConfidence(
        domainsWithEvidence: Int,
        signals: List<SecuritySignal>,
        hasCriticalSignal: Boolean
    ): Confidence = when {
        domainsWithEvidence >= 3 && hasCriticalSignal -> Confidence.VERY_HIGH
        domainsWithEvidence >= 2 -> Confidence.HIGH
        hasCriticalSignal -> Confidence.HIGH
        signals.size >= 2 -> Confidence.MEDIUM
        else -> Confidence.LOW
    }

    // ─── Correlation cluster detection ────────────────────────────────

    data class CorrelationCluster(
        val clusterId: String,
        val signals: List<SecuritySignal>,
        val rootCause: String,
        val doubleCountReduction: Int
    )

    private fun detectCorrelationClusters(signals: List<SecuritySignal>): List<CorrelationCluster> {
        val clusters = mutableListOf<CorrelationCluster>()

        // Cluster 1: Identity anomaly
        val identitySignals = signals.filter {
            it is SecuritySignal.SignalPackageNameChanged ||
            it is SecuritySignal.SignalLabelChanged ||
            it is SecuritySignal.SignalUnexpectedBuildMetadata
        }
        if (identitySignals.size >= 2) {
            val totalWeight = identitySignals.sumOf { SIGNAL_WEIGHTS[it::class.java]?.weight ?: 0 }
            clusters.add(CorrelationCluster(
                "IDENTITY_ANOMALY", identitySignals,
                "Identity change (package/label/filename)",
                totalWeight - (SIGNAL_WEIGHTS[SecuritySignal.SignalPackageNameChanged::class.java]?.weight ?: 5)
            ))
        }

        // Cluster 2: Repackaging
        val repackagingSignals = signals.filter {
            it is SecuritySignal.SignalCertificateMismatch ||
            it is SecuritySignal.SignalApkResigned ||
            it is SecuritySignal.SignalDexMismatch
        }
        if (repackagingSignals.size >= 2) {
            val totalWeight = repackagingSignals.sumOf { SIGNAL_WEIGHTS[it::class.java]?.weight ?: 0 }
            clusters.add(CorrelationCluster(
                "REPACKAGING", repackagingSignals,
                "APK repackaging event",
                totalWeight - (SIGNAL_WEIGHTS[SecuritySignal.SignalCertificateMismatch::class.java]?.weight ?: 60)
            ))
        }

        // Cluster 3: Attribution tampering
        val attributionSignals = signals.filter {
            it is SecuritySignal.SignalAttributionSignatureInvalid ||
            it is SecuritySignal.SignalAttributionRecordMissing
        }
        if (attributionSignals.size >= 2) {
            val totalWeight = attributionSignals.sumOf { SIGNAL_WEIGHTS[it::class.java]?.weight ?: 0 }
            clusters.add(CorrelationCluster(
                "ATTRIBUTION_TAMPERING", attributionSignals,
                "Attribution modification",
                totalWeight - (SIGNAL_WEIGHTS[SecuritySignal.SignalAttributionSignatureInvalid::class.java]?.weight ?: 60)
            ))
        }

        // Cluster 4: Runtime instrumentation
        val runtimeSignals = signals.filter {
            it is SecuritySignal.SignalDebuggerAttached ||
            it is SecuritySignal.SignalHookingFrameworkDetected ||
            it is SecuritySignal.SignalInjectedLibraryDetected
        }
        if (runtimeSignals.size >= 2) {
            val totalWeight = runtimeSignals.sumOf { SIGNAL_WEIGHTS[it::class.java]?.weight ?: 0 }
            clusters.add(CorrelationCluster(
                "RUNTIME_INSTRUMENTATION", runtimeSignals,
                "Runtime instrumentation/hooking",
                totalWeight - (SIGNAL_WEIGHTS[SecuritySignal.SignalDebuggerAttached::class.java]?.weight ?: 25)
            ))
        }

        return clusters
    }
}
