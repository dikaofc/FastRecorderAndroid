// @dikaacode
package com.dikacode.security

/**
 * Security signal definitions.
 * Each signal represents a specific security observation.
 */
sealed class SecuritySignal {
    abstract val domain: RiskAssessment.SignalDomain
    abstract val detail: String

    // ─── Identity domain ──────────────────────────────────────────
    data class SignalPackageNameChanged(
        val expected: String,
        val actual: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.IDENTITY
        override val detail = "Package name: expected=$expected, actual=$actual"
    }

    data class SignalLabelChanged(
        val expected: String,
        val actual: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.IDENTITY
        override val detail = "App label: expected=$expected, actual=$actual"
    }

    data class SignalUnexpectedBuildMetadata(
        val detail_: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.IDENTITY
        override val detail = detail_
    }

    // ─── Signature domain ─────────────────────────────────────────
    data class SignalCertificateMismatch(
        val expectedHash: String,
        val actualHash: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.SIGNATURE
        override val detail = "Certificate mismatch: expected=${expectedHash.take(16)}..., actual=${actualHash.take(16)}..."
    }

    data class SignalApkResigned(
        val detail_: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.SIGNATURE
        override val detail = detail_
    }

    // ─── Binary integrity domain ──────────────────────────────────
    data class SignalDexMismatch(
        val expectedHash: String,
        val actualHash: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.BINARY_INTEGRITY
        override val detail = "DEX integrity mismatch"
    }

    data class SignalNativeLibraryMismatch(
        val library: String,
        val expectedHash: String,
        val actualHash: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.BINARY_INTEGRITY
        override val detail = "Native library mismatch: $library"
    }

    data class SignalIntegrityManifestMismatch(
        val component: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.BINARY_INTEGRITY
        override val detail = "Integrity manifest mismatch: $component"
    }

    // ─── Manifest domain ──────────────────────────────────────────
    data class SignalManifestIntegrityMismatch(
        val detail_: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.MANIFEST
        override val detail = detail_
    }

    // ─── Attribution domain ───────────────────────────────────────
    data class SignalAttributionSignatureInvalid(
        val detail_: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.ATTRIBUTION
        override val detail = detail_
    }

    data class SignalAttributionRecordMissing(
        val detail_: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.ATTRIBUTION
        override val detail = detail_
    }

    // ─── Runtime domain ───────────────────────────────────────────
    data class SignalDebuggerAttached(
        val detail_: String = "Debugger detected"
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.RUNTIME
        override val detail = detail_
    }

    data class SignalHookingFrameworkDetected(
        val framework: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.RUNTIME
        override val detail = "Hooking framework detected: $framework"
    }

    data class SignalInjectedLibraryDetected(
        val library: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.RUNTIME
        override val detail = "Injected library detected: $library"
    }

    // ─── Build domain ─────────────────────────────────────────────
    data class SignalDebugBuildDetected(
        val detail_: String = "Debug build"
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.BUILD
        override val detail = detail_
    }

    data class SignalUnofficialBuild(
        val detail_: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.BUILD
        override val detail = detail_
    }

    // ─── Environment domain ───────────────────────────────────────
    data class SignalEmulatorDetected(
        val detail_: String = "Emulator environment"
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.ENVIRONMENT
        override val detail = detail_
    }

    data class SignalRootDetected(
        val detail_: String = "Rooted device"
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.ENVIRONMENT
        override val detail = detail_
    }

    // ─── Backend domain ───────────────────────────────────────────
    data class SignalBackendUnofficialBuild(
        val detail_: String
    ) : SecuritySignal() {
        override val domain = RiskAssessment.SignalDomain.BACKEND
        override val detail = detail_
    }
}
