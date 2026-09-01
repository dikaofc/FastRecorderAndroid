// @dikaacode
package com.dikacode.recorder

import android.content.Context
import android.content.SharedPreferences

enum class AudioMode {
    NONE, INTERNAL, MIC, BOTH
}

enum class VideoEncoderType {
    DEFAULT, H264, HEVC
}

enum class BitrateType(val value: Int, val label: String) {
    LOW(2000000, "2 Mbps"),
    NORMAL(6000000, "6 Mbps"),
    HIGH(12000000, "12 Mbps"),
    ULTRA(24000000, "24 Mbps")
}

enum class FpsType(val value: Int, val label: String) {
    FPS_30(30, "30 FPS"),
    FPS_60(60, "60 FPS"),
    FPS_90(90, "90 FPS"),
    FPS_120(120, "120 FPS")
}

enum class ResolutionType(val maxDim: Float, val label: String) {
    RES_480(854f, "480p (SD)"),
    RES_720(1280f, "720p (HD)"),
    RES_1080(1920f, "1080p (FHD)"),
    RES_1440(2560f, "1440p (2K)")
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class StorageThreshold(val bytes: Long, val label: String) {
    MB_500(500L * 1024L * 1024L, "500 MB"),
    GB_1(1024L * 1024L * 1024L, "1 GB (Default)"),
    GB_2(2L * 1024L * 1024L * 1024L, "2 GB"),
    GB_5(5L * 1024L * 1024L * 1024L, "5 GB"),
    DISABLED(0L, "Disabled / Off")
}

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("recorder_settings", Context.MODE_PRIVATE)

    var resolution: ResolutionType
        get() = ResolutionType.valueOf(prefs.getString("resolution", ResolutionType.RES_1080.name) ?: ResolutionType.RES_1080.name)
        set(value) = prefs.edit().putString("resolution", value.name).apply()

    var fps: FpsType
        get() = FpsType.valueOf(prefs.getString("fps", FpsType.FPS_30.name) ?: FpsType.FPS_30.name)
        set(value) = prefs.edit().putString("fps", value.name).apply()

    var showOverlay: Boolean
        get() = prefs.getBoolean("show_overlay", true)
        set(value) = prefs.edit().putBoolean("show_overlay", value).apply()

    var audioMode: AudioMode
        get() = AudioMode.valueOf(prefs.getString("audio_mode", AudioMode.INTERNAL.name) ?: AudioMode.INTERNAL.name)
        set(value) = prefs.edit().putString("audio_mode", value.name).apply()

    var videoEncoder: VideoEncoderType
        get() = VideoEncoderType.valueOf(prefs.getString("video_encoder", VideoEncoderType.DEFAULT.name) ?: VideoEncoderType.DEFAULT.name)
        set(value) = prefs.edit().putString("video_encoder", value.name).apply()

    var bitrate: BitrateType
        get() = BitrateType.valueOf(prefs.getString("bitrate", BitrateType.NORMAL.name) ?: BitrateType.NORMAL.name)
        set(value) = prefs.edit().putString("bitrate", value.name).apply()

    var autoUploadCloud: Boolean
        get() = prefs.getBoolean("auto_upload_cloud", false)
        set(value) = prefs.edit().putBoolean("auto_upload_cloud", value).apply()

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", false)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    var batterySaverMode: Boolean
        get() = prefs.getBoolean("battery_saver_mode", false)
        set(value) = prefs.edit().putBoolean("battery_saver_mode", value).apply()

    fun getCloudUrl(recordingId: Long): String? {
        return prefs.getString("cloud_url_$recordingId", null)
    }

    fun setCloudUrl(recordingId: Long, url: String) {
        prefs.edit().putString("cloud_url_$recordingId", url).apply()
    }

    var highPerformanceMode: Boolean
        get() = prefs.getBoolean("high_perf_mode", false)
        set(value) = prefs.edit().putBoolean("high_perf_mode", value).apply()

    var storageUriString: String?
        get() = prefs.getString("storage_uri", null)
        set(value) = prefs.edit().putString("storage_uri", value).apply()

    var storageThreshold: StorageThreshold
        get() = StorageThreshold.valueOf(prefs.getString("storage_threshold", StorageThreshold.GB_1.name) ?: StorageThreshold.GB_1.name)
        set(value) = prefs.edit().putString("storage_threshold", value.name).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        set(value) = prefs.edit().putString("theme_mode", value.name).apply()

    var hasShownDeveloperInfo: Boolean
        get() = prefs.getBoolean("has_shown_dev_info", false)
        set(value) = prefs.edit().putBoolean("has_shown_dev_info", value).apply()
}
