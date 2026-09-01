// @dikaacode
package com.dikacode.recorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.dikacode.GalleryActivity
import com.dikacode.R
import com.dikacode.SettingsActivity

object StorageThresholdNotifier {

    private const val CHANNEL_ID = "storage_alert_channel"
    private const val NOTIFICATION_ID = 9001

    /**
     * Calculates total bytes occupied by the app's video recordings.
     */
    fun calculateTotalRecordingsBytes(context: Context): Long {
        var totalBytes = 0L
        val settings = SettingsManager(context)
        val treeUriStr = settings.storageUriString

        try {
            if (treeUriStr != null) {
                val docDir = DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr))
                if (docDir != null && docDir.exists()) {
                    docDir.listFiles().forEach { file ->
                        val name = file.name ?: ""
                        if (file.isFile && (name.endsWith(".mp4", ignoreCase = true) || file.type?.startsWith("video/") == true)) {
                            totalBytes += file.length()
                        }
                    }
                    return totalBytes
                }
            }

            // MediaStore Fallback
            val projection = arrayOf(MediaStore.Video.Media.SIZE)
            val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%FastRecorder%")

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                while (cursor.moveToNext()) {
                    totalBytes += cursor.getLong(sizeCol)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return totalBytes
    }

    /**
     * Checks if the total recording storage exceeds the user-configured threshold.
     * If exceeded, triggers a high-priority notification.
     */
    fun checkAndNotifyIfExceeded(context: Context) {
        val settings = SettingsManager(context)
        val threshold = settings.storageThreshold
        if (threshold == StorageThreshold.DISABLED || threshold.bytes <= 0) {
            return
        }

        val totalBytes = calculateTotalRecordingsBytes(context)
        if (totalBytes >= threshold.bytes) {
            postStorageWarningNotification(context, totalBytes, threshold)
        }
    }

    private fun postStorageWarningNotification(
        context: Context,
        totalBytes: Long,
        threshold: StorageThreshold
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Storage Limit Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when screen recordings exceed configured storage threshold"
            }
            nm.createNotificationChannel(channel)
        }

        val formattedUsed = StorageUtils.formatBytes(totalBytes)
        val formattedLimit = threshold.label

        val openGalleryIntent = Intent(context, GalleryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pIntent = PendingIntent.getActivity(
            context,
            0,
            openGalleryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openSettingsIntent = Intent(context, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val settingsPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openSettingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle("STORAGE THRESHOLD EXCEEDED")
            .setContentText("Recordings take $formattedUsed (Limit: $formattedLimit). Clear cache or delete old files.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("App recordings are currently taking $formattedUsed of storage space, which exceeds your threshold limit of $formattedLimit. Consider clearing temporary cache, uploading files to Catbox, or deleting old recordings.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pIntent)
            .addAction(R.drawable.ic_check_neo, "MANAGE VIDEOS", pIntent)
            .addAction(R.drawable.ic_record, "STORAGE SETTINGS", settingsPendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }
}
