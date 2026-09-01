import sys
content = open("app/src/main/java/com/example/MainActivity.kt").read()

imports_idx = content.find("import androidx.compose.ui.Modifier")
new_imports = """import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
"""

# add imports
content = content[:imports_idx] + new_imports + content[imports_idx:]

home_idx = content.find("fun HomeScreen")
bg_composable = """
@Composable
fun DotGridBackground() {
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val spacing = 24.dp.toPx()
        val radius = 2.dp.toPx()
        
        val cols = (size.width / spacing).toInt()
        val rows = (size.height / spacing).toInt()
        
        for (i in 0..cols) {
            for (j in 0..rows) {
                drawCircle(
                    color = dotColor,
                    radius = radius,
                    center = Offset(i * spacing, j * spacing)
                )
            }
        }
    }
}

"""

content = content[:home_idx] + bg_composable + content[home_idx:]

# wrap HomeScreen content with Box and add DotGridBackground
col_idx = content.find("    Column(\n        modifier = Modifier\n            .fillMaxSize()\n            .padding(24.dp),")
new_col = """    Box(modifier = Modifier.fillMaxSize()) {
        DotGridBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
"""

if col_idx != -1:
    content = content.replace("    Column(\n        modifier = Modifier\n            .fillMaxSize()\n            .padding(24.dp),", new_col)

# Close Box at the end of HomeScreen
end_home_idx = content.find("        }\n    }\n}\n\n@Composable\nfun SettingsScreen")
if end_home_idx != -1:
    content = content.replace("        }\n    }\n}\n\n@Composable\nfun SettingsScreen", "        }\n    }\n    }\n}\n\n@Composable\nfun SettingsScreen")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
    print("Success patch")

