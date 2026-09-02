// @dikaacode
package com.dikacode.security

import android.content.Context
import android.util.Log
import com.dikacode.BuildConfig
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Main security engine orchestrator.
 * Coordinates identity, integrity, runtime, and attribution verification.
 * Implements state machine: TRUSTED → LOW_RISK → SUSPICIOUS → HIGH_RISK → UNTRUSTED
 */
object SecurityEngine {
    private const val TAG = "SecurityEngine"

    // ─── Security state ───────────────────────────────────────────
    @Volatile
    private var currentState: TrustState = TrustState.TRUSTED

    @Volatile
    private var currentRiskScore: Int = 0

    private val verificationCount = AtomicInteger(0)
    private val failureCount = AtomicInteger(0)
    private val lastVerificationTime = AtomicLong(0)

    // ─── Configuration ────────────────────────────────────────────
    private const val VERIFICATION_INTERVAL_MS = 30_000L // 30 seconds
    private const val MAX_FAILURES_BEFORE_ESCALATION = 3

    // ─── Initialization ───────────────────────────────────────────

    /**
     * Initialize the security engine.
     * Should be called once at app startup.
     */
    fun initialize(context: Context) {
        Log.i(TAG, "SecurityEngine initializing...")

        // Initialize identity verifier with trusted certificate
        // (certificate hash will be auto-recorded on first build)
        IdentityVerifier.initialize(
            trustedHashes = emptySet(), // Will be populated after first official build
            rotationHashes = emptySet()
        )

        // Record baseline integrity
        IntegrityChecker.recordBaseline(context)

        Log.i(TAG, "SecurityEngine initialized — state: $currentState")
    }

    // ─── Main verification entry point ────────────────────────────

    data class SecurityReport(
        val trustState: TrustState,
        val riskScore: Int,
        val confidence: RiskAssessment.Confidence,
        val attributionState: AttributionState,
        val signals: List<SecuritySignal>,
        val correlationClusters: List<RiskAssessment.CorrelationCluster>,
        val domainContributions: Map<RiskAssessment.SignalDomain, Int>,
        val eventLog: List<SecurityEvent>,
        val diagnostics: String,
        val policyResponse: SecurityPolicy.PolicyResponse = SecurityPolicy.getResponseForState(trustState, riskScore)
    )

    /**
     * Perform full security verification.
     * This is the main entry point for all security checks.
     */
    fun verify(context: Context): SecurityReport {
        val startTime = System.currentTimeMillis()
        verificationCount.incrementAndGet()

        Log.i(TAG, "=== Security Verification #${verificationCount.get()} ===")

        // Collect all signals from independent sources
        val allSignals = mutableListOf<SecuritySignal>()
        val eventLog = mutableListOf<SecurityEvent>()

        // ── Layer 1: Identity verification ──
        val identitySignals = IdentityVerifier.verifyIdentity(context)
        allSignals.addAll(identitySignals)
        Log.i(TAG, "Identity signals: ${identitySignals.size}")

        // ── Layer 2: Integrity verification ──
        val integritySignals = IntegrityChecker.performIntegrityCheck(context)
        allSignals.addAll(integritySignals)
        Log.i(TAG, "Integrity signals: ${integritySignals.size}")

        // ── Layer 3: Runtime environment verification ──
        val runtimeSignals = RuntimeDetector.performRuntimeCheck(context)
        allSignals.addAll(runtimeSignals)
        Log.i(TAG, "Runtime signals: ${runtimeSignals.size}")

        // ── Layer 4: Attribution verification ──
        val attributionResult = AttributionGuard.verifyAttribution(context)
        allSignals.addAll(attributionResult.signals)
        Log.i(TAG, "Attribution state: ${attributionResult.attributionState}")

        // ── Layer 5: Build identity verification ──
        val buildSignals = verifyBuildIdentity(context)
        allSignals.addAll(buildSignals)
        Log.i(TAG, "Build signals: ${buildSignals.size}")

        // ── Calculate risk score ──
        val failures = failureCount.get()
        val riskResult = RiskAssessment.calculateRisk(allSignals, failures)

        // ── Update state ──
        val previousState = currentState
        currentState = riskResult.trustState
        currentRiskScore = riskResult.riskScore

        // Track failures
        if (riskResult.trustState.ordinal >= TrustState.SUSPICIOUS.ordinal) {
            failureCount.incrementAndGet()
        } else {
            failureCount.set(0) // Reset on success
        }

        // ── Generate events ──
        for (signal in allSignals) {
            eventLog.add(
                SecurityEvent(
                    buildId = BuildConfig.BUILD_ID ?: "dev",
                    verificationType = VerificationType.STARTUP,
                    signalDomain = signal.domain,
                    riskDelta = RiskAssessment.SIGNAL_WEIGHTS[signal::class.java]?.weight ?: 0,
                    resultingRiskScore = riskResult.riskScore,
                    resultingState = riskResult.trustState,
                    detail = signal.detail
                )
            )
        }

        // ── State transition logging ──
        if (previousState != currentState) {
            Log.w(TAG, "Trust state changed: $previousState → $currentState (risk: ${riskResult.riskScore})")
        }

        // ── Generate diagnostics ──
        val diagnostics = generateDiagnostics(riskResult, attributionResult)

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "Verification completed in ${elapsed}ms — state: $currentState, risk: $currentRiskScore")

        lastVerificationTime.set(System.currentTimeMillis())

        val policyResponse = SecurityPolicy.getResponseForRiskResult(riskResult)

        return SecurityReport(
            trustState = currentState,
            riskScore = riskResult.riskScore,
            confidence = riskResult.confidence,
            attributionState = attributionResult.attributionState,
            signals = allSignals,
            correlationClusters = riskResult.correlationClusters,
            domainContributions = riskResult.domainContributions,
            eventLog = eventLog,
            diagnostics = diagnostics,
            policyResponse = policyResponse
        )
    }

    // ─── Periodic verification ────────────────────────────────────

    /**
     * Check if periodic re-verification is needed.
     */
    fun shouldReverify(): Boolean {
        val elapsed = System.currentTimeMillis() - lastVerificationTime.get()
        return elapsed >= VERIFICATION_INTERVAL_MS
    }

    /**
     * Perform periodic verification if needed.
     */
    fun periodicCheck(context: Context): SecurityReport? {
        return if (shouldReverify()) {
            verify(context)
        } else {
            null
        }
    }

    // ─── Build identity verification ──────────────────────────────

    private fun verifyBuildIdentity(context: Context): List<SecuritySignal> {
        val signals = mutableListOf<SecuritySignal>()

        // Check if build is debug in release context
        if (BuildConfig.BUILD_TYPE == "release") {
            val isDebuggable = (context.applicationInfo.flags and
                    android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                signals.add(SecuritySignal.SignalDebugBuildDetected("Release build is debuggable"))
            }
        } else {
            signals.add(SecuritySignal.SignalDebugBuildDetected("Debug build detected"))
        }

        return signals
    }

    // ─── Diagnostics ──────────────────────────────────────────────

    private fun generateDiagnostics(
        riskResult: RiskAssessment.RiskResult,
        attributionResult: AttributionGuard.AttributionVerificationResult
    ): String {
        return buildString {
            appendLine("SECURITY STATUS")
            appendLine("─────────────────────────────")
            appendLine("risk score: ${riskResult.riskScore}")
            appendLine("state: ${riskResult.trustState}")
            appendLine("confidence: ${riskResult.confidence}")
            appendLine("signature: ${if (riskResult.domainContributions.containsKey(RiskAssessment.SignalDomain.SIGNATURE)) "FAILED" else "VERIFIED"}")
            appendLine("integrity: ${if (riskResult.domainContributions.containsKey(RiskAssessment.SignalDomain.BINARY_INTEGRITY)) "FAILED" else "VERIFIED"}")
            appendLine("build: ${if (riskResult.domainContributions.containsKey(RiskAssessment.SignalDomain.BUILD)) "UNOFFICIAL" else "OFFICIAL"}")
            appendLine("attribution: ${attributionResult.attributionState}")
            appendLine("runtime: ${if (riskResult.domainContributions.containsKey(RiskAssessment.SignalDomain.RUNTIME)) "SUSPICIOUS" else "NORMAL"}")
            appendLine("verification count: ${verificationCount.get()}")
            appendLine("failure count: ${failureCount.get()}")
            if (riskResult.correlationClusters.isNotEmpty()) {
                appendLine("correlation clusters:")
                for (cluster in riskResult.correlationClusters) {
                    appendLine("  - ${cluster.clusterId}: ${cluster.rootCause}")
                }
            }
        }
    }

    // ─── Accessors ────────────────────────────────────────────────

    fun getCurrentState(): TrustState = currentState
    fun getCurrentRiskScore(): Int = currentRiskScore
    fun getVerificationCount(): Int = verificationCount.get()
    fun isTrusted(): Boolean = currentState == TrustState.TRUSTED
    fun isOperational(): Boolean = currentState.ordinal <= TrustState.HIGH_RISK.ordinal
}
