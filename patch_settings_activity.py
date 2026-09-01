import sys

content = open("app/src/main/java/com/example/SettingsActivity.kt").read()

imports = """
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Toast
"""

content = content.replace('import com.example.recorder.SettingsManager', 'import com.example.recorder.SettingsManager\n' + imports)

# We need to add the storage picker launcher
launcher = """
    private val directoryPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                settingsManager.storageUriString = uri.toString()
                updateStoragePathUI()
            }
        }
    }
    
    private fun updateStoragePathUI() {
        val uriStr = settingsManager.storageUriString
        if (uriStr != null) {
            binding.tvStoragePath.text = Uri.parse(uriStr).path?.replace("tree/primary:", "/sdcard/") ?: uriStr
        } else {
            binding.tvStoragePath.text = "/sdcard/Movies/FastRecorder/"
        }
    }
"""

content = content.replace('private lateinit var settingsManager: SettingsManager', 'private lateinit var settingsManager: SettingsManager\n' + launcher)

# In setupSwitches, bind toggleHighPerf to settingsManager and tvStoragePath
high_perf_setup = """
        updateToggle(binding.toggleHighPerf, settingsManager.highPerformanceMode)
        binding.toggleHighPerf.setOnClickListener {
            val newState = !settingsManager.highPerformanceMode
            settingsManager.highPerformanceMode = newState
            updateToggle(binding.toggleHighPerf, newState)
        }
"""
content = content.replace("""        // Just mapping high perf mode to some dummy state for now as it's not fully handled
        var highPerfState = false
        updateToggle(binding.toggleHighPerf, highPerfState)
        binding.toggleHighPerf.setOnClickListener {
            highPerfState = !highPerfState
            updateToggle(binding.toggleHighPerf, highPerfState)
        }""", high_perf_setup)

# In onCreate, call updateStoragePathUI and set click listener
on_create_add = """
        updateStoragePathUI()
        binding.tvStoragePath.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            directoryPickerLauncher.launch(intent)
        }
"""
content = content.replace('setupSwitches()', 'setupSwitches()\n' + on_create_add)

with open("app/src/main/java/com/example/SettingsActivity.kt", "w") as f:
    f.write(content)

