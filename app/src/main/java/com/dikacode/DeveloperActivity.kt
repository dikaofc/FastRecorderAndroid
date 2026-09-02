package com.dikacode

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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

        binding.tvDevInfo.text = "dev: ${BuildConfig.CREDIT_DEVELOPER}\n" +
            "build: ${BuildConfig.RELEASE_ID} · ${BuildConfig.VERSION_NAME}\n" +
            "pkg: ${BuildConfig.SECURITY_PACKAGE}"

        binding.btnBack.setOnClickListener { finish() }

        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.b4jbxy}")
        binding.devVideo.setVideoURI(videoUri)
        binding.devVideo.setOnPreparedListener { it.isLooping = true; it.start() }
        binding.devVideo.setOnErrorListener { _, _, _ -> true }

        openSocial(BuildConfig.CREDIT_URL, binding.btnTelegram)
        openSocial("https://www.tiktok.com/@dikasecx", binding.btnTikTok)
        openSocial("https://www.instagram.com/xxcdicka", binding.btnInstagram)
    }

    private fun openSocial(url: String, btn: android.view.View) {
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

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            applyThemeUI(settingsManager.darkMode)
        }
    }

    private fun applyThemeUI(isDark: Boolean) {
        val white = Color.parseColor("#FFFFFF")
        val black = Color.parseColor("#0A0A0A")
        val subtext = if (isDark) Color.parseColor("#AAAAAA") else Color.parseColor("#666666")

        if (isDark) {
            window.statusBarColor = Color.parseColor("#121212")
            binding.devRoot.setBackgroundColor(Color.parseColor("#121212"))
            binding.tvDevTitle.setTextColor(white)
            binding.btnBack.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            binding.btnBack.setColorFilter(white)
            binding.cardDevDetails.setBackgroundResource(R.drawable.bg_neo_card_dark)
            binding.tvDevAppName.setTextColor(white)
            binding.tvDevInfo.setTextColor(white)
            binding.tvDevMessage.setTextColor(white)
            binding.tvDevHandle.setTextColor(subtext)
        } else {
            window.statusBarColor = ContextCompat.getColor(this, R.color.neo_yellow)
            binding.devRoot.setBackgroundColor(ContextCompat.getColor(this, R.color.neo_yellow))
            binding.tvDevTitle.setTextColor(black)
            binding.btnBack.setBackgroundResource(R.drawable.bg_neo_spinner)
            binding.btnBack.setColorFilter(black)
            binding.cardDevDetails.setBackgroundResource(R.drawable.bg_neo_card)
            binding.tvDevAppName.setTextColor(black)
            binding.tvDevInfo.setTextColor(black)
            binding.tvDevMessage.setTextColor(black)
            binding.tvDevHandle.setTextColor(subtext)
        }
    }
}
