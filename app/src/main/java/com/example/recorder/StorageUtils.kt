// @dikaacode
package com.example.recorder

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.documentfile.provider.DocumentFile
import java.io.File

object StorageUtils {

    data class StorageInfo(
        val totalBytes: Long,
        val freeBytes: Long,
        val usedBytes: Long,
        val pathDisplay: String,
        val isExternal: Boolean
    ) {
        val totalFormatted: String get() = formatBytes(totalBytes)
        val freeFormatted: String get() = formatBytes(freeBytes)
        val usedFormatted: String get() = formatBytes(usedBytes)
        val percentUsed: Int get() = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
    }

    /**
     * Get storage statistics for the currently selected recording destination.
     */
    fun getStorageInfo(context: Context, treeUriString: String?): StorageInfo {
        if (treeUriString != null) {
            try {
                val uri = Uri.parse(treeUriString)
                val docDir = DocumentFile.fromTreeUri(context, uri)
                if (docDir != null && docDir.exists() && docDir.canWrite()) {
                    // Try to get StatFs from document URI path or fallback to external files dir
                    val path = uri.path ?: ""
                    val displayPath = formatTreeUriPath(uri)
                    
                    // Attempt to resolve file path for StatFs
                    val resolvedFile = resolveDocFile(context, uri)
                    if (resolvedFile != null && resolvedFile.exists()) {
                        val stat = StatFs(resolvedFile.absolutePath)
                        val total = stat.totalBytes
                        val free = stat.availableBytes
                        return StorageInfo(
                            totalBytes = total,
                            freeBytes = free,
                            usedBytes = (total - free).coerceAtLeast(0),
                            pathDisplay = displayPath,
                            isExternal = true
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Default: Primary External Storage (e.g. /sdcard/Movies/FastRecorder)
        val defaultDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val stat = StatFs(defaultDir.absolutePath)
        val total = stat.totalBytes
        val free = stat.availableBytes
        return StorageInfo(
            totalBytes = total,
            freeBytes = free,
            usedBytes = (total - free).coerceAtLeast(0),
            pathDisplay = "/sdcard/Movies/FastRecorder/",
            isExternal = false
        )
    }

    /**
     * Validates if a user-selected tree Uri is valid, accessible, and writable.
     */
    fun validateDirectory(context: Context, uri: Uri): Boolean {
        return try {
            val docDir = DocumentFile.fromTreeUri(context, uri)
            if (docDir == null || !docDir.exists() || !docDir.isDirectory || !docDir.canWrite()) {
                return false
            }
            // Test temporary file creation & deletion to confirm real write permissions
            val testFile = docDir.createFile("text/plain", ".test_write_${System.currentTimeMillis()}")
            if (testFile != null && testFile.exists()) {
                testFile.delete()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun resolveDocFile(context: Context, uri: Uri): File? {
        try {
            val path = uri.path ?: return null
            if (path.contains("primary:")) {
                val relPath = path.substringAfter("primary:")
                return File(Environment.getExternalStorageDirectory(), relPath)
            } else if (path.contains(":")) {
                // Secondary storage / SD card (e.g., 1A2B-3C4D:Recordings)
                val parts = path.split(":")
                val storageId = parts[0].substringAfterLast("/")
                val relPath = if (parts.size > 1) parts[1] else ""
                val extDirs = context.getExternalFilesDirs(null)
                for (dir in extDirs) {
                    if (dir != null && dir.absolutePath.contains(storageId)) {
                        val root = dir.absolutePath.substringBefore("/Android")
                        return File(root, relPath)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    }

    fun formatTreeUriPath(uri: Uri): String {
        val path = uri.path ?: return uri.toString()
        return when {
            path.contains("primary:") -> {
                val sub = path.substringAfter("primary:")
                "/sdcard/$sub"
            }
            path.contains(":") -> {
                val parts = path.split(":")
                val id = parts[0].substringAfterLast("/")
                val sub = if (parts.size > 1) parts[1] else ""
                "SD Card ($id)/$sub"
            }
            else -> path
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0.0 GB"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format("%.1f GB", gb)
        } else {
            val mb = bytes / (1024.0 * 1024.0)
            String.format("%.0f MB", mb)
        }
    }
}
