// @dikaacode
package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.neoShadow(
    color: Color? = null,
    offsetX: Dp = 4.dp,
    offsetY: Dp = 4.dp,
    cornerRadius: Dp = 8.dp
) = this.drawBehind {
    val shadowColor = color ?: Color.Black // fallback
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = shadowColor.toArgb()
        frameworkPaint.style = android.graphics.Paint.Style.FILL

        val leftPixel = offsetX.toPx()
        val topPixel = offsetY.toPx()
        val rightPixel = size.width + leftPixel
        val bottomPixel = size.height + topPixel

        canvas.drawRoundRect(
            left = leftPixel,
            top = topPixel,
            right = rightPixel,
            bottom = bottomPixel,
            radiusX = cornerRadius.toPx(),
            radiusY = cornerRadius.toPx(),
            paint = paint
        )
    }
}

@Composable
fun NeoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    content: @Composable RowScope.() -> Unit
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    Button(
        onClick = onClick,
        modifier = modifier
            .neoShadow(color = outlineColor, cornerRadius = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = if (backgroundColor == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(3.dp, outlineColor),
        shape = RoundedCornerShape(12.dp),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val cardModifier = modifier.neoShadow(color = outlineColor, cornerRadius = 16.dp)
    
    val clickableModifier = if (onClick != null) {
        cardModifier.clickable { onClick() }
    } else {
        cardModifier
    }

    Card(
        modifier = clickableModifier,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(3.dp, outlineColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}
