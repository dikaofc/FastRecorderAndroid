// @dikaacode
package com.example.recorder

import android.content.Context
import android.util.Log
import java.io.File

object TempFileCleaner {

    private const val TAG = "TempFileCleaner"
    private const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L

    /**
     * Automatically purges temporary recording fragments and cache files older than 24 hours.
     */
    fun purgeOldTempFiles(context: Context): Int {
        var purgedCount = 0
        val now = System.currentTimeMillis()
        try {
            val dirsToScan = listOfNotNull(
                context.cacheDir,
                context.externalCacheDir,
                File(context.filesDir, "temp"),
                File(context.cacheDir, "recordings")
            )

            for (dir in dirsToScan) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile && (now - file.lastModified() > TWENTY_FOUR_HOURS_MS)) {
                            if (file.delete()) {
                                purgedCount++
                                Log.d(TAG, "Purged old temp file: ${file.name}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error purging old temp files", e)
        }
        return purgedCount
    }

    /**
     * Computes the total cache and temporary files size in bytes.
     */
    fun getCacheSize(context: Context): Long {
        var totalSize = 0L
        try {
            val dirsToScan = listOfNotNull(
                context.cacheDir,
                context.externalCacheDir,
                File(context.filesDir, "temp"),
                File(context.cacheDir, "recordings")
            )

            for (dir in dirsToScan) {
                totalSize += getDirectorySize(dir)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating cache size", e)
        }
        return totalSize
    }

    /**
     * Clears all temporary recording files, cache directory, and orphaned metadata.
     * Returns total bytes freed.
     */
    fun clearAllCache(context: Context): Long {
        var freedBytes = 0L
        try {
            val dirsToClean = listOfNotNull(
                context.cacheDir,
                context.externalCacheDir,
                File(context.filesDir, "temp"),
                File(context.cacheDir, "recordings")
            )

            for (dir in dirsToClean) {
                freedBytes += deleteDirectoryContents(dir)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
        return freedBytes
    }

    private fun getDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirectorySize(file) else file.length()
        }
        return size
    }

    private fun deleteDirectoryContents(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var freed = 0L
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                freed += deleteDirectoryContents(file)
                file.delete()
            } else {
                freed += file.length()
                file.delete()
            }
        }
        return freed
    }
}
