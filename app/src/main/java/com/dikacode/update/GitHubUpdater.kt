// @dikaacode
package com.dikacode.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.dikacode.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object GitHubUpdater {
    private const val TAG = "GitHubUpdater"
    const val REPO = "dikaofc/FastRecorderAndroid"
    const val API_RELEASES = "https://api.github.com/repos/dikaofc/FastRecorderAndroid/releases?per_page=10"
    const val REPO_URL = "https://github.com/dikaofc/FastRecorderAndroid"

    private val client by lazy {
        OkHttpClient.Builder().followRedirects(true).build()
    }

    data class Asset(
        val name: String,
        val browserDownloadUrl: String,
        val size: Long,
        val contentType: String = ""
    )

    data class ReleaseInfo(
        val tagName: String,
        val name: String,
        val body: String,
        val htmlUrl: String,
        val publishedAt: String,
        val prerelease: Boolean,
        val assets: List<Asset>
    ) {
        fun findApkAsset(preferRelease: Boolean = true): Asset? {
            val releaseApk = assets.find { it.name == "app-release.apk" }
            if (releaseApk != null) return releaseApk
            val anyRelease = assets.find { it.name.contains("release", true) && it.name.endsWith(".apk") }
            if (anyRelease != null) return anyRelease
            return assets.find { it.name.endsWith(".apk") }
        }
        val versionLabel: String get() = tagName.removePrefix("v")
        val changelog: String get() = body.ifBlank { "No changelog provided." }
    }

    sealed class UpdateResult {
        data class UpdateAvailable(val release: ReleaseInfo, val asset: Asset) : UpdateResult()
        data class NoUpdate(val release: ReleaseInfo? = null) : UpdateResult()
        data class Error(val message: String, val cause: Throwable? = null) : UpdateResult()
    }

    fun getCurrentVersion(context: Context): String {
        return try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val vc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) pi.longVersionCode.toString() else pi.versionCode.toString()
            "${pi.versionName ?: "1.0"} ($vc)"
        } catch (e: Exception) { BuildConfig.VERSION_NAME ?: "1.0" }
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: BuildConfig.VERSION_NAME ?: "1.0"
        } catch (e: Exception) { BuildConfig.VERSION_NAME ?: "1.0" }
    }

    private fun parseTagVersion(tag: String): List<Int> {
        return tag.removePrefix("v").removePrefix("V").split(".", "-", "_")
            .mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
    }

    fun isNewerVersion(latestTag: String, currentTag: String): Boolean {
        val latest = parseTagVersion(latestTag)
        val cur = parseTagVersion(currentTag)
        val maxLen = maxOf(latest.size, cur.size)
        for (i in 0 until maxLen) {
            val l = latest.getOrElse(i) { 0 }
            val c = cur.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    suspend fun checkForUpdate(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(API_RELEASES)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "FastRecorder-Updater")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext UpdateResult.Error("GitHub API ${resp.code}: ${resp.message}")
                }
                val body = resp.body?.string() ?: return@withContext UpdateResult.Error("Empty response")
                val arr = JSONArray(body)
                if (arr.length() == 0) return@withContext UpdateResult.NoUpdate()
                var latest: ReleaseInfo? = null
                var latestAsset: Asset? = null
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.optBoolean("draft", false)) continue
                    if (obj.optBoolean("prerelease", false)) continue
                    val rel = parseRelease(obj) ?: continue
                    val asset = rel.findApkAsset()
                    if (asset != null) {
                        latest = rel
                        latestAsset = asset
                        break
                    }
                }
                if (latest == null || latestAsset == null) {
                    return@withContext UpdateResult.NoUpdate()
                }
                val currentTag = "v" + getCurrentVersionName(context)
                val newer = isNewerVersion(latest.tagName, currentTag)
                if (newer) {
                    UpdateResult.UpdateAvailable(latest, latestAsset)
                } else {
                    UpdateResult.NoUpdate(latest)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkForUpdate failed", e)
            UpdateResult.Error(e.message ?: "Unknown error", e)
        }
    }

    private fun parseRelease(obj: JSONObject): ReleaseInfo? {
        return try {
            val tag = obj.optString("tag_name", "")
            if (tag.isBlank()) return null
            val assetsJson = obj.optJSONArray("assets") ?: JSONArray()
            val assets = mutableListOf<Asset>()
            for (i in 0 until assetsJson.length()) {
                val a = assetsJson.getJSONObject(i)
                assets.add(
                    Asset(
                        name = a.optString("name", ""),
                        browserDownloadUrl = a.optString("browser_download_url", ""),
                        size = a.optLong("size", 0L),
                        contentType = a.optString("content_type", "")
                    )
                )
            }
            ReleaseInfo(
                tagName = tag,
                name = obj.optString("name", tag),
                body = obj.optString("body", ""),
                htmlUrl = obj.optString("html_url", REPO_URL),
                publishedAt = obj.optString("published_at", ""),
                prerelease = obj.optBoolean("prerelease", false),
                assets = assets
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseRelease failed", e)
            null
        }
    }

    fun getDownloadFile(context: Context, asset: Asset, release: ReleaseInfo): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        val safeTag = release.tagName.replace("/", "_")
        val fileName = "FastRecorder-${safeTag}-${asset.name}"
        return File(dir, fileName)
    }

    fun getPublicDownloadPath(file: File): String = file.absolutePath

    suspend fun downloadApk(
        asset: Asset,
        destFile: File,
        onProgress: (percent: Int, bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destFile.parentFile?.mkdirs()
            val tmp = File(destFile.absolutePath + ".tmp")
            val req = Request.Builder()
                .url(asset.browserDownloadUrl)
                .header("User-Agent", "FastRecorder-Updater")
                .header("Accept", "application/octet-stream")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Download failed: HTTP ${resp.code}"))
                }
                val body = resp.body ?: return@withContext Result.failure(Exception("Empty body"))
                val total = body.contentLength()
                var read: Long = 0
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(32 * 1024)
                        var n: Int
                        var lastPercent = -1
                        while (input.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = ((read * 100) / total).toInt().coerceIn(0, 100)
                                if (pct != lastPercent) {
                                    lastPercent = pct
                                    withContext(Dispatchers.Main) { onProgress(pct, read, total) }
                                }
                            } else {
                                withContext(Dispatchers.Main) { onProgress(-1, read, total) }
                            }
                        }
                    }
                }
            }
            if (tmp.exists()) {
                if (destFile.exists()) destFile.delete()
                tmp.renameTo(destFile)
            }
            withContext(Dispatchers.Main) { onProgress(100, read, 0) }
            Result.success(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            Result.failure(e)
        }
    }

    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "install failed", e)
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (e2: Exception) {
                Log.e(TAG, "fallback install also failed", e2)
                false
            }
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown size"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.1f MB", mb)
    }
}
