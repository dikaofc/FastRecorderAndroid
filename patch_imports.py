import sys
content = open("app/src/main/java/com/example/MainActivity.kt").read()

imports = """
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import com.example.R
"""

import_idx = content.find("import android.os.Bundle")
if import_idx != -1:
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(content[:import_idx] + imports + content[import_idx:])
        print("Success imports")
else:
    print("Not found imports")
