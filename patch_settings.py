import sys

content = open("app/src/main/java/com/example/recorder/SettingsManager.kt").read()

new_props = """
    var highPerformanceMode: Boolean
        get() = prefs.getBoolean("high_perf_mode", false)
        set(value) = prefs.edit().putBoolean("high_perf_mode", value).apply()

    var storageUriString: String?
        get() = prefs.getString("storage_uri", null)
        set(value) = prefs.edit().putString("storage_uri", value).apply()
"""

content = content.replace('    var themeMode: ThemeMode', new_props + '\n    var themeMode: ThemeMode')

with open("app/src/main/java/com/example/recorder/SettingsManager.kt", "w") as f:
    f.write(content)
