// @dikaacode
package com.dikacode.service

import android.app.PendingIntent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.dikacode.R
import com.dikacode.recorder.AudioMode
import com.dikacode.recorder.BitrateType
import com.dikacode.recorder.CatboxUploader
import com.dikacode.recorder.ScreenRecorder
import com.dikacode.recorder.SettingsManager
import com.dikacode.recorder.TempFileCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RecordingForegroundService : Service() {

    private var screenRecorder: ScreenRecorder? = null
    private var mediaProjection: MediaProjection? = null
    private var outputFileDescriptor: ParcelFileDescriptor? = null
    private var outputUri: Uri? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var fileName: String = ""

    private var overlayManager: OverlayManager? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (data != null && resultCode != 0) {
                    startRecording(resultCode, data)
                }
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun isBatterySaverActive(settings: SettingsManager): Boolean {
        if (settings.batterySaverMode) return true
        try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                registerReceiver(null, ifilter)
            }
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                val batteryPct = (level * 100) / scale
                if (batteryPct <= 15) return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        try {
            val settings = SettingsManager(this)
            val isSaver = isBatterySaverActive(settings)
            
            val fps = if (isSaver) Math.min(settings.fps.value, 30) else settings.fps.value
            val maxDim = settings.resolution.maxDim
            var audioMode = settings.audioMode
            val videoEncoder = settings.videoEncoder
            val bitrate = if (isSaver) BitrateType.LOW else settings.bitrate

            if (audioMode != AudioMode.NONE) {
                val hasAudioPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!hasAudioPerm) {
                    audioMode = AudioMode.NONE
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            this@RecordingForegroundService,
                            "Audio permission not granted. Recording without audio.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            
            createNotificationChannel()
            val notification = buildRecordingNotification(isPaused = false, isSaver = isSaver)
        
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (audioMode == AudioMode.MIC || audioMode == AudioMode.BOTH || audioMode == AudioMode.INTERNAL)) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                // Android 14 (API 34) enforces strict foreground service type declaration
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            
            val scale = if (Math.max(metrics.widthPixels, metrics.heightPixels) > maxDim) {
                maxDim / Math.max(metrics.widthPixels, metrics.heightPixels)
            } else {
                1f
            }
            
            val rawWidth = (metrics.widthPixels * scale).toInt()
            val rawHeight = (metrics.heightPixels * scale).toInt()
            val width = (rawWidth / 16) * 16
            val height = (rawHeight / 16) * 16
            val density = metrics.densityDpi

            fileName = "REC_${System.currentTimeMillis()}.mp4"
            outputFileDescriptor = createOutputFile(fileName)
            val fd = outputFileDescriptor?.fileDescriptor
            if (fd == null) {
                stopRecording()
                return
            }
            
            screenRecorder = ScreenRecorder(width, height, density, fps, audioMode, videoEncoder, bitrate, mediaProjection!!, fd, this)
            screenRecorder?.start()
            
            RecordingState.setRecording(true)
            RecordingState.setPaused(false)
            RecordingState.setError(null)
            
            // In Battery Saver mode, suppress floating overlay to save power
            if (settings.showOverlay && !isSaver) {
                overlayManager = OverlayManager(this)
                overlayManager?.showOverlay()
            }

            startTimer()

            // Purge temporary files older than 24 hours in background
            serviceScope.launch(Dispatchers.IO) {
                TempFileCleaner.purgeOldTempFiles(this@RecordingForegroundService)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            RecordingState.setError("Service error: ${e.message}")
            stopRecording()
        }
    }
    
    private fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            screenRecorder?.pause()
            RecordingState.setPaused(true)
            updateRecordingNotification(isPaused = true)
        }
    }
    
    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            screenRecorder?.resume()
            RecordingState.setPaused(false)
            updateRecordingNotification(isPaused = false)
        }
    }

    private fun stopRecording() {
        overlayManager?.hideOverlay()
        screenRecorder?.stop()
        screenRecorder = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        outputFileDescriptor?.close()
        outputFileDescriptor = null
        
        RecordingState.setRecording(false)
        RecordingState.setPaused(false)
        stopTimer()
        RecordingState.setSavedMessage("Recording saved: $fileName")

        // Check storage threshold in background
        serviceScope.launch(Dispatchers.IO) {
            com.dikacode.recorder.StorageThresholdNotifier.checkAndNotifyIfExceeded(this@RecordingForegroundService)
        }
        
        val uri = outputUri
        val settings = SettingsManager(this)
        
        if (uri != null && settings.autoUploadCloud) {
            updateNotificationForUpload(0, 0L, 0L)
            serviceScope.launch {
                try {
                    val cloudUrl = CatboxUploader.uploadFile(
                        this@RecordingForegroundService,
                        uri
                    ) { percent, bytesSent, totalBytes ->
                        updateNotificationForUpload(percent, bytesSent, totalBytes)
                    }

                    if (cloudUrl != null) {
                        try {
                            val id = ContentUris.parseId(uri)
                            settings.setCloudUrl(id, cloudUrl)
                        } catch(e: Exception) {
                            settings.setCloudUrl(uri.hashCode().toLong(), cloudUrl)
                        }
                        RecordingState.setSavedMessage("Uploaded: $cloudUrl")
                        
                        Handler(Looper.getMainLooper()).post {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Catbox URL", cloudUrl)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(this@RecordingForegroundService, "Catbox Link Copied: $cloudUrl", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        RecordingState.setSavedMessage("Failed to upload to cloud")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    RecordingState.setSavedMessage("Failed to upload to cloud")
                } finally {
                    finishService()
                }
            }
        } else {
            finishService()
        }
    }

    private fun updateNotificationForUpload(percent: Int, bytesSent: Long, totalBytes: Long) {
        val sentMb = String.format("%.1f", bytesSent / (1024.0 * 1024.0))
        val totalMb = String.format("%.1f", totalBytes / (1024.0 * 1024.0))
        val text = if (totalBytes > 0) "Uploading to Catbox: $percent% ($sentMb/$totalMb MB)" else "Uploading to Catbox..."

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Uploading Recording")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_record)
            .setProgress(100, percent, totalBytes <= 0)
            .setOngoing(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun finishService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    private fun startTimer() {
        timerJob?.cancel()
        RecordingState.setDuration(0)
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (!RecordingState.isPaused.value) {
                    RecordingState.setDuration(RecordingState.durationSeconds.value + 1)
                }
            }
        }
    }
    
    private fun stopTimer() {
        timerJob?.cancel()
        RecordingState.setDuration(0)
    }

    private fun createOutputFile(name: String): ParcelFileDescriptor? {
        val settings = SettingsManager(this)
        val uriStr = settings.storageUriString
        val resolver = contentResolver
        if (uriStr != null) {
            try {
                val treeUri = Uri.parse(uriStr)
                val dir = DocumentFile.fromTreeUri(this, treeUri)
                if (dir != null && dir.canWrite()) {
                    val file = dir.createFile("video/mp4", name)
                    if (file != null) {
                        outputUri = file.uri
                        return resolver.openFileDescriptor(file.uri, "w")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Fallback to MediaStore
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FastRecorder")
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        outputUri = uri
        return uri?.let { resolver.openFileDescriptor(it, "w") }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildRecordingNotification(isPaused: Boolean, isSaver: Boolean = false): Notification {
        val title = when {
            isPaused -> "Recording Paused"
            isSaver -> "Recording Screen (Battery Saver)"
            else -> "Recording Screen"
        }
        val text = if (isPaused) "Paused — tap Resume to continue" else "Tap Pause / Stop from notification"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_record)
            .setOngoing(true)

        // Pause / Resume action
        val toggleIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePending = PendingIntent.getService(
            this, if (isPaused) 2 else 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            NotificationCompat.Action(
                0,
                if (isPaused) "Resume" else "Pause",
                togglePending
            )
        )
        // Stop action
        val stopIntent = Intent(this, RecordingForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(NotificationCompat.Action(0, "Stop", stopPending))

        return builder.build()
    }

    private fun updateRecordingNotification(isPaused: Boolean) {
        try {
            val settings = SettingsManager(this)
            val isSaver = isBatterySaverActive(settings)
            val n = buildRecordingNotification(isPaused, isSaver)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, n)
        } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1
    }
}
