// @dikaacode
package com.dikacode.update

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

object UpdateDialog {

    fun show(
        activity: AppCompatActivity,
        release: GitHubUpdater.ReleaseInfo,
        asset: GitHubUpdater.Asset,
        isDark: Boolean = false
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(
            activity.resources.getIdentifier("dialog_update", "layout", activity.packageName), null
        )

        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogView)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
        }

        val tvVersion = dialogView.findViewById<TextView>(activity.resources.getIdentifier("tvUpdateVersion", "id", activity.packageName))
        val tvSize = dialogView.findViewById<TextView>(activity.resources.getIdentifier("tvUpdateSize", "id", activity.packageName))
        val tvChangelog = dialogView.findViewById<TextView>(activity.resources.getIdentifier("tvChangelog", "id", activity.packageName))
        val layoutProgress = dialogView.findViewById<View>(activity.resources.getIdentifier("layoutProgress", "id", activity.packageName))
        val tvProgressLabel = dialogView.findViewById<TextView>(activity.resources.getIdentifier("tvProgressLabel", "id", activity.packageName))
        val progressContainer = dialogView.findViewById<View>(activity.resources.getIdentifier("progressContainer", "id", activity.packageName))
        val progressFill = dialogView.findViewById<View>(activity.resources.getIdentifier("progressFill", "id", activity.packageName))
        val tvProgressBytes = dialogView.findViewById<TextView>(activity.resources.getIdentifier("tvProgressBytes", "id", activity.packageName))
        val tvDownloadPath = dialogView.findViewById<TextView>(activity.resources.getIdentifier("tvDownloadPath", "id", activity.packageName))
        val btnCancel = dialogView.findViewById<TextView>(activity.resources.getIdentifier("btnCancel", "id", activity.packageName))
        val btnDownload = dialogView.findViewById<TextView>(activity.resources.getIdentifier("btnDownload", "id", activity.packageName))
        val btnInstall = dialogView.findViewById<TextView>(activity.resources.getIdentifier("btnInstall", "id", activity.packageName))
        val btnOpenFolder = dialogView.findViewById<TextView>(activity.resources.getIdentifier("btnOpenFolder", "id", activity.packageName))

        val currentVer = GitHubUpdater.getCurrentVersionName(activity)
        tvVersion.text = "$currentVer → ${release.tagName}  (${if (release.prerelease) "prerelease" else "stable"})"
        tvSize.text = "${asset.name} • ${GitHubUpdater.formatSize(asset.size)}"
        tvChangelog.text = release.changelog.take(2000)

        var downloadedFile: File? = null
        val destFile = GitHubUpdater.getDownloadFile(activity, asset, release)

        // If already downloaded, show install directly
        if (destFile.exists() && destFile.length() > 0) {
            downloadedFile = destFile
            layoutProgress.visibility = View.VISIBLE
            tvProgressLabel.text = "Already downloaded ✓"
            tvProgressBytes.text = "${GitHubUpdater.formatSize(destFile.length())} • Ready to install"
            tvDownloadPath.visibility = View.VISIBLE
            tvDownloadPath.text = "Saved to:\n${GitHubUpdater.getPublicDownloadPath(destFile)}\n\nYou can delete this file manually to free memory."
            progressFill.post {
                val w = (progressContainer as View).width
                if (w > 0) {
                    val lp = progressFill.layoutParams
                    lp.width = w
                    progressFill.layoutParams = lp
                }
            }
            btnDownload.visibility = View.GONE
            btnInstall.visibility = View.VISIBLE
            btnOpenFolder.visibility = View.VISIBLE
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnOpenFolder.setOnClickListener {
            val path = downloadedFile?.absolutePath ?: destFile.absolutePath
            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("apk_path", path))
            Toast.makeText(activity, "Path copied: $path", Toast.LENGTH_LONG).show()
        }

        btnDownload.setOnClickListener {
            btnDownload.isEnabled = false
            btnDownload.text = "DOWNLOADING..."
            layoutProgress.visibility = View.VISIBLE
            tvDownloadPath.visibility = View.GONE

            activity.lifecycleScope.launch {
                val result = GitHubUpdater.downloadApk(asset, destFile) { pct, read, total ->
                    if (pct >= 0) {
                        tvProgressLabel.text = "Downloading... $pct%"
                    } else {
                        tvProgressLabel.text = "Downloading... ${GitHubUpdater.formatSize(read)}"
                    }
                    val totalStr = if (total > 0) GitHubUpdater.formatSize(total) else "?"
                    tvProgressBytes.text = "${GitHubUpdater.formatSize(read)} / $totalStr"

                    if (pct >= 0) {
                        progressFill.post {
                            val w = (progressContainer.width)
                            if (w > 0) {
                                val lp = progressFill.layoutParams
                                lp.width = ((w * pct) / 100).coerceAtLeast(4)
                                progressFill.layoutParams = lp
                            }
                        }
                    }
                }
                result.onSuccess { file ->
                    downloadedFile = file
                    btnDownload.visibility = View.GONE
                    btnInstall.visibility = View.VISIBLE
                    btnOpenFolder.visibility = View.VISIBLE
                    tvProgressLabel.text = "Download complete ✓"
                    tvProgressBytes.text = "${GitHubUpdater.formatSize(file.length())} • Ready"
                    tvDownloadPath.visibility = View.VISIBLE
                    tvDownloadPath.text = "Saved to:\n${GitHubUpdater.getPublicDownloadPath(file)}\n\nYou can delete this file manually to free memory:\n${file.absolutePath}"
                    Toast.makeText(activity, "Downloaded to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                }.onFailure { e ->
                    btnDownload.isEnabled = true
                    btnDownload.text = "RETRY DOWNLOAD"
                    tvProgressLabel.text = "Failed: ${e.message}"
                    Toast.makeText(activity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnInstall.setOnClickListener {
            val file = downloadedFile ?: destFile
            if (!file.exists()) {
                Toast.makeText(activity, "File not found, please download again", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val canInstall = activity.packageManager.canRequestPackageInstalls().let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) it else true
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(activity, "Please allow 'Install unknown apps' for FastRecorder in Settings", Toast.LENGTH_LONG).show()
                try {
                    activity.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            android.net.Uri.parse("package:${activity.packageName}")
                        )
                    )
                } catch (_: Exception) {}
                return@setOnClickListener
            }
            val ok = GitHubUpdater.installApk(activity, file)
            if (!ok) Toast.makeText(activity, "Could not launch installer", Toast.LENGTH_SHORT).show()
            else dialog.dismiss()
        }

        dialog.show()
    }
}
