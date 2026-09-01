import sys

content = """package com.example

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ActivityGalleryBinding
import com.example.recorder.CatboxUploader
import com.example.recorder.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VideoItem(val name: String, val size: Long, val uri: Uri)

class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private val videos = mutableListOf<VideoItem>()
    private lateinit var adapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = GalleryAdapter(videos, this::playVideo, this::uploadVideo, this::deleteVideo)
        binding.rvGallery.layoutManager = GridLayoutManager(this, 2)
        binding.rvGallery.adapter = adapter

        loadVideos()
    }

    private fun loadVideos() {
        CoroutineScope(Dispatchers.IO).launch {
            val list = mutableListOf<VideoItem>()
            val settings = SettingsManager(this@GalleryActivity)
            val uriStr = settings.storageUriString
            
            if (uriStr != null) {
                try {
                    val treeUri = Uri.parse(uriStr)
                    val dir = DocumentFile.fromTreeUri(this@GalleryActivity, treeUri)
                    dir?.listFiles()?.forEach { file ->
                        if (file.type?.startsWith("video/") == true || file.name?.endsWith(".mp4") == true) {
                            list.add(VideoItem(file.name ?: "Unknown", file.length(), file.uri))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                // MediaStore query
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
                        val uri = android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        if (name.startsWith("REC_") || name.contains("video")) {
                            list.add(VideoItem(name, size, uri))
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                videos.clear()
                videos.addAll(list.sortedByDescending { it.name })
                adapter.notifyDataSetChanged()
                
                if (videos.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvGallery.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.rvGallery.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun playVideo(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "video/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }

    private fun uploadVideo(item: VideoItem) {
        Toast.makeText(this, "Uploading ${item.name} to Catbox...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            val url = CatboxUploader.uploadFile(this@GalleryActivity, item.uri)
            withContext(Dispatchers.Main) {
                if (url != null) {
                    Toast.makeText(this@GalleryActivity, "Uploaded: $url", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@GalleryActivity, "Upload failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteVideo(item: VideoItem) {
        try {
            val settings = SettingsManager(this)
            val uriStr = settings.storageUriString
            var deleted = false
            
            if (uriStr != null) {
                val treeUri = Uri.parse(uriStr)
                val dir = DocumentFile.fromTreeUri(this, treeUri)
                val file = dir?.findFile(item.name)
                if (file != null && file.delete()) {
                    deleted = true
                }
            }
            
            if (!deleted) {
                val rows = contentResolver.delete(item.uri, null, null)
                if (rows > 0) deleted = true
            }

            if (deleted) {
                Toast.makeText(this, "Deleted ${item.name}", Toast.LENGTH_SHORT).show()
                loadVideos()
            } else {
                Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error deleting file", Toast.LENGTH_SHORT).show()
        }
    }
}

class GalleryAdapter(
    private val items: List<VideoItem>,
    private val onPlay: (Uri) -> Unit,
    private val onUpload: (VideoItem) -> Unit,
    private val onDelete: (VideoItem) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        val tvSize: TextView = view.findViewById(R.id.tvSize)
        val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        val btnPlay: ImageView = view.findViewById(R.id.btnPlay)
        val btnUpload: ImageView = view.findViewById(R.id.btnUpload)
        val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvFileName.text = item.name
        val sizeMb = item.size / (1024.0 * 1024.0)
        holder.tvSize.text = String.format("%.2f MB", sizeMb)
        
        holder.btnPlay.setOnClickListener { onPlay(item.uri) }
        holder.btnUpload.setOnClickListener { onUpload(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }

        // Load thumbnail
        holder.ivThumbnail.tag = item.uri
        holder.ivThumbnail.setImageDrawable(null) // clear previous
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    holder.itemView.context.contentResolver.loadThumbnail(item.uri, android.util.Size(300, 300), null)
                } else {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(holder.itemView.context, item.uri)
                    val bmp = retriever.getFrameAtTime(0)
                    retriever.release()
                    bmp
                }
                
                withContext(Dispatchers.Main) {
                    if (holder.ivThumbnail.tag == item.uri && bitmap != null) {
                        holder.ivThumbnail.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                // Ignore thumbnail errors
            }
        }
    }

    override fun getItemCount() = items.size
}
"""

with open("app/src/main/java/com/example/GalleryActivity.kt", "w") as f:
    f.write(content)

