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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Terminal
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ToolTraceCard(
    items: List<String>,
    modifier: Modifier = Modifier,
    title: String = "Thực thi công cụ",
    isRunning: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(false) }

    val transition = rememberInfiniteTransition()
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        )
    )

    val activeToolName = remember(items) {
        val last = items.lastOrNull()?.removePrefix("•")?.trim()
        if (last.isNullOrBlank()) {
            null
        } else {
            // If format is "run_command: Test something", show "Test something" or full summary
            if (last.contains(":")) {
                last.substringAfter(":").trim()
            } else {
                last
            }
        }
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
                imageVector = Icons.Outlined.Terminal,
                contentDescription = null,
                tint = if (isRunning) Color(0xFFD4785A) else Color(0xFF8E8E93),
                modifier = Modifier
                    .size(13.dp)
                    .then(if (isRunning) Modifier.alpha(pulseAlpha) else Modifier)
            )
            Spacer(modifier = Modifier.width(7.dp))

            if (isRunning) {
                val runText = if (activeToolName != null) {
                    "Đang chạy: $activeToolName"
                } else {
                    "Đang thực thi công cụ..."
                }
                ShimmerText(
                    text = runText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    baseColor = Color(0xFFA1A1AA),
                    highlightColor = Color(0xFFFDBA74)
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFFA1A1AA)
                )
                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${items.size} tác vụ)",
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items.forEach { rawItem ->
                        val cleanItem = rawItem.removePrefix("•").trim()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "$",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFFD4785A),
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = cleanItem,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.5.sp
                                ),
                                color = Color(0xFFC7C7CC),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
