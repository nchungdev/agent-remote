package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun ShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    baseColor: Color = Color(0xFF8E8E98),
    highlightColor: Color = Color(0xFFFFFFFF),
    durationMillis: Int = 1350,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    var textWidth by remember { mutableFloatStateOf(0f) }

    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    val width = if (textWidth > 0f) textWidth else 260f
    val beamWidth = (width * 0.45f).coerceIn(80f, 220f)
    val startX = (width + beamWidth * 2f) * progress - beamWidth

    val brush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            baseColor,
            highlightColor,
            highlightColor,
            baseColor,
            baseColor
        ),
        start = Offset(startX, 0f),
        end = Offset(startX + beamWidth, 0f)
    )

    Text(
        text = text,
        style = style.copy(brush = brush),
        modifier = modifier.onSizeChanged { size ->
            if (size.width > 0) {
                textWidth = size.width.toFloat()
            }
        },
        maxLines = maxLines,
        overflow = overflow
    )
}
