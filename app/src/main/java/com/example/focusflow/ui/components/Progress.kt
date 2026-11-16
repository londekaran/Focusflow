package com.example.focusflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A circular progress ring that animates smoothly to its value (0 to 1). Content is drawn in the middle. */
@Composable
fun ProgressRing(
    progress: Float, modifier: Modifier = Modifier, ringSize: Dp = 72.dp, stroke: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = tween(600), label = "ring")
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val topLeft = Offset(sw / 2, sw / 2)
            val arc = Size(size.width - sw, size.height - sw)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(sw))
            if (animated > 0.001f) drawArc(color, -90f, 360f * animated, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

/** A thin horizontal progress bar that animates to its value (0 to 1). */
@Composable
fun LinearProgress(
    progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, barHeight: Dp = 6.dp
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = tween(500), label = "bar")
    Box(modifier.fillMaxWidth().height(barHeight).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(CircleShape).background(color))
    }
}
