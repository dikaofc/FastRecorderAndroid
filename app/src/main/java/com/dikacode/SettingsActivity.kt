package com.dikacode

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dikacode.databinding.ActivitySettingsBinding
import com.dikacode.recorder.AudioMode
import com.dikacode.recorder.FpsType
import com.dikacode.recorder.ResolutionType
import com.dikacode.recorder.SettingsManager
import com.dikacode.recorder.StorageThreshold
import com.dikacode.recorder.StorageThresholdNotifier
import com.dikacode.recorder.StorageUtils
import com.dikacode.recorder.TempFileCleaner
import androidx.lifecycle.lifecycleScope
import com.dikacode.update.GitHubUpdater
import com.dikacode.update.UpdateDialog
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager

    private val directoryPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                try {
                    // Request persistable read/write permissions
                    val takeFlags: Int = (result.data?.flags ?: 0) and
                            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    contentResolver.takePersistableUriPermission(uri, takeFlags.ifZero {
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    })

                    // Validate directory accessibility and writeability
                    if (StorageUtils.validateDirectory(this, uri)) {
                        settingsManager.storageUriString = uri.toString()
                        Toast.makeText(this, "Recording directory updated!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Selected folder is not writable. Kept previous folder.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to set directory: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                updateStorageUsageUI()
            }
        }
    }

    private fun Int.ifZero(default: () -> Int): Int = if (this == 0) default() else this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        // Purge old files on opening settings
        TempFileCleaner.purgeOldTempFiles(this)

        applyThemeUI(settingsManager.darkMode)

        binding.btnBack.setOnClickListener {
            finish()
        }

        setupSpinners()
        setupSwitches()
        setupStorageActions()
        setupUpdateSection()
        updateStorageUsageUI()
        updateCacheSizeDisplay()
    }

    override fun onResume() {
        super.onResume()
        updateStorageUsageUI()
        updateCacheSizeDisplay()
    }

    private fun setupStorageActions() {
        binding.btnSelectDirectory.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            directoryPickerLauncher.launch(intent)
        }

        binding.btnResetDirectory.setOnClickListener {
            settingsManager.storageUriString = null
            Toast.makeText(this, "Reset to default storage (/sdcard/Movies/FastRecorder/)", Toast.LENGTH_SHORT).show()
            updateStorageUsageUI()
        }
    }

    private fun updateStorageUsageUI() {
        val info = StorageUtils.getStorageInfo(this, settingsManager.storageUriString)
        
        binding.tvStorageTypeLabel.text = if (info.isExternal) "EXTERNAL STORAGE / SD CARD" else "INTERNAL STORAGE"
        binding.tvStorageUsageSummary.text = "${info.freeFormatted} Free / ${info.totalFormatted} Total"
        binding.tvStoragePath.text = info.pathDisplay

        binding.storageProgressContainer.post {
            val containerWidth = binding.storageProgressContainer.width
            if (containerWidth > 0) {
                val params = binding.storageProgressFill.layoutParams
                params.width = ((containerWidth * info.percentUsed) / 100).coerceAtLeast(4)
                binding.storageProgressFill.layoutParams = params
            }
        }
    }

    private fun updateCacheSizeDisplay() {
        val sizeBytes = TempFileCleaner.getCacheSize(this)
        val sizeMb = String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
        binding.tvCacheSize.text = sizeMb
    }

    private fun createThemedSpinnerAdapter(items: List<String>): ArrayAdapter<String> {
        val isDark = settingsManager.darkMode
        return object : ArrayAdapter<String>(this, R.layout.item_neo_spinner, items) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#0A0A0A"))
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                if (isDark) {
                    v.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
                    v.setTextColor(Color.parseColor("#FFFFFF"))
                } else {
                    v.setBackgroundResource(R.drawable.bg_neo_spinner)
                    v.setTextColor(Color.parseColor("#0A0A0A"))
                }
                return v
            }
        }.apply {
            setDropDownViewResource(R.layout.item_neo_spinner_dropdown)
        }
    }

    private fun setupSpinners() {
        val resOptions = ResolutionType.values()
        val resAdapter = createThemedSpinnerAdapter(resOptions.map { it.label })
        binding.spinnerRes.adapter = resAdapter
        binding.spinnerRes.setSelection(resOptions.indexOf(settingsManager.resolution))

        binding.spinnerRes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.resolution = resOptions[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val fpsOptions = FpsType.values()
        val fpsAdapter = createThemedSpinnerAdapter(fpsOptions.map { it.label })
        binding.spinnerFps.adapter = fpsAdapter
        binding.spinnerFps.setSelection(fpsOptions.indexOf(settingsManager.fps))

        binding.spinnerFps.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.fps = fpsOptions[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val audioOptions = AudioMode.values()
        val audioLabels = audioOptions.map {
            when (it) {
                AudioMode.NONE -> "Mute (No Audio)"
                AudioMode.MIC -> "Microphone"
                AudioMode.INTERNAL -> "Internal System Audio"
                AudioMode.BOTH -> "Internal + Microphone"
            }
        }
        val audioAdapter = createThemedSpinnerAdapter(audioLabels)
        binding.spinnerAudio.adapter = audioAdapter
        binding.spinnerAudio.setSelection(audioOptions.indexOf(settingsManager.audioMode))

        binding.spinnerAudio.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = audioOptions[position]
                settingsManager.audioMode = selected
                val isInternal = selected == AudioMode.INTERNAL || selected == AudioMode.BOTH
                updateToggle(binding.toggleInternalAudio, isInternal)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val thresholdOptions = StorageThreshold.values()
        val thresholdAdapter = createThemedSpinnerAdapter(thresholdOptions.map { it.label })
        binding.spinnerStorageThreshold.adapter = thresholdAdapter
        binding.spinnerStorageThreshold.setSelection(thresholdOptions.indexOf(settingsManager.storageThreshold))

        binding.spinnerStorageThreshold.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.storageThreshold = thresholdOptions[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSwitches() {
        val isInternalAudio = settingsManager.audioMode == AudioMode.INTERNAL || settingsManager.audioMode == AudioMode.BOTH
        updateToggle(binding.toggleInternalAudio, isInternalAudio)
        binding.toggleInternalAudio.setOnClickListener {
            val currentInternal = settingsManager.audioMode == AudioMode.INTERNAL || settingsManager.audioMode == AudioMode.BOTH
            val newMode = if (currentInternal) AudioMode.NONE else AudioMode.INTERNAL
            settingsManager.audioMode = newMode
            updateToggle(binding.toggleInternalAudio, !currentInternal)
            val audioOptions = AudioMode.values()
            binding.spinnerAudio.setSelection(audioOptions.indexOf(newMode))
        }

        updateToggle(binding.toggleOverlay, settingsManager.showOverlay)
        binding.toggleOverlay.setOnClickListener {
            val newState = !settingsManager.showOverlay
            settingsManager.showOverlay = newState
            updateToggle(binding.toggleOverlay, newState)
        }

        updateToggle(binding.toggleHighPerf, settingsManager.highPerformanceMode)
        binding.toggleHighPerf.setOnClickListener {
            val newState = !settingsManager.highPerformanceMode
            settingsManager.highPerformanceMode = newState
            updateToggle(binding.toggleHighPerf, newState)
        }

        updateToggle(binding.toggleBatterySaver, settingsManager.batterySaverMode)
        binding.toggleBatterySaver.setOnClickListener {
            val newState = !settingsManager.batterySaverMode
            settingsManager.batterySaverMode = newState
            updateToggle(binding.toggleBatterySaver, newState)
        }

        updateToggle(binding.toggleAutoUpload, settingsManager.autoUploadCloud)
        binding.toggleAutoUpload.setOnClickListener {
            val newState = !settingsManager.autoUploadCloud
            settingsManager.autoUploadCloud = newState
            updateToggle(binding.toggleAutoUpload, newState)
        }

        updateToggle(binding.toggleDarkMode, settingsManager.darkMode)
        binding.toggleDarkMode.setOnClickListener {
            val newState = !settingsManager.darkMode
            settingsManager.darkMode = newState
            updateToggle(binding.toggleDarkMode, newState)
            setupSpinners()
            applyThemeUI(newState)
        }

        binding.btnClearCache.setOnClickListener {
            val freedBytes = TempFileCleaner.clearAllCache(this)
            val freedMb = String.format("%.2f", freedBytes / (1024.0 * 1024.0))
            Toast.makeText(this, "Cleared $freedMb MB of temporary cache!", Toast.LENGTH_SHORT).show()
            updateCacheSizeDisplay()
            updateStorageUsageUI()
        }

        binding.btnSecurityDiagnostics.setOnClickListener {
            startActivity(Intent(this, SecurityDiagnosticsActivity::class.java))
        }
    }

    private fun setupUpdateSection() {
        val currentVer = GitHubUpdater.getCurrentVersion(this)
        binding.tvCurrentVersion.text = "Current: $currentVer"
        binding.tvUpdateStatus.text = "Tap check"
        binding.btnCheckUpdate.setOnClickListener { checkForUpdate() }

        // Show last downloaded path if exists
        val updatesDir = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: filesDir, "updates")
        if (updatesDir.exists()) {
            val files = updatesDir.listFiles()?.filter { it.name.endsWith(".apk") }?.sortedByDescending { it.lastModified() }
            if (!files.isNullOrEmpty()) {
                val f = files.first()
                binding.tvUpdateDownloadPath.visibility = View.VISIBLE
                binding.tvUpdateDownloadPath.text = "Last download:\n${f.absolutePath}\n(${GitHubUpdater.formatSize(f.length())}) — delete manually to free memory"
                binding.tvUpdateDownloadPath.setOnClickListener {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("path", f.absolutePath))
                    Toast.makeText(this, "Path copied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkForUpdate() {
        binding.btnCheckUpdate.isEnabled = false
        binding.btnCheckUpdate.text = "CHECKING..."
        binding.tvUpdateStatus.text = "Checking..."
        lifecycleScope.launch {
            when (val res = GitHubUpdater.checkForUpdate(this@SettingsActivity)) {
                is GitHubUpdater.UpdateResult.UpdateAvailable -> {
                    binding.btnCheckUpdate.isEnabled = true
                    binding.btnCheckUpdate.text = "VIEW UPDATE"
                    binding.tvUpdateStatus.text = "Update ${res.release.tagName} available!"
                    binding.tvUpdateStatus.setTextColor(Color.parseColor("#2E7D32"))
                    // Rewire button to open dialog
                    binding.btnCheckUpdate.setOnClickListener {
                        UpdateDialog.show(this@SettingsActivity, res.release, res.asset, settingsManager.darkMode)
                    }
                    // Auto show dialog
                    UpdateDialog.show(this@SettingsActivity, res.release, res.asset, settingsManager.darkMode)
                }
                is GitHubUpdater.UpdateResult.NoUpdate -> {
                    binding.btnCheckUpdate.isEnabled = true
                    binding.btnCheckUpdate.text = "CHECK AGAIN"
                    binding.tvUpdateStatus.text = "You are up to date ✓"
                    binding.tvUpdateStatus.setTextColor(Color.parseColor("#2E7D32"))
                    binding.btnCheckUpdate.setOnClickListener { checkForUpdate() }
                    Toast.makeText(this@SettingsActivity, "Already on latest version", Toast.LENGTH_SHORT).show()
                }
                is GitHubUpdater.UpdateResult.Error -> {
                    binding.btnCheckUpdate.isEnabled = true
                    binding.btnCheckUpdate.text = "RETRY"
                    binding.tvUpdateStatus.text = "Error: ${res.message.take(40)}"
                    binding.tvUpdateStatus.setTextColor(Color.parseColor("#C62828"))
                    binding.btnCheckUpdate.setOnClickListener { checkForUpdate() }
                    Toast.makeText(this@SettingsActivity, "Update check failed: ${res.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateToggle(textView: TextView, isOn: Boolean) {
        if (isOn) {
            textView.text = "ON"
            textView.setTextColor(Color.parseColor("#FFFFFF"))
            textView.setBackgroundResource(R.drawable.bg_neo_toggle_on)
        } else {
            textView.text = "OFF"
            textView.setTextColor(Color.parseColor("#0A0A0A"))
            textView.setBackgroundResource(R.drawable.bg_neo_toggle_off)
        }
    }

    private fun applyThemeUI(isDark: Boolean) {
        if (isDark) {
            window.statusBarColor = Color.parseColor("#121212")
            binding.settingsRoot.setBackgroundColor(Color.parseColor("#121212"))
            binding.settingsCard.setBackgroundResource(R.drawable.bg_neo_card_dark)
            binding.btnBack.setBackgroundResource(R.drawable.bg_neo_button_dark)
            binding.btnBack.setColorFilter(Color.parseColor("#FFFFFF"))

            val whiteColor = Color.parseColor("#FFFFFF")
            val subtextColor = Color.parseColor("#AAAAAA")

            binding.tvSettingsTitle.setTextColor(whiteColor)
            binding.lblResolution.setTextColor(whiteColor)
            binding.lblFps.setTextColor(whiteColor)
            binding.lblAudio.setTextColor(whiteColor)
            binding.lblInternalAudio.setTextColor(whiteColor)
            binding.lblOverlay.setTextColor(whiteColor)
            binding.lblHighPerf.setTextColor(whiteColor)
            binding.lblBatterySaver.setTextColor(whiteColor)
            binding.lblAutoUpload.setTextColor(whiteColor)
            binding.lblDarkMode.setTextColor(whiteColor)
            binding.lblStorageSection.setTextColor(whiteColor)
            binding.lblStorageThreshold.setTextColor(whiteColor)
            binding.lblCache.setTextColor(whiteColor)

            binding.subInternalAudio.setTextColor(subtextColor)
            binding.subOverlay.setTextColor(subtextColor)
            binding.subHighPerf.setTextColor(subtextColor)
            binding.subBatterySaver.setTextColor(subtextColor)
            binding.subAutoUpload.setTextColor(subtextColor)
            binding.subDarkMode.setTextColor(subtextColor)
            binding.subStorageThreshold.setTextColor(subtextColor)
            binding.tvCacheSize.setTextColor(subtextColor)

            binding.frameRes.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            binding.frameFps.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            binding.frameAudio.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            binding.frameStorageThreshold.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            
            binding.storageStatsCard.setBackgroundResource(R.drawable.bg_neo_card_dark)
            binding.tvStorageTypeLabel.setTextColor(subtextColor)
            binding.tvStorageUsageSummary.setTextColor(whiteColor)
            binding.tvStoragePath.setTextColor(whiteColor)
            binding.btnResetDirectory.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            binding.btnResetDirectory.setTextColor(whiteColor)

            binding.ivArrowRes.setColorFilter(whiteColor)
            binding.ivArrowFps.setColorFilter(whiteColor)
            binding.ivArrowAudio.setColorFilter(whiteColor)
            binding.ivArrowStorageThreshold.setColorFilter(whiteColor)

            binding.btnClearCache.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            binding.btnClearCache.setTextColor(whiteColor)
            binding.tvSecurityStatus.setTextColor(Color.parseColor("#00E676"))
            binding.btnSecurityDiagnostics.setBackgroundResource(R.drawable.bg_neo_button_danger)
        } else {
            window.statusBarColor = ContextCompat.getColor(this, R.color.neo_yellow)
            binding.settingsRoot.setBackgroundColor(ContextCompat.getColor(this, R.color.neo_yellow))
            binding.settingsCard.setBackgroundResource(R.drawable.bg_neo_card)
            binding.btnBack.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.btnBack.setColorFilter(Color.parseColor("#0A0A0A"))

            val blackColor = Color.parseColor("#0A0A0A")
            val subtextColor = Color.parseColor("#666666")

            binding.tvSettingsTitle.setTextColor(blackColor)
            binding.lblResolution.setTextColor(blackColor)
            binding.lblFps.setTextColor(blackColor)
            binding.lblAudio.setTextColor(blackColor)
            binding.lblInternalAudio.setTextColor(blackColor)
            binding.lblOverlay.setTextColor(blackColor)
            binding.lblHighPerf.setTextColor(blackColor)
            binding.lblBatterySaver.setTextColor(blackColor)
            binding.lblAutoUpload.setTextColor(blackColor)
            binding.lblDarkMode.setTextColor(blackColor)
            binding.lblStorageSection.setTextColor(blackColor)
            binding.lblStorageThreshold.setTextColor(blackColor)
            binding.lblCache.setTextColor(blackColor)

            binding.subInternalAudio.setTextColor(subtextColor)
            binding.subOverlay.setTextColor(subtextColor)
            binding.subHighPerf.setTextColor(subtextColor)
            binding.subBatterySaver.setTextColor(subtextColor)
            binding.subAutoUpload.setTextColor(subtextColor)
            binding.subDarkMode.setTextColor(subtextColor)
            binding.subStorageThreshold.setTextColor(subtextColor)
            binding.tvCacheSize.setTextColor(Color.parseColor("#555555"))

            binding.frameRes.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.frameFps.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.frameAudio.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.frameStorageThreshold.setBackgroundResource(R.drawable.bg_neo_spinner)

            binding.storageStatsCard.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.tvStorageTypeLabel.setTextColor(Color.parseColor("#555555"))
            binding.tvStorageUsageSummary.setTextColor(blackColor)
            binding.tvStoragePath.setTextColor(blackColor)
            binding.btnResetDirectory.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.btnResetDirectory.setTextColor(blackColor)

            binding.ivArrowRes.setColorFilter(blackColor)
            binding.ivArrowFps.setColorFilter(blackColor)
            binding.ivArrowAudio.setColorFilter(blackColor)
            binding.ivArrowStorageThreshold.setColorFilter(blackColor)

            binding.btnClearCache.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.btnClearCache.setTextColor(blackColor)
            binding.tvSecurityStatus.setTextColor(ContextCompat.getColor(this, R.color.neo_green))
            binding.btnSecurityDiagnostics.setBackgroundResource(R.drawable.bg_neo_button_danger)
        }
    }
}
