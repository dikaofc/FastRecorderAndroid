// @dikaacode
package com.dikacode.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object UpdateNotifier {
    private const val CHANNEL_ID = "fastrecorder_update"
    private const val NOTIF_ID = 9401

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifies when a new FastRecorder version is available"
                    enableVibration(true)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun showUpdateAvailable(context: Context, release: GitHubUpdater.ReleaseInfo, asset: GitHubUpdater.Asset) {
        ensureChannel(context)
        // Intent to open MainActivity and show dialog on next launch via flag
        val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
        prefs.edit().putString("pending_update_tag", release.tagName).apply()

        val intent = Intent(context, com.dikacode.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_update", true)
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Install intent via FileProvider will be handled by dialog, notification action opens app
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("FastRecorder ${release.tagName} available")
            .setContentText("${asset.name} • ${GitHubUpdater.formatSize(asset.size)} — tap to update")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${release.body.take(500)}\nTap to download & install."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addAction(android.R.drawable.stat_sys_download, "Update", pending)

        try {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
            }
        } catch (_: SecurityException) {}
    }

    fun cancel(context: Context) {
        try { NotificationManagerCompat.from(context).cancel(NOTIF_ID) } catch (_: Exception) {}
    }
}
