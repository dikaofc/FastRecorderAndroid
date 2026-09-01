import sys
content = open("app/src/main/java/com/example/MainActivity.kt").read()

start_idx = content.find("        ) {\n            Column {\n                Text(\"FastRecorder\"")
end_idx = content.find("                Text(\n                    text = if (isRecording) \"Recording in progress...\" else \"Ready to capture\",")

new_code = """        ) {
            val transition = androidx.compose.animation.core.rememberInfiniteTransition()
            val scale by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                )
            )
            androidx.compose.foundation.Image(
                bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(id = R.drawable.fast_recorder_logo_1788107942806),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("FastRecorder", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
"""

if start_idx != -1 and end_idx != -1:
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(content[:start_idx] + new_code + content[end_idx:])
        print("Success logo")
else:
    print(f"Not found {start_idx} {end_idx}")
