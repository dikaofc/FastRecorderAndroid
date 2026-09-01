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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

        // Background auto purge of temp files older than 24h
        lifecycleScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
            binding.btnSettings.setBackgroundResource(R.drawable.bg_neo_button_dark)
            binding.btnSettings.setColorFilter(Color.parseColor("#FFFFFF"))
            binding.tvTimer.setTextColor(Color.parseColor("#FFFFFF"))
            binding.configCard.setBackgroundResource(R.drawable.bg_neo_card_dark)
            binding.tvResolution.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvAudioInfo.setTextColor(Color.parseColor("#AAAAAA"))
            binding.btnGallery.setBackgroundResource(R.drawable.bg_neo_button_dark)
            binding.btnGallery.setColorFilter(Color.parseColor("#FFFFFF"))
            
            if (!RecordingState.isRecording.value) {
                binding.tvStatus.setTextColor(Color.parseColor("#FFFFFF"))
            }
        } else {
            window.statusBarColor = ContextCompat.getColor(this, R.color.neo_yellow)
            binding.root.setBackgroundColor(ContextCompat.getColor(this, R.color.neo_yellow))
            binding.tvTitle.setTextColor(Color.parseColor("#0A0A0A"))
            binding.btnSettings.setBackgroundResource(R.drawable.bg_neo_button)
            binding.btnSettings.setColorFilter(Color.parseColor("#0A0A0A"))
            binding.tvTimer.setTextColor(Color.parseColor("#0A0A0A"))
            binding.configCard.setBackgroundResource(R.drawable.bg_neo_card)
            binding.tvResolution.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvAudioInfo.setTextColor(Color.parseColor("#333333"))
            binding.btnGallery.setBackgroundResource(R.drawable.bg_neo_button)
            binding.btnGallery.setColorFilter(Color.parseColor("#0A0A0A"))

            if (!RecordingState.isRecording.value) {
                binding.tvStatus.setTextColor(Color.parseColor("#0A0A0A"))
            }
        }
    }

    private fun updateStatusInfo() {
        val res = settingsManager.resolution
        val fps = settingsManager.fps
        val audio = settingsManager.audioMode
        val saver = if (settingsManager.batterySaverMode) " | Saver" else ""

        binding.tvResolution.text = "Res: ${res.label} | FPS: ${fps.value}$saver"
        binding.tvAudioInfo.text = "Audio: ${audio.name}"
    }

    private fun setupUI() {
        binding.btnRecContainer.setOnClickListener {
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
                    binding.tvStatus.text = "RECORDING"
                    binding.tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.neo_red))
                    startPulseAnim()
                } else {
                    binding.tvStatus.text = "READY"
                    val readyColor = if (settingsManager.darkMode) Color.parseColor("#FFFFFF") else ContextCompat.getColor(this@MainActivity, R.color.neo_black)
                    binding.tvStatus.setTextColor(readyColor)
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

        dialog.show()
    }
}
