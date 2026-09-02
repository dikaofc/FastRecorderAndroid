package com.dikacode

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dikacode.databinding.ActivitySecurityDiagnosticsBinding
import com.dikacode.recorder.SettingsManager
import com.dikacode.security.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Security diagnostics screen.
 * Shows current trust state, risk score, active signals, and diagnostics.
 */
class SecurityDiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecurityDiagnosticsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        applyThemeUI(settingsManager.darkMode)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRefresh.setOnClickListener { runSecurityCheck() }

        runSecurityCheck()
    }

    override fun onResume() {
        super.onResume()
        applyThemeUI(settingsManager.darkMode)
    }

    private fun runSecurityCheck() {
        lifecycleScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    SecurityEngine.verify(this@SecurityDiagnosticsActivity)
                }
                displayReport(report)
            } catch (e: Exception) {
                Toast.makeText(this@SecurityDiagnosticsActivity, "Security check failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayReport(report: SecurityEngine.SecurityReport) {
        val isDark = settingsManager.darkMode
        val textColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#0A0A0A")
        val subtextColor = if (isDark) Color.parseColor("#AAAAAA") else Color.parseColor("#666666")

        // ── Trust State Banner ──
        binding.tvTrustState.text = report.trustState.name
        binding.tvTrustState.setTextColor(getTrustStateColor(report.trustState))

        val description = when (report.trustState) {
            TrustState.TRUSTED -> "No threats detected"
            TrustState.LOW_RISK -> "Minor anomalies detected"
            TrustState.SUSPICIOUS -> "Manipulation indicators found"
            TrustState.HIGH_RISK -> "Strong evidence of modification"
            TrustState.UNTRUSTED -> "Build is very likely untrusted"
        }
        binding.tvTrustDescription.text = description

        // ── Risk Score ──
        binding.tvRiskScoreValue.text = report.riskScore.toString()
        binding.riskProgressFill.post {
            val containerWidth = (binding.riskProgressFill.parent as View).width
            if (containerWidth > 0) {
                val params = binding.riskProgressFill.layoutParams
                params.width = ((containerWidth * report.riskScore) / 100).coerceAtLeast(4)
                binding.riskProgressFill.layoutParams = params
            }
        }

        // ── Response Level ──
        val policy = report.policyResponse
        binding.tvResponseLevel.text = policy.responseLevel.name

        // ── Confidence ──
        binding.tvConfidence.text = report.confidence.name

        // ── Attribution ──
        binding.tvAttributionState.text = report.attributionState.name
        binding.tvAttributionState.setTextColor(
            when (report.attributionState) {
                AttributionState.VERIFIED -> ContextCompat.getColor(this, R.color.neo_green)
                AttributionState.MISSING -> ContextCompat.getColor(this, R.color.neo_red)
                AttributionState.TAMPERED -> ContextCompat.getColor(this, R.color.neo_red)
                AttributionState.UNVERIFIED -> Color.parseColor("#FF9800")
            }
        )
        binding.tvAttributionAuthor.text = AttributionGuard.ATTRIBUTION_AUTHOR

        // ── Domain Contributions ──
        binding.domainContainer.removeAllViews()
        if (report.domainContributions.isEmpty()) {
            addNoDataTextView(binding.domainContainer, "No active domains", textColor)
        } else {
            for ((domain, contribution) in report.domainContributions.entries.sortedByDescending { it.value }) {
                addDomainRow(domain.name, contribution, textColor, subtextColor)
            }
        }

        // ── Active Signals ──
        binding.signalsContainer.removeAllViews()
        if (report.signals.isEmpty()) {
            addNoDataTextView(binding.signalsContainer, "No active signals", textColor)
        } else {
            for (signal in report.signals) {
                addSignalRow(signal.domain.name, signal.detail, textColor, subtextColor)
            }
        }

        // ── Correlation Clusters ──
        binding.clustersContainer.removeAllViews()
        if (report.correlationClusters.isEmpty()) {
            addNoDataTextView(binding.clustersContainer, "No correlation clusters detected", textColor)
        } else {
            for (cluster in report.correlationClusters) {
                addClusterRow(cluster.clusterId, cluster.rootCause, cluster.signals.size, textColor, subtextColor)
            }
        }

        // ── Diagnostics Log ──
        binding.tvDiagnostics.text = report.diagnostics
    }

    private fun addDomainRow(domain: String, contribution: Int, textColor: Int, subtextColor: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6, 0, 6)
        }

        val domainName = TextView(this).apply {
            text = domain
            textSize = 12f
            setTextColor(textColor)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 }
        }

        val score = TextView(this).apply {
            text = "$contribution pts"
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(
                when {
                    contribution >= 40 -> ContextCompat.getColor(this@SecurityDiagnosticsActivity, R.color.neo_red)
                    contribution >= 20 -> Color.parseColor("#FF9800")
                    else -> ContextCompat.getColor(this@SecurityDiagnosticsActivity, R.color.neo_green)
                }
            )
        }

        row.addView(domainName)
        row.addView(score)
        binding.domainContainer.addView(row)
    }

    private fun addSignalRow(domain: String, detail: String, textColor: Int, subtextColor: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 4)
        }

        val domainLabel = TextView(this).apply {
            text = "[$domain]"
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(subtextColor)
        }

        val signalDetail = TextView(this).apply {
            text = detail
            textSize = 11f
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(textColor)
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                breakStrategy = 0 // BREAK_STRATEGY_SIMPLE
                hyphenationFrequency = 0 // HYPHENATION_FREQUENCY_NONE
            }
        }

        row.addView(domainLabel)
        row.addView(signalDetail)
        binding.signalsContainer.addView(row)
    }

    private fun addClusterRow(clusterId: String, rootCause: String, signalCount: Int, textColor: Int, subtextColor: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 4)
        }

        val clusterLabel = TextView(this).apply {
            text = "[$clusterId] — $signalCount signals"
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(subtextColor)
        }

        val cause = TextView(this).apply {
            text = rootCause
            textSize = 11f
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(textColor)
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                breakStrategy = 0 // BREAK_STRATEGY_SIMPLE
                hyphenationFrequency = 0 // HYPHENATION_FREQUENCY_NONE
            }
        }

        row.addView(clusterLabel)
        row.addView(cause)
        binding.clustersContainer.addView(row)
    }

    private fun addNoDataTextView(container: LinearLayout, text: String, textColor: Int) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor("#999999"))
            setPadding(0, 8, 0, 8)
        }
        container.addView(tv)
    }

    private fun getTrustStateColor(state: TrustState): Int {
        return when (state) {
            TrustState.TRUSTED -> ContextCompat.getColor(this, R.color.neo_green)
            TrustState.LOW_RISK -> Color.parseColor("#FF9800")
            TrustState.SUSPICIOUS -> Color.parseColor("#FF5722")
            TrustState.HIGH_RISK -> ContextCompat.getColor(this, R.color.neo_red)
            TrustState.UNTRUSTED -> Color.parseColor("#B71C1C")
        }
    }

    private fun applyThemeUI(isDark: Boolean) {
        if (isDark) {
            window.statusBarColor = Color.parseColor("#121212")
            binding.securityRoot.setBackgroundColor(Color.parseColor("#121212"))
            binding.securityCard.setBackgroundResource(R.drawable.bg_neo_card_dark)
            binding.btnBack.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            binding.btnBack.setColorFilter(Color.parseColor("#FFFFFF"))
            binding.tvSecurityTitle.setTextColor(Color.parseColor("#FFFFFF"))
            binding.trustStateBanner.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            binding.tvTrustStateLabel.setTextColor(Color.parseColor("#AAAAAA"))
            binding.tvTrustDescription.setTextColor(Color.parseColor("#AAAAAA"))
            binding.tvRiskScoreValue.setTextColor(Color.parseColor("#FFFFFF"))
            binding.lblRiskScore.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvResponseLevel.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvResponseLevel.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            binding.lblResponseLevel.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvConfidence.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvConfidence.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            binding.lblConfidence.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvAttributionState.setTextColor(Color.parseColor("#00E676"))
            binding.tvAttributionAuthor.setTextColor(Color.parseColor("#AAAAAA"))
            binding.attributionRow.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            binding.lblAttribution.setTextColor(Color.parseColor("#FFFFFF"))
            binding.lblDomains.setTextColor(Color.parseColor("#FFFFFF"))
            binding.lblSignals.setTextColor(Color.parseColor("#FFFFFF"))
            binding.lblClusters.setTextColor(Color.parseColor("#FFFFFF"))
            binding.lblDiagnostics.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvDiagnostics.setTextColor(Color.parseColor("#CCCCCC"))
            binding.tvDiagnostics.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            binding.btnRefresh.setBackgroundResource(R.drawable.bg_neo_toggle_on)
        } else {
            window.statusBarColor = ContextCompat.getColor(this, R.color.neo_yellow)
            binding.securityRoot.setBackgroundColor(ContextCompat.getColor(this, R.color.neo_yellow))
            binding.securityCard.setBackgroundResource(R.drawable.bg_neo_card)
            binding.btnBack.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.btnBack.setColorFilter(Color.parseColor("#0A0A0A"))
            binding.tvSecurityTitle.setTextColor(Color.parseColor("#0A0A0A"))
            binding.trustStateBanner.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.tvTrustStateLabel.setTextColor(Color.parseColor("#666666"))
            binding.tvTrustDescription.setTextColor(Color.parseColor("#666666"))
            binding.tvRiskScoreValue.setTextColor(Color.parseColor("#0A0A0A"))
            binding.lblRiskScore.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvResponseLevel.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvResponseLevel.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.lblResponseLevel.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvConfidence.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvConfidence.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.lblConfidence.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvAttributionState.setTextColor(Color.parseColor("#00E676"))
            binding.tvAttributionAuthor.setTextColor(Color.parseColor("#666666"))
            binding.attributionRow.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.lblAttribution.setTextColor(Color.parseColor("#0A0A0A"))
            binding.lblDomains.setTextColor(Color.parseColor("#0A0A0A"))
            binding.lblSignals.setTextColor(Color.parseColor("#0A0A0A"))
            binding.lblClusters.setTextColor(Color.parseColor("#0A0A0A"))
            binding.lblDiagnostics.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvDiagnostics.setTextColor(Color.parseColor("#333333"))
            binding.tvDiagnostics.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.btnRefresh.setBackgroundResource(R.drawable.bg_neo_toggle_on)
        }
    }
}
