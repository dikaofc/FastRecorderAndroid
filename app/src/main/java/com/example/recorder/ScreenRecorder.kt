// @dikaacode
package com.example.recorder

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class ScreenRecorder(
    private val width: Int,
    private val height: Int,
    private val density: Int,
    private val fps: Int,
    private val audioMode: AudioMode,
    private val videoEncoderType: VideoEncoderType,
    private val bitrateType: BitrateType,
    private val mediaProjection: MediaProjection,
    private val outputFileDescriptor: FileDescriptor,
    private val context: Context
) {
    companion object {
        private const val TAG = "ScreenRecorder"
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_CHANNEL_COUNT = 2
        private const val AUDIO_BIT_RATE = 128000
        private const val TIMEOUT_US = 10000L
        private const val I_FRAME_INTERVAL = 1
    }

    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var internalAudioRecord: AudioRecord? = null
    private var micAudioRecord: AudioRecord? = null

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private val muxerLock = Any()

    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var pauseStartTime = 0L
    private var totalPauseDurationUs = 0L

    private var videoDrainThread: Thread? = null
    private var audioRecordThread: Thread? = null
    private var audioDrainThread: Thread? = null

    private val pendingVideoBuffers = mutableListOf<PendingSample>()
    private val pendingAudioBuffers = mutableListOf<PendingSample>()

    private data class PendingSample(
        val isVideo: Boolean,
        val buffer: ByteBuffer,
        val bufferInfo: MediaCodec.BufferInfo
    )

    private var projectionCallback: MediaProjection.Callback? = null

    fun start() {
        try {
            // Register MediaProjection.Callback before creating any VirtualDisplay (Mandatory on Android 14+ / API 34 and best practice on API 29+)
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.d(TAG, "MediaProjection stopped callback triggered")
                    stop()
                }
            }
            projectionCallback = callback
            mediaProjection.registerCallback(callback, Handler(Looper.getMainLooper()))

            initMuxer()
            initVideoEncoder()
            if (audioMode != AudioMode.NONE) {
                initAudioEncoder()
                initAudioRecord()
            }

            isRecording.set(true)
            isPaused.set(false)
            pauseStartTime = 0L
            totalPauseDurationUs = 0L

            // Start drain threads
            startVideoDrainThread()
            if (audioMode != AudioMode.NONE) {
                startAudioRecordThread()
                startAudioDrainThread()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ScreenRecorder", e)
            com.example.service.RecordingState.setError("Failed to start ScreenRecorder: ${e.message}")
            release()
            throw e
        }
    }

    private fun initMuxer() {
        muxer = MediaMuxer(outputFileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxerStarted = false
        videoTrackIndex = -1
        audioTrackIndex = -1
    }

    private fun initVideoEncoder() {
        val mime = when (videoEncoderType) {
            VideoEncoderType.HEVC -> MediaFormat.MIMETYPE_VIDEO_HEVC
            else -> MediaFormat.MIMETYPE_VIDEO_AVC
        }

        val videoFormat = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateType.value)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            // Ensure continuous frame emission even when screen is static
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1_000_000L / fps)
        }

        videoEncoder = MediaCodec.createEncoderByType(mime).apply {
            configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = createInputSurface()
            start()

            val virtualDisplayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "FastScreenRecorder",
                width,
                height,
                density,
                virtualDisplayFlags,
                surface,
                null,
                null
            )
        }
    }

    private fun initAudioEncoder() {
        val audioFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            AUDIO_SAMPLE_RATE,
            AUDIO_CHANNEL_COUNT
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768)
        }

        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
    }

    private fun initAudioRecord() {
        val channelConfig = AudioFormat.CHANNEL_IN_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, channelConfig, audioFormat)
        val bufferSize = (minBufSize * 2).coerceAtLeast(8192)

        var internalSuccess = false

        if (audioMode == AudioMode.INTERNAL || audioMode == AudioMode.BOTH) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build()

                    val record = AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(captureConfig)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(audioFormat)
                                .setSampleRate(AUDIO_SAMPLE_RATE)
                                .setChannelMask(channelConfig)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .build()

                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        internalAudioRecord = record
                        internalSuccess = true
                        Log.d(TAG, "AudioPlaybackCapture AudioRecord initialized successfully")
                    } else {
                        record.release()
                        Log.w(TAG, "AudioPlaybackCapture AudioRecord failed to initialize (uninitialized state)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize AudioPlaybackCapture AudioRecord", e)
                }
            } else {
                Log.w(TAG, "AudioPlaybackCapture is only available on Android 10 (API 29)+")
            }

            // Fallback for internal audio capture failure
            if (!internalSuccess && audioMode == AudioMode.INTERNAL) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "System internal audio capture unavailable on this stream/device. Falling back to microphone audio.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // Initialize mic if requested or as fallback
        if (audioMode == AudioMode.MIC || audioMode == AudioMode.BOTH || (audioMode == AudioMode.INTERNAL && !internalSuccess)) {
            try {
                @Suppress("DEPRECATION")
                val mic = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AUDIO_SAMPLE_RATE,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                if (mic.state == AudioRecord.STATE_INITIALIZED) {
                    micAudioRecord = mic
                    Log.d(TAG, "Microphone AudioRecord initialized successfully")
                } else {
                    mic.release()
                    Log.w(TAG, "Microphone AudioRecord failed to initialize")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Microphone AudioRecord", e)
            }
        }
    }

    private fun startVideoDrainThread() {
        videoDrainThread = Thread({
            val bufferInfo = MediaCodec.BufferInfo()
            val encoder = videoEncoder ?: return@Thread

            while (isRecording.get()) {
                try {
                    val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when (outputBufferIndex) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No output buffer available yet
                        }
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            synchronized(muxerLock) {
                                if (!muxerStarted) {
                                    val newFormat = encoder.outputFormat
                                    videoTrackIndex = muxer?.addTrack(newFormat) ?: -1
                                    Log.d(TAG, "Video format changed: $newFormat, track: $videoTrackIndex")
                                    checkAndStartMuxer()
                                }
                            }
                        }
                        else -> {
                            if (outputBufferIndex >= 0) {
                                val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                                if (outputBuffer != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                    adjustPresentationTime(bufferInfo)
                                    if (!isPaused.get()) {
                                        writeSampleData(true, outputBuffer, bufferInfo)
                                    }
                                }
                                encoder.releaseOutputBuffer(outputBufferIndex, false)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in video drain loop", e)
                    break
                }
            }
            drainRemainingVideo()
        }, "VideoDrainThread").apply { start() }
    }

    private fun startAudioRecordThread() {
        audioRecordThread = Thread({
            try {
                internalAudioRecord?.startRecording()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting internalAudioRecord", e)
            }
            try {
                micAudioRecord?.startRecording()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting micAudioRecord", e)
            }

            val bufferSize = 4096
            val internalBuffer = ShortArray(bufferSize / 2)
            val micBuffer = ShortArray(bufferSize / 2)
            val mixedBuffer = ShortArray(bufferSize / 2)
            val byteBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.LITTLE_ENDIAN)

            var totalShortsWritten = 0L

            while (isRecording.get()) {
                try {
                    if (isPaused.get()) {
                        SystemClock.sleep(20)
                        continue
                    }

                    var shortsRead = 0
                    val hasInternal = internalAudioRecord != null && internalAudioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
                    val hasMic = micAudioRecord != null && micAudioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

                    if (hasInternal && hasMic) {
                        val readInt = internalAudioRecord?.read(internalBuffer, 0, internalBuffer.size) ?: 0
                        val readMic = micAudioRecord?.read(micBuffer, 0, micBuffer.size) ?: 0
                        val actualReadInt = if (readInt > 0) readInt else 0
                        val actualReadMic = if (readMic > 0) readMic else 0
                        shortsRead = maxOf(actualReadInt, actualReadMic)

                        for (i in 0 until shortsRead) {
                            val sampleInt = if (i < actualReadInt) internalBuffer[i].toInt() else 0
                            val sampleMic = if (i < actualReadMic) micBuffer[i].toInt() else 0
                            val sum = sampleInt + sampleMic
                            mixedBuffer[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                    } else if (hasInternal) {
                        val r = internalAudioRecord?.read(mixedBuffer, 0, mixedBuffer.size) ?: 0
                        shortsRead = if (r > 0) r else 0
                    } else if (hasMic) {
                        val r = micAudioRecord?.read(mixedBuffer, 0, mixedBuffer.size) ?: 0
                        shortsRead = if (r > 0) r else 0
                    }

                    // If no audio samples were read (silence or idle playback capture), generate silence so encoder is never starved
                    if (shortsRead <= 0) {
                        shortsRead = 1024
                        for (i in 0 until shortsRead) {
                            mixedBuffer[i] = 0
                        }
                        SystemClock.sleep(15)
                    }

                    if (shortsRead > 0 && isRecording.get()) {
                        byteBuffer.clear()
                        for (i in 0 until shortsRead) {
                            byteBuffer.putShort(mixedBuffer[i])
                        }
                        val bytesToEncode = shortsRead * 2

                        val encoder = audioEncoder ?: break
                        val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                            if (inputBuffer != null) {
                                inputBuffer.clear()
                                byteBuffer.flip()
                                inputBuffer.put(byteBuffer)

                                val ptsUs = (totalShortsWritten * 1_000_000L) / (AUDIO_SAMPLE_RATE * AUDIO_CHANNEL_COUNT)
                                totalShortsWritten += shortsRead

                                encoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    bytesToEncode,
                                    ptsUs,
                                    0
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in audio record loop", e)
                    break
                }
            }

            try {
                internalAudioRecord?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping internal audio", e)
            }
            try {
                micAudioRecord?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping mic audio", e)
            }
        }, "AudioRecordThread").apply { start() }
    }

    private fun startAudioDrainThread() {
        audioDrainThread = Thread({
            val bufferInfo = MediaCodec.BufferInfo()
            val encoder = audioEncoder ?: return@Thread

            while (isRecording.get()) {
                try {
                    val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when (outputBufferIndex) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No audio buffer yet
                        }
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            synchronized(muxerLock) {
                                if (!muxerStarted) {
                                    val newFormat = encoder.outputFormat
                                    audioTrackIndex = muxer?.addTrack(newFormat) ?: -1
                                    Log.d(TAG, "Audio format changed: $newFormat, track: $audioTrackIndex")
                                    checkAndStartMuxer()
                                }
                            }
                        }
                        else -> {
                            if (outputBufferIndex >= 0) {
                                val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                                if (outputBuffer != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                    adjustPresentationTime(bufferInfo)
                                    if (!isPaused.get()) {
                                        writeSampleData(false, outputBuffer, bufferInfo)
                                    }
                                }
                                encoder.releaseOutputBuffer(outputBufferIndex, false)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in audio drain loop", e)
                    break
                }
            }
            drainRemainingAudio()
        }, "AudioDrainThread").apply { start() }
    }

    private fun checkAndStartMuxer() {
        synchronized(muxerLock) {
            if (muxerStarted) return

            val videoReady = videoTrackIndex >= 0
            val audioReady = (audioMode == AudioMode.NONE) || (audioTrackIndex >= 0)

            if (videoReady && audioReady) {
                try {
                    muxer?.start()
                    muxerStarted = true
                    Log.d(TAG, "MediaMuxer started successfully")

                    // Flush pending video buffers
                    for (pending in pendingVideoBuffers) {
                        val track = if (pending.isVideo) videoTrackIndex else audioTrackIndex
                        if (track >= 0) {
                            muxer?.writeSampleData(track, pending.buffer, pending.bufferInfo)
                        }
                    }
                    pendingVideoBuffers.clear()

                    // Flush pending audio buffers
                    for (pending in pendingAudioBuffers) {
                        val track = if (pending.isVideo) videoTrackIndex else audioTrackIndex
                        if (track >= 0) {
                            muxer?.writeSampleData(track, pending.buffer, pending.bufferInfo)
                        }
                    }
                    pendingAudioBuffers.clear()

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start MediaMuxer", e)
                }
            }
        }
    }

    private fun writeSampleData(isVideo: Boolean, buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        synchronized(muxerLock) {
            if (muxerStarted) {
                val track = if (isVideo) videoTrackIndex else audioTrackIndex
                if (track >= 0) {
                    try {
                        muxer?.writeSampleData(track, buffer, bufferInfo)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing sample data to muxer", e)
                    }
                }
            } else {
                // Buffer samples until muxer starts
                val copy = ByteBuffer.allocateDirect(bufferInfo.size)
                buffer.position(bufferInfo.offset)
                buffer.limit(bufferInfo.offset + bufferInfo.size)
                copy.put(buffer)
                copy.flip()

                val infoCopy = MediaCodec.BufferInfo().apply {
                    set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                }

                if (isVideo) {
                    if (pendingVideoBuffers.size < 200) {
                        pendingVideoBuffers.add(PendingSample(true, copy, infoCopy))
                    }
                } else {
                    if (pendingAudioBuffers.size < 200) {
                        pendingAudioBuffers.add(PendingSample(false, copy, infoCopy))
                    }
                }
            }
        }
    }

    private fun adjustPresentationTime(bufferInfo: MediaCodec.BufferInfo) {
        if (totalPauseDurationUs > 0) {
            bufferInfo.presentationTimeUs = (bufferInfo.presentationTimeUs - totalPauseDurationUs).coerceAtLeast(0L)
        }
    }

    fun pause() {
        if (isRecording.get() && !isPaused.get()) {
            isPaused.set(true)
            pauseStartTime = SystemClock.elapsedRealtimeNanos() / 1000L
            Log.d(TAG, "Screen recording paused")
        }
    }

    fun resume() {
        if (isRecording.get() && isPaused.get()) {
            val resumeTime = SystemClock.elapsedRealtimeNanos() / 1000L
            if (pauseStartTime > 0) {
                totalPauseDurationUs += (resumeTime - pauseStartTime)
            }
            pauseStartTime = 0L
            isPaused.set(false)
            Log.d(TAG, "Screen recording resumed, total pause: ${totalPauseDurationUs}us")
        }
    }

    fun stop() {
        if (!isRecording.getAndSet(false)) {
            return
        }
        Log.d(TAG, "Stopping ScreenRecorder...")

        try {
            videoEncoder?.signalEndOfInputStream()
        } catch (e: Exception) {
            Log.e(TAG, "Error signaling end of video input stream", e)
        }

        try {
            videoDrainThread?.join(1500)
            audioRecordThread?.join(1000)
            audioDrainThread?.join(1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error joining threads", e)
        }

        release()
        Log.d(TAG, "ScreenRecorder stopped and released successfully")
    }

    private fun drainRemainingVideo() {
        try {
            val encoder = videoEncoder ?: return
            val bufferInfo = MediaCodec.BufferInfo()
            var count = 0
            while (count < 10) {
                val index = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (index >= 0) {
                    val buffer = encoder.getOutputBuffer(index)
                    if (buffer != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        adjustPresentationTime(bufferInfo)
                        writeSampleData(true, buffer, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(index, false)
                } else {
                    break
                }
                count++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error draining remaining video", e)
        }
    }

    private fun drainRemainingAudio() {
        try {
            val encoder = audioEncoder ?: return
            val bufferInfo = MediaCodec.BufferInfo()
            var count = 0
            while (count < 10) {
                val index = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (index >= 0) {
                    val buffer = encoder.getOutputBuffer(index)
                    if (buffer != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        adjustPresentationTime(bufferInfo)
                        writeSampleData(false, buffer, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(index, false)
                } else {
                    break
                }
                count++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error draining remaining audio", e)
        }
    }

    private fun release() {
        try {
            projectionCallback?.let {
                mediaProjection.unregisterCallback(it)
            }
            projectionCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering projection callback", e)
        }

        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing VirtualDisplay", e)
        }

        try {
            videoEncoder?.stop()
            videoEncoder?.release()
            videoEncoder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing VideoEncoder", e)
        }

        try {
            audioEncoder?.stop()
            audioEncoder?.release()
            audioEncoder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioEncoder", e)
        }

        try {
            internalAudioRecord?.release()
            internalAudioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing internal AudioRecord", e)
        }

        try {
            micAudioRecord?.release()
            micAudioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing mic AudioRecord", e)
        }

        synchronized(muxerLock) {
            if (muxerStarted) {
                try {
                    muxer?.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping MediaMuxer", e)
                }
            }
            try {
                muxer?.release()
                muxer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaMuxer", e)
            }
            muxerStarted = false
        }
    }
}
