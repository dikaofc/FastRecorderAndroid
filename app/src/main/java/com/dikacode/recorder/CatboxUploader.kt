// @dikaacode
package com.dikacode.recorder

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source
import java.io.InputStream
import java.util.concurrent.TimeUnit

object CatboxUploader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun uploadFile(
        context: Context,
        uri: Uri,
        onProgress: ((percent: Int, bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            
            var fileName = "REC_${System.currentTimeMillis()}.mp4"
            var fileSize = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            if (fileSize <= 0L) {
                try {
                    contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        fileSize = afd.length
                    }
                } catch (e: Exception) {
                    fileSize = 1L
                }
            }

            val finalTotalBytes = if (fileSize > 0) fileSize else 1L

            val customRequestBody = object : RequestBody() {
                override fun contentType() = "video/mp4".toMediaTypeOrNull()

                override fun contentLength(): Long = finalTotalBytes

                override fun writeTo(sink: BufferedSink) {
                    val inputStream: InputStream? = contentResolver.openInputStream(uri)
                    if (inputStream == null) return

                    val buffer = ByteArray(8192)
                    var bytesSent = 0L
                    var lastReportTime = 0L

                    inputStream.use { stream ->
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1) {
                            sink.write(buffer, 0, read)
                            bytesSent += read

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastReportTime > 100 || bytesSent >= finalTotalBytes) {
                                lastReportTime = currentTime
                                val percent = ((bytesSent * 100) / finalTotalBytes).toInt().coerceIn(0, 100)
                                onProgress?.invoke(percent, bytesSent, finalTotalBytes)
                            }
                        }
                    }
                }
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("fileToUpload", fileName, customRequestBody)
                .build()

            val request = Request.Builder()
                .url("https://catbox.moe/user/api.php")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val url = response.body?.string()?.trim()
                if (url != null && url.startsWith("http")) {
                    onProgress?.invoke(100, finalTotalBytes, finalTotalBytes)
                    return@withContext url
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
