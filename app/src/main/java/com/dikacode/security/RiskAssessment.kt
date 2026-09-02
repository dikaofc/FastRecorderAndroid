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
    // Each signal has a severity and weight.
    // Weights are designed so that a single weak signal cannot push to R3/R4.

    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

    data class SignalWeight(val severity: Severity, val weight: Int)

    val SIGNAL_WEIGHTS = mapOf(
        // Identity domain
        SignalPackageNameChanged::class.java to SignalWeight(Severity.LOW, 5),
        SignalLabelChanged::class.java to SignalWeight(Severity.LOW, 5),
        SignalUnexpectedBuildMetadata::class.java to SignalWeight(Severity.LOW, 10),

        // Signature domain
        SignalCertificateMismatch::class.java to SignalWeight(Severity.CRITICAL, 60),
        SignalApkResigned::class.java to SignalWeight(Severity.CRITICAL, 60),

        // Binary integrity domain
        SignalDexMismatch::class.java to SignalWeight(Severity.CRITICAL, 50),
        SignalNativeLibraryMismatch::class.java to SignalWeight(Severity.CRITICAL, 50),
        SignalIntegrityManifestMismatch::class.java to SignalWeight(Severity.HIGH, 30),

        // Manifest domain
        SignalManifestIntegrityMismatch::class.java to SignalWeight(Severity.HIGH, 30),

        // Attribution domain
        SignalAttributionSignatureInvalid::class.java to SignalWeight(Severity.CRITICAL, 60),
        SignalAttributionRecordMissing::class.java to SignalWeight(Severity.HIGH, 40),

        // Runtime domain
        SignalDebuggerAttached::class.java to SignalWeight(Severity.MEDIUM, 25),
        SignalHookingFrameworkDetected::class.java to SignalWeight(Severity.HIGH, 35),
        SignalInjectedLibraryDetected::class.java to SignalWeight(Severity.CRITICAL, 50),

        // Build domain
        SignalDebugBuildDetected::class.java to SignalWeight(Severity.MEDIUM, 15),
        SignalUnofficialBuild::class.java to SignalWeight(Severity.CRITICAL, 60),

        // Environment domain
        SignalEmulatorDetected::class.java to SignalWeight(Severity.LOW, 5),
        SignalRootDetected::class.java to SignalWeight(Severity.LOW, 5),

        // Backend domain
        SignalBackendUnofficialBuild::class.java to SignalWeight(Severity.CRITICAL, 60)
    )

    // ─── Domain separation ────────────────────────────────────────────

    enum class SignalDomain {
        IDENTITY,
        SIGNATURE,
        BINARY_INTEGRITY,
        MANIFEST,
        RUNTIME,
        ENVIRONMENT,
        ATTRIBUTION,
        BACKEND,
        BUILD
    }

    // Maximum contribution per domain to prevent one category from dominating
    private const val MAX_DOMAIN_CONTRIBUTION = 40

    // ─── Correlation bonuses ──────────────────────────────────────────

    private const val CROSS_DOMAIN_BONUS = 20   // When evidence from 2+ domains
    private const val THREE_DOMAIN_BONUS = 30   // When evidence from 3+ domains
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

    /**
     * Calculate risk score from collected signals.
     * Applies domain separation, correlation rules, and double-counting prevention.
     */
    fun calculateRisk(
        signals: List<SecuritySignal>,
        previousFailures: Int = 0
    ): RiskResult {
        if (signals.isEmpty()) {
            return RiskResult(
                riskScore = 0,
                trustState = TrustState.TRUSTED,
                confidence = Confidence.HIGH,
                domainContributions = emptyMap(),
                activeSignals = emptyList(),
                correlationClusters = emptyList()
            )
        }

        // Step 1: Group signals by domain
        val domainGroups = signals.groupBy { it.domain }

        // Step 2: Apply per-domain weight caps
        val domainContributions = mutableMapOf<SignalDomain, Int>()
        for ((domain, domainSignals) in domainGroups) {
            val rawWeight = domainSignals.sumOf { signal ->
                SIGNAL_WEIGHTS[signal::class.java]?.weight ?: 0
            }
            // Cap per-domain contribution
            domainContributions[domain] = rawWeight.coerceAtMost(MAX_DOMAIN_CONTRIBUTION)
        }

        // Step 3: Sum domain contributions (capped)
        val baseScore = domainContributions.values.sum().coerceAtMost(100)

        // Step 4: Correlation — count distinct domains with evidence
        val domainsWithEvidence = domainGroups.keys.size
        val correlationBonus = when {
            domainsWithEvidence >= 3 -> THREE_DOMAIN_BONUS
            domainsWithEvidence >= 2 -> CROSS_DOMAIN_BONUS
            else -> 0
        }

        // Step 5: Repeated failure bonus (bounded)
        val repeatedBonus = when {
            previousFailures >= 3 -> REPEATED_FAILURE_BONUS
            previousFailures >= 2 -> REPEATED_FAILURE_BONUS / 2
            else -> 0
        }

        // Step 6: Correlation clusters (prevent double-counting)
        val clusters = detectCorrelationClusters(signals)
        val clusterReduction = clusters.sumOf { it.doubleCountReduction }

        // Step 7: Final score
        val rawScore = baseScore + correlationBonus + repeatedBonus - clusterReduction
        val finalScore = rawScore.coerceIn(0, 100)

        // Step 8: Determine trust state
        val trustState = when {
            finalScore >= 75 -> TrustState.UNTRUSTED
            finalScore >= 50 -> TrustState.HIGH_RISK
            finalScore >= 30 -> TrustState.SUSPICIOUS
            finalScore >= 10 -> TrustState.LOW_RISK
            else -> TrustState.TRUSTED
        }

        // Step 9: Determine confidence
        val confidence = calculateConfidence(
            domainsWithEvidence = domainsWithEvidence,
            signals = signals,
            hasCriticalSignal = signals.any {
                SIGNAL_WEIGHTS[it::class.java]?.severity == Severity.CRITICAL
            }
        )

        return RiskResult(
            riskScore = finalScore,
            trustState = trustState,
            confidence = confidence,
            domainContributions = domainContributions,
            activeSignals = signals,
            correlationClusters = clusters
        )
    }

    // ─── Confidence calculation ───────────────────────────────────────

    private fun calculateConfidence(
        domainsWithEvidence: Int,
        signals: List<SecuritySignal>,
        hasCriticalSignal: Boolean
    ): Confidence {
        return when {
            // 3+ domains with critical evidence
            domainsWithEvidence >= 3 && hasCriticalSignal -> Confidence.VERY_HIGH
            // 2+ domains with evidence or critical signal
            domainsWithEvidence >= 2 -> Confidence.HIGH
            // Single domain with critical evidence
            hasCriticalSignal -> Confidence.HIGH
            // Multiple signals in same domain
            signals.size >= 2 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
    }

    // ─── Correlation cluster detection ────────────────────────────────

    data class CorrelationCluster(
        val clusterId: String,
        val signals: List<SecuritySignal>,
        val rootCause: String,
        val doubleCountReduction: Int
    )

    /**
     * Detect signals that share the same root cause to prevent double-counting.
     * Example: package mismatch + label mismatch + filename mismatch
     * are all consequences of a single identity change.
     */
    private fun detectCorrelationClusters(signals: List<SecuritySignal>): List<CorrelationCluster> {
        val clusters = mutableListOf<CorrelationCluster>()

        // Cluster 1: Identity anomaly cluster
        val identitySignals = signals.filter {
            it is SignalPackageNameChanged || it is SignalLabelChanged || it is SignalPackageNameChanged
        }
        if (identitySignals.size >= 2) {
            val totalWeight = identitySignals.sumOf {
                SIGNAL_WEIGHTS[it::class.java]?.weight ?: 0
            }
            clusters.add(
                CorrelationCluster(
                    clusterId = "IDENTITY_ANOMALY",
                    signals = identitySignals,
                    rootCause = "Identity change (package/label/filename)",
                    doubleCountReduction = totalWeight - (SIGNAL_WEIGHTS[SignalPackageNameChanged::class.java]?.weight ?: 5)
                )
            )
        }

        // Cluster 2: Repackaging cluster
        val repackagingSignals = signals.filter {
            it is SignalCertificateMismatch || it is SignalApkResigned || it is SignalDexMismatch
        }
        if (repackagingSignals.size >= 2) {
            val totalWeight = repackagingSignals.sumOf {
                SIGNAL_WEIGHTS[it::class.java]?.weight ?: 0
            }
            clusters.add(
                CorrelationCluster(
                    clusterId = "REPACKAGING",
                    signals = repackagingSignals,
                    rootCause = "APK repackaging event",
                    doubleCountReduction = totalWeight - (SIGNAL_WEIGHTS[SignalCertificateMismatch::class.java]?.weight ?: 60)
                )
            )
        }

        // Cluster 3: Attribution tampering cluster
        val attributionSignals = signals.filter {
            it is SignalAttributionSignatureInvalid || it is SignalAttributionRecordMissing
        }
        if (attributionSignals.size >= 2) {
            val totalWeight = attributionSignals.sumOf {
                SIGNAL_WEIGHTS[it::class.java]?.weight ?: 0
            }
            clusters.add(
                CorrelationCluster(
                    clusterId = "ATTRIBUTION_TAMPERING",
                    signals = attributionSignals,
                    rootCause = "Attribution modification",
                    doubleCountReduction = totalWeight - (SIGNAL_WEIGHTS[SignalAttributionSignatureInvalid::class.java]?.weight ?: 60)
                )
            )
        }

        // Cluster 4: Runtime instrumentation cluster
        val runtimeSignals = signals.filter {
            it is SignalDebuggerAttached || it is SignalHookingFrameworkDetected || it is SignalInjectedLibraryDetected
        }
        if (runtimeSignals.size >= 2) {
            val totalWeight = runtimeSignals.sumOf {
                SIGNAL_WEIGHTS[it::class.java]?.weight ?: 0
            }
            clusters.add(
                CorrelationCluster(
                    clusterId = "RUNTIME_INSTRUMENTATION",
                    signals = runtimeSignals,
                    rootCause = "Runtime instrumentation/hooking",
                    doubleCountReduction = totalWeight - (SIGNAL_WEIGHTS[SignalDebuggerAttached::class.java]?.weight ?: 25)
                )
            )
        }

        return clusters
    }
}
