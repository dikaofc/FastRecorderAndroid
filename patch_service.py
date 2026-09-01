import sys

content = open("app/src/main/java/com/example/service/RecordingForegroundService.kt").read()

imports = """
import androidx.documentfile.provider.DocumentFile
import android.net.Uri
"""
content = content.replace('import android.provider.MediaStore', 'import android.provider.MediaStore\n' + imports)

new_func = """    private fun createOutputFile(name: String): ParcelFileDescriptor? {
        val settings = com.example.recorder.SettingsManager(this)
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
    }"""

# Using regex or simple replace
import re
content = re.sub(r'    private fun createOutputFile\(name: String\): ParcelFileDescriptor\? \{.*?\n    }', new_func, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/service/RecordingForegroundService.kt", "w") as f:
    f.write(content)
