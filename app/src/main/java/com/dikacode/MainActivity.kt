package com.dikacode

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dikacode.databinding.ActivityMainBinding
import com.dikacode.recorder.AudioMode
import com.dikacode.recorder.SettingsManager
import com.dikacode.recorder.TempFileCleaner
import com.dikacode.service.RecordingForegroundService
import com.dikacode.service.RecordingState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsManager: SettingsManager
    private var pulseAnimator: ObjectAnimator? = null

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this@MainActivity, RecordingForegroundService::class.java).apply {
                action = RecordingForegroundService.ACTION_START
                putExtra(RecordingForegroundService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(RecordingForegroundService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this@MainActivity.startForegroundService(intent)
            } else {
                this@MainActivity.startService(intent)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS]
                ?: (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        } else true

        if (settingsManager.audioMode != AudioMode.NONE && !audioGranted) {
            Toast.makeText(this@MainActivity, "Audio recording permission is required for internal/mic audio capture.", Toast.LENGTH_SHORT).show()
        }

        if (notificationGranted) {
            startCapture()
        } else {
            Toast.makeText(this@MainActivity, "Notification permission is required to run screen recorder.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        // Initialize security system
        initSecurity()

        // Background auto purge of temp files older than 24h
        lifecycleScope.launch {                withContext(Dispatchers.IO) {
                TempFileCleaner.purgeOldTempFiles(this@MainActivity)
            }
        }

        setupUI()
        observeState()

        // Show Developer Info alert only once on initial app launch
        if (!settingsManager.hasShownDeveloperInfo) {
            showDeveloperInfoDialog()
            settingsManager.hasShownDeveloperInfo = true
        }

        // Cleanup APK left from previous update if delete-after-install was checked
        cleanupPendingApkIfNeeded()

        // If launched from update notification, show dialog immediately
        if (intent.getBooleanExtra("show_update", false)) {
            launchUpdateFlowImmediate()
        } else {
            // Auto check for update (silent, once per launch, 3s delay)
            checkUpdateSilently()
        }
    }

    private fun initSecurity() {
        lifecycleScope.launch {                withContext(Dispatchers.IO) {
                try {
                    // Initialize security engine
                    com.dikacode.security.SecurityEngine.initialize(this@MainActivity)

                    // Run full security verification
                    val report = com.dikacode.security.SecurityEngine.verify(this@MainActivity)

                    // Log diagnostics
                    android.util.Log.i("Security", report.diagnostics)

                    // Show user-facing warnings based on policy response
                    withContext(Dispatchers.Main) {
                        val policy = report.policyResponse

                        if (policy.showWarning && policy.warningMessage != null) {
                            Toast.makeText(
                                this@MainActivity,
                                policy.warningMessage,
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        // Attribution check
                        if (report.attributionState != com.dikacode.security.AttributionState.VERIFIED) {
                            android.util.Log.w("Security", "Attribution could not be verified: ${report.attributionState}")
                        }

                        // Log policy decision
                        com.dikacode.security.SecurityPolicy.logPolicyDecision(
                            report.trustState,
                            report.riskScore,
                            "Startup verification complete"
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Security", "Security init failed", e)
                }
            }
        }
    }

    private fun checkUpdateSilently() {
        lifecycleScope.launch {
            kotlinx.coroutines.delay(3000)
            try {
                val prefs = getSharedPreferences("updater", Context.MODE_PRIVATE)
                val lastCheck = prefs.getLong("last_check", 0L)
                if (System.currentTimeMillis() - lastCheck < 6 * 60 * 60 * 1000L) return@launch
                prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
                val res = com.dikacode.update.GitHubUpdater.checkForUpdate(this@MainActivity)
                if (res is com.dikacode.update.GitHubUpdater.UpdateResult.UpdateAvailable) {
                    com.dikacode.update.UpdateNotifier.showUpdateAvailable(this@MainActivity, res.release, res.asset)
                    com.dikacode.update.UpdateDialog.show(this@MainActivity, res.release, res.asset, settingsManager.darkMode)
                    Toast.makeText(this@MainActivity, "Update ${res.release.tagName} available!", Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {}
        }
    }

    private fun launchUpdateFlowImmediate() {
        lifecycleScope.launch {
            try {
                val res = com.dikacode.update.GitHubUpdater.checkForUpdate(this@MainActivity)
                if (res is com.dikacode.update.GitHubUpdater.UpdateResult.UpdateAvailable) {
                    com.dikacode.update.UpdateDialog.show(this@MainActivity, res.release, res.asset, settingsManager.darkMode)
                } else {
                    Toast.makeText(this@MainActivity, "Already on latest version", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Check failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cleanupPendingApkIfNeeded() {
        try {
            val prefs = getSharedPreferences("updater", Context.MODE_PRIVATE)
            val pending = prefs.getString("pending_delete_apk", null) ?: return
            val deleteEnabled = prefs.getBoolean("delete_after_install", true)
            if (!deleteEnabled) return
            val f = java.io.File(pending)
            // If app was updated, versionCode changed — safe to delete old apk
            // Also delete if file is older than 1 day
            if (f.exists()) {
                val ageOk = System.currentTimeMillis() - f.lastModified() > 60 * 1000L
                // Only delete if current version is newer than file's tag or file is stale
                f.delete()
                if (!f.exists()) {
                    android.util.Log.i("Updater", "Deleted pending APK: $pending")
                }
            }
            prefs.edit().remove("pending_delete_apk").apply()
            // Also clean any other stale APKs in updates dir
            val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.let { java.io.File(it, "updates") }
            dir?.listFiles()?.forEach { file ->
                if (file.name.endsWith(".apk") && file.absolutePath != pending) {
                    if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L) file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("show_update", false)) launchUpdateFlowImmediate()
    }

    override fun onResume() {
        super.onResume()
        applyThemeUI(settingsManager.darkMode)
        updateStatusInfo()
    }

    private fun applyThemeUI(isDark: Boolean) {
        if (isDark) {
            window.statusBarColor = Color.parseColor("#121212")
            binding.root.setBackgroundColor(Color.parseColor("#121212"))
            binding.tvTitle.setTextColor(Color.parseColor("#FFFFFF"))
            binding.headerDivider.setBackgroundColor(Color.parseColor("#2A2A2A"))
            binding.btnSettings.setBackgroundResource(R.drawable.bg_neo_button_dark)
            binding.btnSettings.setColorFilter(Color.parseColor("#FFFFFF"))
            binding.tvTimer.setTextColor(Color.parseColor("#FFFFFF"))
            binding.configCard.setBackgroundResource(R.drawable.bg_neo_card_dark)
            binding.tvResValue.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvFpsValue.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvAudioValue.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvSourceValue.setTextColor(Color.parseColor("#FFFFFF"))
            binding.btnGallery.setBackgroundResource(R.drawable.bg_neo_button_dark)
            binding.btnGallery.setColorFilter(Color.parseColor("#FFFFFF"))

            if (!RecordingState.isRecording.value) {
                binding.tvStatus.setTextColor(Color.parseColor("#FFFFFF"))
            }
        } else {
            window.statusBarColor = ContextCompat.getColor(this, R.color.neo_yellow)
            binding.root.setBackgroundColor(ContextCompat.getColor(this, R.color.neo_yellow))
            binding.tvTitle.setTextColor(Color.parseColor("#0A0A0A"))
            binding.headerDivider.setBackgroundColor(Color.parseColor("#0A0A0A"))
            binding.btnSettings.setBackgroundResource(R.drawable.bg_neo_button)
            binding.btnSettings.setColorFilter(Color.parseColor("#0A0A0A"))
            binding.tvTimer.setTextColor(Color.parseColor("#0A0A0A"))
            binding.configCard.setBackgroundResource(R.drawable.bg_neo_card)
            binding.tvResValue.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvFpsValue.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvAudioValue.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvSourceValue.setTextColor(Color.parseColor("#0A0A0A"))
            binding.btnGallery.setBackgroundResource(R.drawable.bg_neo_button)
            binding.btnGallery.setColorFilter(Color.parseColor("#0A0A0A"))
        }
    }

    private fun updateStatusInfo() {
        val res = settingsManager.resolution
        val fps = settingsManager.fps
        val audio = settingsManager.audioMode
        binding.tvResValue.text = res.label.replace("p","p HD").replace(" HD HD"," HD")
        binding.tvFpsValue.text = "${fps.value} FPS"
        binding.tvAudioValue.text = if (audio == AudioMode.NONE) "OFF" else "ON"
        binding.tvSourceValue.text = when(audio) {
            AudioMode.NONE -> "—"
            AudioMode.MIC -> "MIC"
            AudioMode.INTERNAL -> "INTERNAL"
            AudioMode.BOTH -> "BOTH"
        }
    }

    private fun setupUI() {
        binding.btnRecContainer.setOnClickListener {
            it.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).withEndAction { it.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }.start()
            if (RecordingState.isRecording.value) {
                val intent = Intent(this@MainActivity, RecordingForegroundService::class.java).apply {
                    action = RecordingForegroundService.ACTION_STOP
                }
                this@MainActivity.startService(intent)
            } else {
                requestPermissionsAndStart()
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

        binding.btnGallery.setOnClickListener {
            startActivity(Intent(this@MainActivity, GalleryActivity::class.java))
        }
    }

    private fun requestPermissionsAndStart() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            startCapture()
        }
    }

    private fun startCapture() {
        val projectionManager = this@MainActivity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun observeState() {
        lifecycleScope.launch {
            RecordingState.isRecording.collectLatest { isRecording ->
                if (isRecording) {
                    binding.tvStatus.text = "● RECORDING"
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_pill_red)
                    binding.tvStatus.setTextColor(Color.parseColor("#FFFFFF"))
                    binding.tvRecIcon.text = "■"
                    binding.tvRecIcon.textSize = 28f
                    binding.recRing.alpha = 1f
                    startPulseAnim()
                } else {
                    binding.tvStatus.text = "● READY"
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_pill_black)
                    binding.tvStatus.setTextColor(Color.parseColor("#FFFFFF"))
                    binding.tvRecIcon.text = "●"
                    binding.tvRecIcon.textSize = 36f
                    binding.recRing.alpha = 0f
                    stopPulseAnim()
                }
            }
        }
        lifecycleScope.launch {
            RecordingState.durationSeconds.collectLatest { duration ->
                val m = duration / 60
                val s = duration % 60
                binding.tvTimer.text = String.format("%02d:%02d:00", m, s)
            }
        }
    }

    private fun startPulseAnim() {
        if (pulseAnimator == null) {
            val scaleX = PropertyValuesHolder.ofFloat("scaleX", 1f, 1.5f)
            val scaleY = PropertyValuesHolder.ofFloat("scaleY", 1f, 1.5f)
            val alpha = PropertyValuesHolder.ofFloat("alpha", 1f, 0f)
            pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.recPulse, scaleX, scaleY, alpha).apply {
                duration = 1200
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }
        }
        binding.recPulse.alpha = 1f
        pulseAnimator?.start()
    }

    private fun stopPulseAnim() {
        pulseAnimator?.cancel()
        binding.recPulse.alpha = 0f
    }

    private fun showDeveloperInfoDialog() {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_developer_info)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
        }

        val isDark = settingsManager.darkMode
        val dialogDevRoot = dialog.findViewById<View>(R.id.dialogDevRoot)
        val ivDevIcon = dialog.findViewById<ImageView>(R.id.ivDevIcon)
        val tvDevTitle = dialog.findViewById<TextView>(R.id.tvDevTitle)
        val cardDevDetails = dialog.findViewById<LinearLayout>(R.id.cardDevDetails)
        val tvDevAppName = dialog.findViewById<TextView>(R.id.tvDevAppName)
        val tvDevMessage = dialog.findViewById<TextView>(R.id.tvDevMessage)
        val tvDevHandle = dialog.findViewById<TextView>(R.id.tvDevHandle)
        val btnTelegram = dialog.findViewById<LinearLayout>(R.id.btnTelegram)
        val btnCloseDevInfo = dialog.findViewById<TextView>(R.id.btnCloseDevInfo)

        if (isDark) {
            val white = Color.parseColor("#FFFFFF")
            val subtext = Color.parseColor("#AAAAAA")

            dialogDevRoot.setBackgroundResource(R.drawable.bg_neo_card_dark)
            ivDevIcon.setColorFilter(white)
            tvDevTitle.setTextColor(white)
            cardDevDetails.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            tvDevAppName.setTextColor(white)
            tvDevMessage.setTextColor(white)
            tvDevHandle.setTextColor(subtext)

            btnCloseDevInfo.setBackgroundResource(R.drawable.bg_neo_button_dark)
            btnCloseDevInfo.setTextColor(white)
        }

        btnTelegram.setOnClickListener {
            val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/dikaacode")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(telegramIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open Telegram link", Toast.LENGTH_SHORT).show()
            }
        }

        btnCloseDevInfo.setOnClickListener {
            dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.btnCloseX)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
