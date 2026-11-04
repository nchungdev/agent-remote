package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.ChatMessage
import app.duinch.agentremote.core.domain.model.MessageRole

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                color = Color(0xFF282422),
                border = BorderStroke(1.dp, Color(0xFF443933)),
                modifier = Modifier.widthIn(max = 335.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        letterSpacing = 0.1.sp
                    ),
                    color = Color(0xFFF5F4F0),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                if (message.hasThinking || message.hasToolCalls || (message.isStreaming && message.content.isBlank())) {
                    ExecutionTraceView(
                        thinking = message.thinking,
                        toolCalls = message.toolCalls,
                        isStreaming = message.isStreaming,
                        modifier = Modifier.padding(bottom = if (message.content.isNotBlank()) 8.dp else 0.dp)
                    )
                }

                if (message.content.isNotBlank()) {
                    MarkdownContent(
                        content = message.content,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (message.isStreaming && message.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    StreamingCursor()
                }
            }
        }
    }
}

@Composable
private fun StreamingCursor() {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(550),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .size(7.dp)
            .alpha(alpha)
            .background(Color(0xFFD4785A), CircleShape)
    )
}
