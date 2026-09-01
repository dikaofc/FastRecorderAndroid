// @dikaacode
package com.dikacode

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dikacode.databinding.ActivityGalleryBinding
import com.dikacode.recorder.CatboxUploader
import com.dikacode.recorder.SettingsManager
import com.dikacode.recorder.StorageThreshold
import com.dikacode.recorder.StorageThresholdNotifier
import com.dikacode.recorder.StorageUtils
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

data class VideoItem(val name: String, val size: Long, val uri: Uri)

data class UploadResultItem(val name: String, val uri: Uri, val url: String)

data class VideoTechnicalInfo(
    val name: String,
    val path: String,
    val sizeFormatted: String,
    val durationFormatted: String,
    val resolution: String,
    val bitrateFormatted: String,
    val mimeType: String,
    val dateModified: String
)

/**
 * High-performance background thumbnail loader with LRU caching.
 */
object ThumbnailManager {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val thumbnailExecutor = Executors.newFixedThreadPool(4)
    val thumbnailDispatcher = thumbnailExecutor.asCoroutineDispatcher()

    fun getCachedBitmap(key: String): Bitmap? = memoryCache.get(key)

    fun putBitmap(key: String, bitmap: Bitmap) {
        if (getCachedBitmap(key) == null) {
            memoryCache.put(key, bitmap)
        }
    }

    fun loadThumbnail(context: Context, uri: Uri, width: Int = 300, height: Int = 300): Bitmap? {
        val cacheKey = uri.toString()
        getCachedBitmap(cacheKey)?.let { return it }

        var bitmap: Bitmap? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    bitmap = context.contentResolver.loadThumbnail(uri, android.util.Size(width, height), null)
                } catch (e: Exception) {
                    // Fallback to MediaMetadataRetriever
                }
            }

            if (bitmap == null) {
                val retriever = MediaMetadataRetriever()
                try {
                    if (uri.scheme == "content" || uri.scheme == "file") {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            retriever.setDataSource(pfd.fileDescriptor)
                        } ?: retriever.setDataSource(context, uri)
                    } else {
                        retriever.setDataSource(context, uri)
                    }

                    bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            0,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            width,
                            height
                        )
                    } else {
                        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }
                } finally {
                    try { retriever.release() } catch (e: Exception) {}
                }
            }

            if (bitmap != null) {
                putBitmap(cacheKey, bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bitmap
    }
}

class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private val videos = mutableListOf<VideoItem>()
    private val selectedUris = mutableSetOf<Uri>()
    private var isSelectionMode = false

    private lateinit var adapter: GalleryAdapter
    private lateinit var settingsManager: SettingsManager
    private var uploadJob: Job? = null

    // Multiple file picker launcher for choosing any videos from device storage
    private val pickStorageVideosLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            val pickedVideos = uris.map { uri ->
                var name = "Video_${System.currentTimeMillis()}.mp4"
                var size = 0L
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                            if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                VideoItem(name, size, uri)
            }
            startBatchUpload(pickedVideos)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        applyThemeUI(settingsManager.darkMode)

        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        setupClickListeners()
        loadVideos()
    }

    override fun onResume() {
        super.onResume()
        applyThemeUI(settingsManager.darkMode)
        loadVideos()
    }

    private fun setupRecyclerView() {
        adapter = GalleryAdapter(
            items = videos,
            selectedUris = selectedUris,
            isSelectionMode = { isSelectionMode },
            isDark = settingsManager.darkMode,
            onItemClick = { item ->
                if (isSelectionMode) {
                    toggleVideoSelection(item)
                } else {
                    showVideoActionDialog(item)
                }
            },
            onPlay = this::playVideo,
            onInfo = this::showVideoInfoDialog,
            onUploadSingle = { item -> startBatchUpload(listOf(item)) },
            onDelete = { item -> showConfirmDeleteDialog(listOf(item)) },
            onToggleSelect = this::toggleVideoSelection,
            onLongClick = { item ->
                if (isSelectionMode) {
                    toggleVideoSelection(item)
                } else {
                    showRenameDialog(item)
                }
            }
        )

        binding.rvGallery.layoutManager = GridLayoutManager(this, 2)
        binding.rvGallery.adapter = adapter
        // Disabled: lint error when using wrap_content in scrolling direction
        // binding.rvGallery.setHasFixedSize(true)
        binding.rvGallery.setItemViewCacheSize(20)
    }

    private fun setupClickListeners() {
        // Prominent Upload / Selection Button in top bar
        binding.btnUploadToCatbox.setOnClickListener {
            if (videos.isNotEmpty()) {
                enterSelectionMode()
            } else {
                pickStorageVideosLauncher.launch("video/*")
            }
        }

        // Storage Alert Button
        binding.btnManageStorage.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Selection header actions
        binding.btnCloseSelection.setOnClickListener {
            exitSelectionMode()
        }

        binding.btnSelectAll.setOnClickListener {
            if (selectedUris.size == videos.size) {
                selectedUris.clear()
            } else {
                selectedUris.clear()
                selectedUris.addAll(videos.map { it.uri })
            }
            updateSelectionUI()
            adapter.notifyDataSetChanged()
        }

        binding.btnPickStorageVideos.setOnClickListener {
            pickStorageVideosLauncher.launch("video/*")
        }

        binding.btnPickFromStorageEmpty.setOnClickListener {
            pickStorageVideosLauncher.launch("video/*")
        }

        // Bottom batch action buttons
        binding.btnStartBatchUpload.setOnClickListener {
            val selectedItems = videos.filter { selectedUris.contains(it.uri) }
            if (selectedItems.isNotEmpty()) {
                startBatchUpload(selectedItems)
            } else {
                Toast.makeText(this, "Please select at least one video to upload", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBatchDelete.setOnClickListener {
            val selectedItems = videos.filter { selectedUris.contains(it.uri) }
            if (selectedItems.isNotEmpty()) {
                showConfirmDeleteDialog(selectedItems)
            } else {
                Toast.makeText(this, "Please select at least one video to delete", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCancelBatch.setOnClickListener {
            exitSelectionMode()
        }
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        selectedUris.clear()
        binding.layoutNormalHeader.visibility = View.GONE
        binding.layoutSelectionHeader.visibility = View.VISIBLE
        binding.layoutBottomBatchBar.visibility = View.VISIBLE
        updateSelectionUI()
        adapter.notifyDataSetChanged()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedUris.clear()
        binding.layoutSelectionHeader.visibility = View.GONE
        binding.layoutBottomBatchBar.visibility = View.GONE
        binding.layoutNormalHeader.visibility = View.VISIBLE
        adapter.notifyDataSetChanged()
    }

    private fun toggleVideoSelection(item: VideoItem) {
        if (selectedUris.contains(item.uri)) {
            selectedUris.remove(item.uri)
        } else {
            selectedUris.add(item.uri)
        }
        updateSelectionUI()
        adapter.notifyDataSetChanged()
    }

    private fun updateSelectionUI() {
        val count = selectedUris.size
        binding.tvSelectionCount.text = "$count SELECTED"
        binding.tvBatchUploadText.text = "UPLOAD ($count)"
        binding.tvBatchDeleteText.text = "DELETE ($count)"
        binding.btnSelectAll.text = if (count > 0 && count == videos.size) "DESELECT ALL" else "SELECT ALL"
        
        val hasSelection = count > 0
        binding.btnStartBatchUpload.alpha = if (hasSelection) 1.0f else 0.5f
        binding.btnStartBatchUpload.isEnabled = hasSelection
        binding.btnBatchDelete.alpha = if (hasSelection) 1.0f else 0.5f
        binding.btnBatchDelete.isEnabled = hasSelection
    }

    private fun applyThemeUI(isDark: Boolean) {
        if (isDark) {
            window.statusBarColor = Color.parseColor("#121212")
            binding.galleryRoot.setBackgroundColor(Color.parseColor("#121212"))
            binding.btnBack.setBackgroundResource(R.drawable.bg_neo_button_dark)
            binding.btnBack.setColorFilter(Color.parseColor("#FFFFFF"))
            binding.btnCloseSelection.setBackgroundResource(R.drawable.bg_neo_button_dark)
            binding.btnCloseSelection.setColorFilter(Color.parseColor("#FFFFFF"))
            binding.btnSelectAll.setBackgroundResource(R.drawable.bg_neo_button_dark)
            binding.btnSelectAll.setTextColor(Color.parseColor("#FFFFFF"))
            binding.btnPickStorageVideos.setBackgroundResource(R.drawable.bg_neo_button_dark)
            binding.btnPickStorageVideos.setTextColor(Color.parseColor("#FFFFFF"))
            binding.btnCancelBatch.setBackgroundResource(R.drawable.bg_neo_button_dark)
            binding.btnCancelBatch.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvTitleGallery.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvSelectionCount.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvEmptyTitle.setTextColor(Color.parseColor("#FFFFFF"))
            binding.tvEmptyDesc.setTextColor(Color.parseColor("#AAAAAA"))
            binding.bannerStorageWarning.setBackgroundResource(R.drawable.bg_neo_card_dark)
            binding.btnManageStorage.setBackgroundResource(R.drawable.bg_neo_button_dark)
            binding.btnManageStorage.setTextColor(Color.parseColor("#FFFFFF"))
        } else {
            window.statusBarColor = ContextCompat.getColor(this, R.color.neo_yellow)
            binding.galleryRoot.setBackgroundColor(ContextCompat.getColor(this, R.color.neo_yellow))
            binding.btnBack.setBackgroundResource(R.drawable.bg_neo_button)
            binding.btnBack.setColorFilter(Color.parseColor("#0A0A0A"))
            binding.btnCloseSelection.setBackgroundResource(R.drawable.bg_neo_button)
            binding.btnCloseSelection.setColorFilter(Color.parseColor("#0A0A0A"))
            binding.btnSelectAll.setBackgroundResource(R.drawable.bg_neo_button)
            binding.btnSelectAll.setTextColor(Color.parseColor("#0A0A0A"))
            binding.btnPickStorageVideos.setBackgroundResource(R.drawable.bg_neo_button)
            binding.btnPickStorageVideos.setTextColor(Color.parseColor("#0A0A0A"))
            binding.btnCancelBatch.setBackgroundResource(R.drawable.bg_neo_button)
            binding.btnCancelBatch.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvTitleGallery.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvSelectionCount.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvEmptyTitle.setTextColor(Color.parseColor("#0A0A0A"))
            binding.tvEmptyDesc.setTextColor(Color.parseColor("#555555"))
            binding.bannerStorageWarning.setBackgroundResource(R.drawable.bg_neo_card)
            binding.btnManageStorage.setBackgroundResource(R.drawable.bg_neo_button)
            binding.btnManageStorage.setTextColor(Color.parseColor("#0A0A0A"))
        }
    }

    private fun loadVideos() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                val result = mutableListOf<VideoItem>()
                val uriStr = settingsManager.storageUriString
                
                if (uriStr != null) {
                    try {
                        val treeUri = Uri.parse(uriStr)
                        val dir = DocumentFile.fromTreeUri(this@GalleryActivity, treeUri)
                        dir?.listFiles()?.forEach { file ->
                            if (file.type?.startsWith("video/") == true || file.name?.endsWith(".mp4") == true) {
                                result.add(VideoItem(file.name ?: "Unknown", file.length(), file.uri))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE)
                    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
                    
                    contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                        
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            val name = cursor.getString(nameCol)
                            val size = cursor.getLong(sizeCol)
                            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                            if (name.startsWith("REC_") || name.contains("video") || name.endsWith(".mp4")) {
                                result.add(VideoItem(name, size, uri))
                            }
                        }
                    }
                }
                result
            }

            videos.clear()
            videos.addAll(list.sortedByDescending { it.name })
            adapter.notifyDataSetChanged()
            
            if (videos.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvGallery.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.rvGallery.visibility = View.VISIBLE
            }

            checkStorageThresholdBanner()
        }
    }

    private fun checkStorageThresholdBanner() {
        val threshold = settingsManager.storageThreshold
        if (threshold == StorageThreshold.DISABLED || threshold.bytes <= 0) {
            binding.bannerStorageWarning.visibility = View.GONE
            return
        }

        val totalBytes = videos.sumOf { it.size }
        if (totalBytes >= threshold.bytes) {
            binding.bannerStorageWarning.visibility = View.VISIBLE
            val usedFormatted = StorageUtils.formatBytes(totalBytes)
            val limitFormatted = threshold.label
            binding.tvStorageWarnDesc.text = "Recordings: $usedFormatted / Limit: $limitFormatted"
        } else {
            binding.bannerStorageWarning.visibility = View.GONE
        }
    }

    private fun playVideo(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No video player available", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Alert notification action dialog when a video item in gallery is clicked.
     */
    private fun showVideoActionDialog(item: VideoItem) {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_video_action)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
        }

        val isDark = settingsManager.darkMode
        val dialogActionRoot = dialog.findViewById<View>(R.id.dialogActionRoot)
        val ivActionThumbnail = dialog.findViewById<ImageView>(R.id.ivActionThumbnail)
        val tvActionFileName = dialog.findViewById<TextView>(R.id.tvActionFileName)
        val tvActionFileSize = dialog.findViewById<TextView>(R.id.tvActionFileSize)
        val btnActionPlay = dialog.findViewById<LinearLayout>(R.id.btnActionPlay)
        val tvActionPlay = dialog.findViewById<TextView>(R.id.tvActionPlay)
        val btnActionInfo = dialog.findViewById<LinearLayout>(R.id.btnActionInfo)
        val ivActionInfoIcon = dialog.findViewById<ImageView>(R.id.ivActionInfoIcon)
        val tvActionInfo = dialog.findViewById<TextView>(R.id.tvActionInfo)
        val btnActionRename = dialog.findViewById<LinearLayout>(R.id.btnActionRename)
        val ivActionRenameIcon = dialog.findViewById<ImageView>(R.id.ivActionRenameIcon)
        val tvActionRename = dialog.findViewById<TextView>(R.id.tvActionRename)
        val btnActionUpload = dialog.findViewById<LinearLayout>(R.id.btnActionUpload)
        val ivActionUploadIcon = dialog.findViewById<ImageView>(R.id.ivActionUploadIcon)
        val tvActionUpload = dialog.findViewById<TextView>(R.id.tvActionUpload)
        val btnActionDelete = dialog.findViewById<LinearLayout>(R.id.btnActionDelete)
        val btnActionCancel = dialog.findViewById<TextView>(R.id.btnActionCancel)

        tvActionFileName.text = item.name
        tvActionFileSize.text = StorageUtils.formatBytes(item.size)

        // Load thumbnail in action dialog
        val cached = ThumbnailManager.getCachedBitmap(item.uri.toString())
        if (cached != null) {
            ivActionThumbnail.setImageBitmap(cached)
        } else {
            CoroutineScope(ThumbnailManager.thumbnailDispatcher).launch {
                val bmp = ThumbnailManager.loadThumbnail(this@GalleryActivity, item.uri)
                if (bmp != null) {
                    withContext(Dispatchers.Main) {
                        ivActionThumbnail.setImageBitmap(bmp)
                    }
                }
            }
        }

        if (isDark) {
            val white = Color.parseColor("#FFFFFF")
            val subtext = Color.parseColor("#AAAAAA")

            dialogActionRoot.setBackgroundResource(R.drawable.bg_neo_card_dark)
            tvActionFileName.setTextColor(white)
            tvActionFileSize.setTextColor(subtext)

            btnActionInfo.setBackgroundResource(R.drawable.bg_neo_button_dark)
            tvActionInfo.setTextColor(white)
            ivActionInfoIcon.setColorFilter(white)

            btnActionRename.setBackgroundResource(R.drawable.bg_neo_button_dark)
            tvActionRename.setTextColor(white)
            ivActionRenameIcon.setColorFilter(white)

            btnActionUpload.setBackgroundResource(R.drawable.bg_neo_button_dark)
            tvActionUpload.setTextColor(white)
            ivActionUploadIcon.setColorFilter(white)

            btnActionCancel.setBackgroundResource(R.drawable.bg_neo_button_dark)
            btnActionCancel.setTextColor(white)
        }

        btnActionPlay.setOnClickListener {
            dialog.dismiss()
            playVideo(item.uri)
        }

        btnActionInfo.setOnClickListener {
            dialog.dismiss()
            showVideoInfoDialog(item)
        }

        btnActionRename.setOnClickListener {
            dialog.dismiss()
            showRenameDialog(item)
        }

        btnActionUpload.setOnClickListener {
            dialog.dismiss()
            startBatchUpload(listOf(item))
        }

        btnActionDelete.setOnClickListener {
            dialog.dismiss()
            showConfirmDeleteDialog(listOf(item))
        }

        btnActionCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Technical Info modal displaying duration, resolution, size, bitrate, format, and path.
     */
    private fun showVideoInfoDialog(item: VideoItem) {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_video_info)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.94).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
        }

        val isDark = settingsManager.darkMode
        val dialogInfoRoot = dialog.findViewById<View>(R.id.dialogInfoRoot)
        val ivInfoIcon = dialog.findViewById<ImageView>(R.id.ivInfoIcon)
        val tvInfoTitle = dialog.findViewById<TextView>(R.id.tvInfoTitle)
        val tvInfoFileName = dialog.findViewById<TextView>(R.id.tvInfoFileName)
        val infoContainer = dialog.findViewById<LinearLayout>(R.id.infoContainer)
        val tvInfoSize = dialog.findViewById<TextView>(R.id.tvInfoSize)
        val tvInfoDuration = dialog.findViewById<TextView>(R.id.tvInfoDuration)
        val tvInfoResolution = dialog.findViewById<TextView>(R.id.tvInfoResolution)
        val tvInfoBitrate = dialog.findViewById<TextView>(R.id.tvInfoBitrate)
        val tvInfoFps = dialog.findViewById<TextView>(R.id.tvInfoFps)
        val tvInfoCodec = dialog.findViewById<TextView>(R.id.tvInfoCodec)
        val tvInfoAudio = dialog.findViewById<TextView>(R.id.tvInfoAudio)
        val tvInfoDate = dialog.findViewById<TextView>(R.id.tvInfoDate)
        val tvInfoPath = dialog.findViewById<TextView>(R.id.tvInfoPath)
        val btnInfoPlay = dialog.findViewById<TextView>(R.id.btnInfoPlay)
        val btnInfoClose = dialog.findViewById<TextView>(R.id.btnInfoClose)

        // Labels for dark mode theme sync
        val lblInfoDuration = dialog.findViewById<TextView>(R.id.lblInfoDuration)
        val lblInfoSize = dialog.findViewById<TextView>(R.id.lblInfoSize)
        val lblInfoResolution = dialog.findViewById<TextView>(R.id.lblInfoResolution)
        val lblInfoBitrate = dialog.findViewById<TextView>(R.id.lblInfoBitrate)
        val lblInfoFps = dialog.findViewById<TextView>(R.id.lblInfoFps)
        val lblInfoCodec = dialog.findViewById<TextView>(R.id.lblInfoCodec)
        val lblInfoAudio = dialog.findViewById<TextView>(R.id.lblInfoAudio)
        val lblInfoDate = dialog.findViewById<TextView>(R.id.lblInfoDate)
        val lblInfoPath = dialog.findViewById<TextView>(R.id.lblInfoPath)

        if (isDark) {
            val white = Color.parseColor("#FFFFFF")
            val subtext = Color.parseColor("#AAAAAA")

            dialogInfoRoot.setBackgroundResource(R.drawable.bg_neo_card_dark)
            ivInfoIcon.setColorFilter(white)
            tvInfoTitle.setTextColor(white)
            tvInfoFileName.setTextColor(white)
            infoContainer.setBackgroundResource(R.drawable.bg_neo_spinner_dark)

            tvInfoSize.setTextColor(white)
            tvInfoDuration.setTextColor(white)
            tvInfoResolution.setTextColor(white)
            tvInfoBitrate.setTextColor(white)
            tvInfoFps.setTextColor(white)
            tvInfoCodec.setTextColor(white)
            tvInfoAudio.setTextColor(white)
            tvInfoDate.setTextColor(white)
            tvInfoPath.setTextColor(white)
            tvInfoPath.setBackgroundColor(Color.parseColor("#2A2A2A"))

            lblInfoDuration.setTextColor(subtext)
            lblInfoSize.setTextColor(subtext)
            lblInfoResolution.setTextColor(subtext)
            lblInfoBitrate.setTextColor(subtext)
            lblInfoFps.setTextColor(subtext)
            lblInfoCodec.setTextColor(subtext)
            lblInfoAudio.setTextColor(subtext)
            lblInfoDate.setTextColor(subtext)
            lblInfoPath.setTextColor(subtext)

            btnInfoClose.setBackgroundResource(R.drawable.bg_neo_button_dark)
            btnInfoClose.setTextColor(white)
        }

        tvInfoFileName.text = item.name
        tvInfoSize.text = StorageUtils.formatBytes(item.size)

        // Extract metadata in background
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { extractVideoInfo(item) }
            if (dialog.isShowing) {
                tvInfoFileName.text = info.name
                tvInfoSize.text = info.sizeFormatted
                tvInfoDuration.text = info.durationFormatted
                tvInfoResolution.text = info.resolution
                tvInfoBitrate.text = info.bitrateFormatted
                tvInfoCodec.text = info.mimeType
                tvInfoDate.text = info.dateModified
                tvInfoPath.text = info.path
            }
        }

        btnInfoPlay.setOnClickListener {
            dialog.dismiss()
            playVideo(item.uri)
        }

        btnInfoClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun extractVideoInfo(item: VideoItem): VideoTechnicalInfo {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 0
        var height = 0
        var bitrate = 0L
        var mime = "video/mp4"
        var date = "Recorded file"

        try {
            if (item.uri.scheme == "content" || item.uri.scheme == "file") {
                contentResolver.openFileDescriptor(item.uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                } ?: retriever.setDataSource(this, item.uri)
            } else {
                retriever.setDataSource(this, item.uri)
            }

            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                width = h
                height = w
            } else {
                width = w
                height = h
            }

            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"
            date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE) ?: "Recorded file"
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }

        val durationFormatted = formatDuration(durationMs)
        val sizeFormatted = StorageUtils.formatBytes(item.size)
        val resolutionFormatted = if (width > 0 && height > 0) "${width} x ${height} px" else "N/A"
        val bitrateFormatted = if (bitrate > 0) {
            val mbps = bitrate / 1_000_000.0
            if (mbps >= 1.0) String.format("%.2f Mbps", mbps) else String.format("%d kbps", bitrate / 1000)
        } else {
            "N/A"
        }

        val path = item.uri.path ?: item.uri.toString()

        return VideoTechnicalInfo(
            name = item.name,
            path = path,
            sizeFormatted = sizeFormatted,
            durationFormatted = durationFormatted,
            resolution = resolutionFormatted,
            bitrateFormatted = bitrateFormatted,
            mimeType = mime,
            dateModified = date
        )
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Rename file dialog supporting both Scoped Storage DocumentFile and MediaStore.
     */
    private fun showRenameDialog(item: VideoItem) {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_neo_rename)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
        }

        val isDark = settingsManager.darkMode
        val dialogRenameRoot = dialog.findViewById<View>(R.id.dialogRenameRoot)
        val ivRenameIcon = dialog.findViewById<ImageView>(R.id.ivRenameIcon)
        val tvRenameTitle = dialog.findViewById<TextView>(R.id.tvRenameTitle)
        val tvRenameDesc = dialog.findViewById<TextView>(R.id.tvRenameDesc)
        val etNewFileName = dialog.findViewById<EditText>(R.id.etNewFileName)
        val btnCancelRename = dialog.findViewById<TextView>(R.id.btnCancelRename)
        val btnSubmitRename = dialog.findViewById<TextView>(R.id.btnSubmitRename)

        val nameWithoutExt = if (item.name.endsWith(".mp4", ignoreCase = true)) {
            item.name.substring(0, item.name.length - 4)
        } else {
            item.name
        }

        etNewFileName.setText(nameWithoutExt)
        etNewFileName.selectAll()

        if (isDark) {
            val white = Color.parseColor("#FFFFFF")
            val subtext = Color.parseColor("#AAAAAA")

            dialogRenameRoot.setBackgroundResource(R.drawable.bg_neo_card_dark)
            ivRenameIcon.setColorFilter(white)
            tvRenameTitle.setTextColor(white)
            tvRenameDesc.setTextColor(subtext)
            etNewFileName.setBackgroundResource(R.drawable.bg_neo_input_dark)
            etNewFileName.setTextColor(white)
            etNewFileName.setHintTextColor(Color.parseColor("#888888"))
            btnCancelRename.setBackgroundResource(R.drawable.bg_neo_button_dark)
            btnCancelRename.setTextColor(white)
        }

        btnCancelRename.setOnClickListener {
            dialog.dismiss()
        }

        btnSubmitRename.setOnClickListener {
            val newRawName = etNewFileName.text.toString().trim()
            if (newRawName.isBlank()) {
                Toast.makeText(this, "Please enter a valid filename", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            performRename(item, newRawName)
        }

        dialog.show()
    }

    private fun performRename(item: VideoItem, newRawName: String) {
        val cleanName = if (newRawName.endsWith(".mp4", ignoreCase = true)) newRawName else "$newRawName.mp4"
        if (cleanName == item.name) return

        lifecycleScope.launch {
            val renamed = withContext(Dispatchers.IO) {
                var renamed = false
                val uriStr = settingsManager.storageUriString

                if (uriStr != null) {
                    try {
                        val treeUri = Uri.parse(uriStr)
                        val dir = DocumentFile.fromTreeUri(this@GalleryActivity, treeUri)
                        val file = dir?.findFile(item.name)
                        if (file != null && file.renameTo(cleanName)) {
                            renamed = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (!renamed) {
                    try {
                        val values = android.content.ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, cleanName)
                        }
                        val updatedRows = contentResolver.update(item.uri, values, null, null)
                        if (updatedRows > 0) {
                            renamed = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                renamed
            }

            if (renamed) {
                Toast.makeText(this@GalleryActivity, "Renamed to $cleanName", Toast.LENGTH_SHORT).show()
                loadVideos()
            } else {
                Toast.makeText(this@GalleryActivity, "Failed to rename file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Confirmation dialog before deleting single or batch videos.
     */
    private fun showConfirmDeleteDialog(itemsToDelete: List<VideoItem>) {
        if (itemsToDelete.isEmpty()) return

        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_neo_confirm_delete)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
        }

        val isDark = settingsManager.darkMode
        val dialogDeleteRoot = dialog.findViewById<View>(R.id.dialogDeleteRoot)
        val tvDeleteTitle = dialog.findViewById<TextView>(R.id.tvDeleteTitle)
        val tvDeleteMessage = dialog.findViewById<TextView>(R.id.tvDeleteMessage)
        val btnCancelDelete = dialog.findViewById<TextView>(R.id.btnCancelDelete)
        val btnConfirmDelete = dialog.findViewById<TextView>(R.id.btnConfirmDelete)

        val count = itemsToDelete.size
        if (count == 1) {
            tvDeleteMessage.text = "Are you sure you want to permanently delete \"${itemsToDelete.first().name}\" from storage?"
        } else {
            tvDeleteMessage.text = "Are you sure you want to permanently delete all $count selected video recordings?"
        }

        if (isDark) {
            dialogDeleteRoot.setBackgroundResource(R.drawable.bg_neo_card_dark)
            tvDeleteTitle.setTextColor(Color.parseColor("#FFFFFF"))
            tvDeleteMessage.setTextColor(Color.parseColor("#AAAAAA"))
            btnCancelDelete.setBackgroundResource(R.drawable.bg_neo_button_dark)
            btnCancelDelete.setTextColor(Color.parseColor("#FFFFFF"))
        }

        btnCancelDelete.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmDelete.setOnClickListener {
            dialog.dismiss()
            performDelete(itemsToDelete)
        }

        dialog.show()
    }

    private fun performDelete(itemsToDelete: List<VideoItem>) {
        lifecycleScope.launch {
            val deletedCount = withContext(Dispatchers.IO) {
                var count = 0
                val uriStr = settingsManager.storageUriString

                for (item in itemsToDelete) {
                    var deleted = false
                    if (uriStr != null) {
                        try {
                            val treeUri = Uri.parse(uriStr)
                            val dir = DocumentFile.fromTreeUri(this@GalleryActivity, treeUri)
                            val file = dir?.findFile(item.name)
                            if (file != null && file.delete()) {
                                deleted = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (!deleted) {
                        try {
                            val rows = contentResolver.delete(item.uri, null, null)
                            if (rows > 0) deleted = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (deleted) count++
                }
                count
            }

            Toast.makeText(this@GalleryActivity, "Deleted $deletedCount video(s)", Toast.LENGTH_SHORT).show()
            if (isSelectionMode) {
                exitSelectionMode()
            }
            loadVideos()
        }
    }

    /**
     * Executes batch or single upload to Catbox with live progress feedback,
     * and shows a dialog containing all generated links with copy buttons.
     */
    private fun startBatchUpload(itemsToUpload: List<VideoItem>) {
        if (itemsToUpload.isEmpty()) return

        val totalCount = itemsToUpload.size
        val results = mutableListOf<UploadResultItem>()

        val progressDialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_neo_upload_progress)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setCancelable(false)
        }

        val isDark = settingsManager.darkMode
        val dialogRoot = progressDialog.findViewById<View>(R.id.dialogRoot)
        val tvUploadTitle = progressDialog.findViewById<TextView>(R.id.tvUploadTitle)
        val tvProgressPercent = progressDialog.findViewById<TextView>(R.id.tvProgressPercent)
        val tvUploadFileName = progressDialog.findViewById<TextView>(R.id.tvUploadFileName)
        val progressContainer = progressDialog.findViewById<FrameLayout>(R.id.progressContainer)
        val progressFill = progressDialog.findViewById<View>(R.id.progressFill)
        val tvProgressBytes = progressDialog.findViewById<TextView>(R.id.tvProgressBytes)
        val btnCancelUpload = progressDialog.findViewById<TextView>(R.id.btnCancelUpload)

        if (isDark) {
            dialogRoot.setBackgroundResource(R.drawable.bg_neo_card_dark)
            tvUploadTitle.setTextColor(Color.parseColor("#FFFFFF"))
            tvProgressPercent.setTextColor(Color.parseColor("#FFFFFF"))
            tvUploadFileName.setTextColor(Color.parseColor("#AAAAAA"))
            tvProgressBytes.setTextColor(Color.parseColor("#FFFFFF"))
        }

        btnCancelUpload.setOnClickListener {
            uploadJob?.cancel()
            progressDialog.dismiss()
            Toast.makeText(this, "Upload cancelled", Toast.LENGTH_SHORT).show()
        }

        progressDialog.show()

        uploadJob = lifecycleScope.launch {
            for ((index, item) in itemsToUpload.withIndex()) {
                val currentFileIndex = index + 1
                val totalMb = item.size / (1024.0 * 1024.0)

                withContext(Dispatchers.Main) {
                    tvUploadTitle.text = if (totalCount > 1) {
                        "UPLOADING ($currentFileIndex of $totalCount)"
                    } else {
                        "UPLOADING TO CATBOX"
                    }
                    tvUploadFileName.text = item.name
                    tvProgressPercent.text = "0%"
                    tvProgressBytes.text = String.format("0.0 MB / %.1f MB", totalMb)

                    val params = progressFill.layoutParams
                    params.width = 0
                    progressFill.layoutParams = params
                }

                val url = CatboxUploader.uploadFile(this@GalleryActivity, item.uri) { percent, bytesSent, totalBytes ->
                    runOnUiThread {
                        tvProgressPercent.text = "$percent%"
                        val sentMb = bytesSent / (1024.0 * 1024.0)
                        val totMb = if (totalBytes > 0) totalBytes / (1024.0 * 1024.0) else totalMb
                        tvProgressBytes.text = String.format("%.1f MB / %.1f MB", sentMb, totMb)

                        val containerWidth = progressContainer.width
                        if (containerWidth > 0) {
                            val params = progressFill.layoutParams
                            params.width = ((containerWidth * percent) / 100)
                            progressFill.layoutParams = params
                        }
                    }
                }

                if (url != null) {
                    results.add(UploadResultItem(item.name, item.uri, url))
                    try {
                        val id = android.content.ContentUris.parseId(item.uri)
                        settingsManager.setCloudUrl(id, url)
                    } catch (e: Exception) {
                        settingsManager.setCloudUrl(item.uri.hashCode().toLong(), url)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (progressDialog.isShowing) {
                    progressDialog.dismiss()
                }

                if (isSelectionMode) {
                    exitSelectionMode()
                }

                if (results.isNotEmpty()) {
                    showUploadResultsDialog(results)
                } else {
                    Toast.makeText(this@GalleryActivity, "Upload failed for all selected items", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Displays a dialog showing all uploaded Catbox links with individual and batch copy buttons.
     */
    private fun showUploadResultsDialog(results: List<UploadResultItem>) {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_catbox_batch_results)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.94).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
        }

        val isDark = settingsManager.darkMode
        val dialogResultsRoot = dialog.findViewById<View>(R.id.dialogResultsRoot)
        val ivResultIcon = dialog.findViewById<ImageView>(R.id.ivResultIcon)
        val tvResultsTitle = dialog.findViewById<TextView>(R.id.tvResultsTitle)
        val tvResultsSubtitle = dialog.findViewById<TextView>(R.id.tvResultsSubtitle)
        val rvUploadResults = dialog.findViewById<RecyclerView>(R.id.rvUploadResults)
        val btnCopyAllLinks = dialog.findViewById<TextView>(R.id.btnCopyAllLinks)
        val btnCloseResults = dialog.findViewById<TextView>(R.id.btnCloseResults)

        if (isDark) {
            dialogResultsRoot.setBackgroundResource(R.drawable.bg_neo_card_dark)
            ivResultIcon.setColorFilter(Color.parseColor("#FFFFFF"))
            tvResultsTitle.setTextColor(Color.parseColor("#FFFFFF"))
            tvResultsSubtitle.setTextColor(Color.parseColor("#AAAAAA"))
            btnCloseResults.setBackgroundResource(R.drawable.bg_neo_button_dark)
            btnCloseResults.setTextColor(Color.parseColor("#FFFFFF"))
        }

        tvResultsSubtitle.text = "${results.size} video(s) uploaded successfully to Catbox cloud."

        val resultsAdapter = CatboxResultsAdapter(this, results, isDark) { link ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Catbox URL", link)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        rvUploadResults.layoutManager = LinearLayoutManager(this)
        rvUploadResults.adapter = resultsAdapter

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (results.size == 1) {
            val clip = ClipData.newPlainText("Catbox URL", results.first().url)
            clipboard.setPrimaryClip(clip)
            btnCopyAllLinks.text = "COPY LINK"
        } else {
            btnCopyAllLinks.text = "COPY ALL (${results.size}) LINKS"
        }

        btnCopyAllLinks.setOnClickListener {
            val textToCopy = if (results.size == 1) {
                results.first().url
            } else {
                results.joinToString("\n") { it.url }
            }
            val clip = ClipData.newPlainText("Catbox URLs", textToCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "All links copied to clipboard!", Toast.LENGTH_SHORT).show()
            btnCopyAllLinks.text = "COPIED TO CLIPBOARD!"
        }

        btnCloseResults.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}

/**
 * Adapter for displaying Catbox upload result links.
 */
class CatboxResultsAdapter(
    private val context: Context,
    private val items: List<UploadResultItem>,
    private val isDark: Boolean,
    private val onCopyLink: (String) -> Unit
) : RecyclerView.Adapter<CatboxResultsAdapter.ResultViewHolder>() {

    class ResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.itemResultRoot)
        val tvFileName: TextView = view.findViewById(R.id.tvResultFileName)
        val tvUrl: TextView = view.findViewById(R.id.tvResultUrl)
        val btnCopy: TextView = view.findViewById(R.id.btnCopySingleLink)
        val btnOpen: TextView = view.findViewById(R.id.btnOpenSingleLink)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_catbox_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val item = items[position]
        holder.tvFileName.text = item.name
        holder.tvUrl.text = item.url

        if (isDark) {
            holder.root.setBackgroundResource(R.drawable.bg_neo_spinner_dark)
            holder.tvFileName.setTextColor(Color.parseColor("#FFFFFF"))
            holder.tvUrl.setTextColor(Color.parseColor("#E0E0E0"))
            holder.tvUrl.setBackgroundColor(Color.parseColor("#2A2A2A"))
            holder.btnOpen.setBackgroundResource(R.drawable.bg_neo_button_dark)
            holder.btnOpen.setTextColor(Color.parseColor("#FFFFFF"))
        } else {
            holder.root.setBackgroundResource(R.drawable.bg_neo_spinner)
            holder.tvFileName.setTextColor(Color.parseColor("#0A0A0A"))
            holder.tvUrl.setTextColor(Color.parseColor("#333333"))
            holder.tvUrl.setBackgroundColor(Color.parseColor("#15000000"))
            holder.btnOpen.setBackgroundResource(R.drawable.bg_neo_button)
            holder.btnOpen.setTextColor(Color.parseColor("#0A0A0A"))
        }

        holder.btnCopy.setOnClickListener {
            onCopyLink(item.url)
            holder.btnCopy.text = "COPIED!"
        }

        holder.btnOpen.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount(): Int = items.size
}

/**
 * Gallery RecyclerView Adapter with batch selection, alert menu on click, info modal, rename and delete.
 */
class GalleryAdapter(
    private val items: List<VideoItem>,
    private val selectedUris: Set<Uri>,
    private val isSelectionMode: () -> Boolean,
    private val isDark: Boolean,
    private val onItemClick: (VideoItem) -> Unit,
    private val onPlay: (Uri) -> Unit,
    private val onInfo: (VideoItem) -> Unit,
    private val onUploadSingle: (VideoItem) -> Unit,
    private val onDelete: (VideoItem) -> Unit,
    private val onToggleSelect: (VideoItem) -> Unit,
    private val onLongClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rootLayout: View = view.findViewById(R.id.cardVideoRoot)
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        val tvSize: TextView = view.findViewById(R.id.tvSize)
        val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        val ivPlayCenter: ImageView = view.findViewById(R.id.ivPlayCenter)
        val layoutSelectionCheck: FrameLayout = view.findViewById(R.id.layoutSelectionCheck)
        val ivCheckMark: ImageView = view.findViewById(R.id.ivCheckMark)
        val layoutActions: LinearLayout = view.findViewById(R.id.layoutActions)
        val btnPlay: ImageView = view.findViewById(R.id.btnPlay)
        val btnInfo: ImageView = view.findViewById(R.id.btnInfo)
        val btnUpload: ImageView = view.findViewById(R.id.btnUpload)
        val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val inSelectionMode = isSelectionMode()
        val isSelected = selectedUris.contains(item.uri)

        holder.tvFileName.text = item.name
        val sizeMb = item.size / (1024.0 * 1024.0)
        holder.tvSize.text = String.format("%.2f MB", sizeMb)
        
        if (isDark) {
            holder.rootLayout.setBackgroundResource(
                if (isSelected) R.drawable.bg_neo_card else R.drawable.bg_neo_card_dark
            )
            holder.tvFileName.setTextColor(if (isSelected) Color.parseColor("#0A0A0A") else Color.parseColor("#FFFFFF"))
            holder.tvSize.setTextColor(if (isSelected) Color.parseColor("#444444") else Color.parseColor("#AAAAAA"))
            holder.btnPlay.setBackgroundResource(R.drawable.bg_neo_button_dark)
            holder.btnPlay.setColorFilter(Color.parseColor("#FFFFFF"))
            holder.btnInfo.setBackgroundResource(R.drawable.bg_neo_button_dark)
            holder.btnInfo.setColorFilter(Color.parseColor("#FFFFFF"))
            holder.btnUpload.setBackgroundResource(R.drawable.bg_neo_button_dark)
            holder.btnUpload.setColorFilter(Color.parseColor("#FFFFFF"))
            holder.btnDelete.setBackgroundResource(R.drawable.bg_neo_button_dark)
            holder.btnDelete.setColorFilter(Color.parseColor("#FF5252"))
        } else {
            holder.rootLayout.setBackgroundResource(R.drawable.bg_neo_card)
            holder.tvFileName.setTextColor(Color.parseColor("#0A0A0A"))
            holder.tvSize.setTextColor(Color.parseColor("#444444"))
            holder.btnPlay.setBackgroundResource(R.drawable.bg_neo_button)
            holder.btnPlay.setColorFilter(Color.parseColor("#0A0A0A"))
            holder.btnInfo.setBackgroundResource(R.drawable.bg_neo_button)
            holder.btnInfo.setColorFilter(Color.parseColor("#0A0A0A"))
            holder.btnUpload.setBackgroundResource(R.drawable.bg_neo_button)
            holder.btnUpload.setColorFilter(Color.parseColor("#0A0A0A"))
            holder.btnDelete.setBackgroundResource(R.drawable.bg_neo_button)
            holder.btnDelete.setColorFilter(Color.parseColor("#FF5252"))
        }

        // Handle Selection Mode vs Normal Mode UI
        if (inSelectionMode) {
            holder.layoutSelectionCheck.visibility = View.VISIBLE
            holder.ivPlayCenter.visibility = View.GONE
            holder.layoutActions.visibility = View.GONE

            if (isSelected) {
                holder.layoutSelectionCheck.setBackgroundResource(R.drawable.bg_neo_checkbox_checked)
                holder.ivCheckMark.visibility = View.VISIBLE
            } else {
                holder.layoutSelectionCheck.setBackgroundResource(R.drawable.bg_neo_checkbox_unchecked)
                holder.ivCheckMark.visibility = View.GONE
            }

            holder.rootLayout.setOnClickListener {
                onToggleSelect(item)
            }
        } else {
            holder.layoutSelectionCheck.visibility = View.GONE
            holder.ivPlayCenter.visibility = View.VISIBLE
            holder.layoutActions.visibility = View.VISIBLE

            // Clicking card opens alert action modal
            holder.rootLayout.setOnClickListener {
                onItemClick(item)
            }
        }

        // Long click triggers rename
        holder.rootLayout.setOnLongClickListener {
            onLongClick(item)
            true
        }

        holder.btnPlay.setOnClickListener { onPlay(item.uri) }
        holder.btnInfo.setOnClickListener { onInfo(item) }
        holder.btnUpload.setOnClickListener { onUploadSingle(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }

        // Fast memory cache check for thumbnail
        val cached = ThumbnailManager.getCachedBitmap(item.uri.toString())
        if (cached != null) {
            holder.ivThumbnail.setImageBitmap(cached)
        } else {
            holder.ivThumbnail.setImageDrawable(null)
            holder.ivThumbnail.tag = item.uri

            CoroutineScope(ThumbnailManager.thumbnailDispatcher).launch {
                val bitmap = ThumbnailManager.loadThumbnail(holder.itemView.context, item.uri)
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        if (holder.ivThumbnail.tag == item.uri) {
                            holder.ivThumbnail.setImageBitmap(bitmap)
                        }
                    }
                }
            }
        }
    }

    override fun getItemCount() = items.size
}
