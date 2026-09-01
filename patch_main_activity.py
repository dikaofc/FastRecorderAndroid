import sys

content = open("app/src/main/java/com/example/MainActivity.kt").read()
content = content.replace(
    'Toast.makeText(this@MainActivity, "Settings clicked", Toast.LENGTH_SHORT).show()',
    'startActivity(Intent(this@MainActivity, SettingsActivity::class.java))'
)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
