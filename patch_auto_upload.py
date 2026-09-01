import sys
import re

service_file = "app/src/main/java/com/example/service/RecordingForegroundService.kt"
content = open(service_file).read()

# Add ClipboardManager and Handler imports if missing
imports = """
import android.content.ClipboardManager
import android.content.ClipData
import android.os.Handler
import android.os.Looper
import android.widget.Toast
"""
if "import android.content.ClipboardManager" not in content:
    content = content.replace("import android.content.Intent", imports + "\nimport android.content.Intent")

upload_logic_old = """                    val cloudUrl = com.example.recorder.CatboxUploader.uploadFile(this@RecordingForegroundService, uri)
                    if (cloudUrl != null) {
                        val id = ContentUris.parseId(uri)
                        settings.setCloudUrl(id, cloudUrl)
                        RecordingState.setSavedMessage("Uploaded to cloud: $cloudUrl")
                    } else {"""

upload_logic_new = """                    val cloudUrl = com.example.recorder.CatboxUploader.uploadFile(this@RecordingForegroundService, uri)
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
                    } else {"""

if "val clipboard =" not in content:
    content = content.replace(upload_logic_old, upload_logic_new)

with open(service_file, "w") as f:
    f.write(content)

gallery_file = "app/src/main/java/com/example/GalleryActivity.kt"
gallery_content = open(gallery_file).read()

if "import android.content.ClipboardManager" not in gallery_content:
    gallery_content = gallery_content.replace("import android.content.Intent", "import android.content.ClipboardManager\nimport android.content.ClipData\nimport android.content.Intent")

manual_upload_old = """            val url = CatboxUploader.uploadFile(this@GalleryActivity, item.uri)
            withContext(Dispatchers.Main) {
                if (url != null) {
                    Toast.makeText(this@GalleryActivity, "Uploaded: $url", Toast.LENGTH_LONG).show()
                } else {"""

manual_upload_new = """            val url = CatboxUploader.uploadFile(this@GalleryActivity, item.uri)
            withContext(Dispatchers.Main) {
                if (url != null) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Catbox URL", url)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@GalleryActivity, "Link Copied! $url", Toast.LENGTH_LONG).show()
                    
                    try {
                        val id = android.content.ContentUris.parseId(item.uri)
                        SettingsManager(this@GalleryActivity).setCloudUrl(id, url)
                    } catch(e: Exception) {
                        SettingsManager(this@GalleryActivity).setCloudUrl(item.uri.hashCode().toLong(), url)
                    }
                } else {"""

if "val clipboard = getSystemService" not in gallery_content:
    gallery_content = gallery_content.replace(manual_upload_old, manual_upload_new)

with open(gallery_file, "w") as f:
    f.write(gallery_content)

