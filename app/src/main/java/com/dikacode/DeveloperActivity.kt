package com.dikacode

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dikacode.databinding.ActivityDeveloperBinding
import com.dikacode.recorder.SettingsManager

class DeveloperActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeveloperBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeveloperBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        applyThemeUI(settingsManager.darkMode)

        // Build identity
        binding.tvDevAppName.text = BuildConfig.CREDIT_DEVELOPER
        binding.tvDevHandle.text = BuildConfig.SECURITY_PACKAGE
        binding.tvDevInfo.text = "dev: ${BuildConfig.CREDIT_DEVELOPER}\n" +
            "release: ${BuildConfig.RELEASE_ID} · ${BuildConfig.VERSION_NAME}"
        binding.tvDevBuild.text = BuildConfig.RELEASE_ID
        binding.tvDevVersion.text = BuildConfig.VERSION_NAME

        binding.btnBack.setOnClickListener { finish() }

        // Local video — tight 16:9 frame (prevents black screen / zero-height wrap)
        binding.devVideo.post {
            val w = binding.cardVideo.width
            if (w > 0) {
                val params = binding.cardVideo.layoutParams
                params.height = (w * 9 / 16)
                binding.cardVideo.layoutParams = params
            }
        }
        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.b4jbxy}")
        binding.devVideo.setVideoURI(videoUri)
        binding.devVideo.setOnPreparedListener { it.isLooping = true; it.start() }
        binding.devVideo.setOnErrorListener { _, _, _ -> true }

        openSocial(BuildConfig.CREDIT_URL, binding.btnTelegram)
        openSocial("https://www.tiktok.com/@dikasecx", binding.btnTikTok)
        openSocial("https://www.instagram.com/xxcdicka", binding.btnInstagram)
        openSocial("https://saweria.co/dikatech", binding.btnDonate)

        setupAnimations()
    }

    private fun openSocial(url: String, btn: View) {
        btn.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupAnimations() {
        val views = listOf(
            binding.tvDevTitle,
            binding.cardVideo,
            binding.cardDevDetails,
            binding.btnTelegram,
            binding.btnTikTok,
            binding.btnInstagram,
            binding.btnDonate
        )
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 20f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 60L)
                .setDuration(280L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            applyThemeUI(settingsManager.darkMode)
        }
    }

    private fun applyThemeUI(isDark: Boolean) {
        val white = Color.parseColor("#FFFFFF")
        val black = Color.parseColor("#0A0A0A")
        val subtext = if (isDark) Color.parseColor("#AAAAAA") else Color.parseColor("#555555")

        if (isDark) {
            window.statusBarColor = Color.parseColor("#121212")
            binding.devRoot.setBackgroundColor(Color.parseColor("#121212"))
            binding.tvDevTitle.setTextColor(white)
            binding.btnBack.setBackgroundResource(R.drawable.bg_neo_button_dark)
            binding.btnBack.setColorFilter(white)
            binding.cardDevDetails.setBackgroundResource(R.drawable.bg_neo_card_dark)
            binding.tvDevAppName.setTextColor(white)
            binding.tvDevInfo.setTextColor(subtext)
            binding.tvDevMessage.setTextColor(subtext)
            binding.tvDevHandle.setTextColor(white)
            binding.tvDevBuild.setTextColor(white)
            binding.tvDevVersion.setTextColor(white)
        } else {
            window.statusBarColor = ContextCompat.getColor(this, R.color.neo_yellow)
            binding.devRoot.setBackgroundColor(ContextCompat.getColor(this, R.color.neo_yellow))
            binding.tvDevTitle.setTextColor(black)
            binding.btnBack.setBackgroundResource(R.drawable.bg_neo_button)
            binding.btnBack.setColorFilter(black)
            binding.cardDevDetails.setBackgroundResource(R.drawable.bg_neo_card)
            binding.tvDevAppName.setTextColor(black)
            binding.tvDevInfo.setTextColor(subtext)
            binding.tvDevMessage.setTextColor(subtext)
            binding.tvDevHandle.setTextColor(black)
            binding.tvDevBuild.setTextColor(black)
            binding.tvDevVersion.setTextColor(black)
        }
    }
}