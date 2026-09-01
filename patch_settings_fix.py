import sys

content = open("app/src/main/java/com/example/SettingsActivity.kt").read()

bad = """    private fun setupSwitches()
        updateStoragePathUI()
        binding.tvStoragePath.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            directoryPickerLauncher.launch(intent)
        } {"""
        
good = """    private fun setupSwitches() {
        updateStoragePathUI()
        binding.tvStoragePath.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            directoryPickerLauncher.launch(intent)
        }"""
        
content = content.replace(bad, good)

with open("app/src/main/java/com/example/SettingsActivity.kt", "w") as f:
    f.write(content)

