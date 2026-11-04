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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun ShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    baseColor: Color = Color(0xFF71717A),
    highlightColor: Color = Color(0xFFF4F4F5),
    durationMillis: Int = 1600,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    val transition = rememberInfiniteTransition()
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val shimmerWidth = 220f
    val totalDistance = 800f
    val startX = (totalDistance + shimmerWidth) * progress - shimmerWidth

    val brush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor
        ),
        start = Offset(startX, 0f),
        end = Offset(startX + shimmerWidth, 0f)
    )

    Text(
        text = text,
        style = style.copy(brush = brush),
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow
    )
}
