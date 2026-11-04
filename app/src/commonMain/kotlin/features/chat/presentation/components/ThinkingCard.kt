package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ThinkingCard(
    thought: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(false) }

    val transition = rememberInfiniteTransition()
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        )
    )

    val wordCount = remember(thought) {
        val trimmed = thought.trim()
        if (trimmed.isEmpty()) 0 else trimmed.split(Regex("\\s+")).size
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 4.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFD4785A),
                modifier = Modifier
                    .size(13.dp)
                    .then(if (isStreaming) Modifier.alpha(pulseAlpha) else Modifier)
            )
            Spacer(modifier = Modifier.width(7.dp))

            if (isStreaming) {
                ShimmerText(
                    text = "Đang suy nghĩ...",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    baseColor = Color(0xFFA1A1AA),
                    highlightColor = Color(0xFFFDE68A)
                )
            } else {
                Text(
                    text = "Suy nghĩ",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFFA1A1AA)
                )
                if (wordCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "($wordCount từ)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color(0xFF63636B)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                contentDescription = if (isExpanded) "Thu gọn" else "Mở rộng",
                tint = Color(0xFF71717A),
                modifier = Modifier.size(14.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 2.dp, bottom = 4.dp)
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF2C2C36))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = thought.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontStyle = FontStyle.Italic
                    ),
                    color = Color(0xFFA1A1AA),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
