package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
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
            val lower = last.lowercase()
            when {
                lower.startsWith("read") || lower.contains("read_file") -> {
                    val file = last.substringAfter(":").trim().substringAfterLast('/').substringAfterLast('\\')
                    "Đọc ${if (file.isNotBlank()) file else "tệp"}"
                }
                lower.startsWith("edit") || lower.contains("edit_file") || lower.contains("write_file") -> {
                    val file = last.substringAfter(":").trim().substringAfterLast('/').substringAfterLast('\\')
                    "Chỉnh sửa ${if (file.isNotBlank()) file else "tệp"}"
                }
                lower.startsWith("run") || lower.contains("run_command") -> {
                    val cmd = last.substringAfter(":").trim()
                    val shortCmd = if (cmd.length > 38) cmd.take(35) + "..." else cmd
                    "Chạy ${if (shortCmd.isNotBlank()) shortCmd else "lệnh"}"
                }
                lower.startsWith("search") || lower.contains("grep") -> {
                    val q = last.substringAfter(":").trim()
                    "Tìm kiếm ${if (q.isNotBlank()) q.take(28) else "mã nguồn"}"
                }
                else -> {
                    if (last.contains(":")) last.substringAfter(":").trim() else last
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF18181C),
            border = BorderStroke(0.8.dp, Color(0xFF26262C)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Terminal,
                    contentDescription = null,
                    tint = if (isRunning) Color(0xFFD4785A) else Color(0xFF8E8E93),
                    modifier = Modifier
                        .size(14.dp)
                        .then(if (isRunning) Modifier.alpha(pulseAlpha) else Modifier)
                )
                Spacer(modifier = Modifier.width(9.dp))

                if (isRunning) {
                    val runText = if (activeToolName != null) {
                        "Đang thực thi: $activeToolName"
                    } else {
                        "Đang thực thi công cụ..."
                    }
                    ShimmerText(
                        text = runText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        baseColor = Color(0xFF9E9EA8),
                        highlightColor = Color(0xFFFFFFFF)
                    )
                } else {
                    val displayTitle = if (items.size > 1 && title == "Thực thi công cụ") {
                        "Đã thực thi ${items.size} lệnh & tệp"
                    } else {
                        title
                    }
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFFD4D6DC)
                    )
                    if (items.isNotEmpty() && displayTitle == title) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${items.size} tác vụ)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFF71717A)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                    contentDescription = if (isExpanded) "Thu gọn" else "Mở rộng",
                    tint = Color(0xFF71717A),
                    modifier = Modifier.size(15.dp)
                )
            }
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
