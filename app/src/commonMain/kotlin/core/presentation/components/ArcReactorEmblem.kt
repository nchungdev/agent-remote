package app.duinch.agentremote.core.presentation.components

import agent_remote.generated.resources.Res
import agent_remote.generated.resources.app_logo
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.painterResource

@Composable
fun ArcReactorEmblem(
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "EmblemTransition")
    val pulse by if (animated) {
        transition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "LogoPulse"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.app_logo),
            contentDescription = "Agent Remote Logo",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(percent = 22))
                .graphicsLayer {
                    alpha = pulse
                }
        )
    }
}
