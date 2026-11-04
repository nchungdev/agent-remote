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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.ToolCall
import app.duinch.agentremote.core.domain.model.ToolStatus

@Composable
fun ExecutionTraceView(
    thinking: String?,
    toolCalls: List<ToolCall>,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!thinking.isNullOrBlank()) {
            TraceThinkingItem(thought = thinking, isStreaming = isStreaming)
        }

        toolCalls.forEach { tool ->
            TraceToolItem(toolCall = tool)
        }

        if (isStreaming && thinking.isNullOrBlank() && toolCalls.isEmpty()) {
            LiveWorkingIndicator()
        }
    }
}

@Composable
private fun TraceThinkingItem(
    thought: String,
    isStreaming: Boolean
) {
    // Collapsed by default — flat, borderless inline row like Antigravity / Claude / Codex
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

    val label = if (isStreaming) "Đang suy nghĩ..." else "Suy nghĩ"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFF8E8E93),
                modifier = if (isStreaming) Modifier.alpha(pulseAlpha) else Modifier
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                contentDescription = if (isExpanded) "Thu gọn" else "Mở rộng",
                tint = Color(0xFF71717A),
                modifier = Modifier.size(15.dp)
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
                    .padding(start = 4.dp, top = 4.dp, bottom = 6.dp)
                    .height(IntrinsicSize.Min)
            ) {
                // Thin 1dp vertical timeline line (no surrounding box)
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF2E2E34))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = thought.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    ),
                    color = Color(0xFF8E8E93),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TraceToolItem(toolCall: ToolCall) {
    var isExpanded by remember { mutableStateOf(false) }
    val (icon, title) = parseToolDisplay(toolCall)
    val isRunning = toolCall.status == ToolStatus.RUNNING || toolCall.status == ToolStatus.PENDING_APPROVAL

    val transition = rememberInfiniteTransition()
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF71717A),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = if (toolCall.status == ToolStatus.FAILED) Color(0xFFEF4444) else Color(0xFF8E8E93),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isRunning) Modifier.alpha(pulseAlpha) else Modifier
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF63636B),
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
                    .padding(start = 4.dp, top = 4.dp, bottom = 6.dp)
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF2E2E34))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF141418),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        if (toolCall.argsJson.isNotBlank()) {
                            Text(
                                text = toolCall.argsJson,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                ),
                                color = Color(0xFF8E8E93)
                            )
                        }
                        if (!toolCall.result.isNullOrBlank()) {
                            Text(
                                text = toolCall.result,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    lineHeight = 17.sp
                                ),
                                color = if (toolCall.status == ToolStatus.FAILED) Color(0xFFEF4444) else Color(0xFFA1A1AA),
                                modifier = Modifier.padding(top = if (toolCall.argsJson.isNotBlank()) 6.dp else 0.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseToolDisplay(tool: ToolCall): Pair<ImageVector, String> {
    val name = tool.toolName.lowercase()
    val args = tool.argsJson

    return when {
        name.contains("command") || name.contains("terminal") || name.contains("bash") -> {
            val cmd = extractJsonField(args, "CommandLine") ?: extractJsonField(args, "command") ?: "command"
            val shortCmd = if (cmd.length > 42) cmd.take(39) + "..." else cmd
            val prefix = if (tool.status == ToolStatus.RUNNING || tool.status == ToolStatus.PENDING_APPROVAL) {
                "Đang chạy"
            } else {
                "Đã chạy"
            }
            Icons.Outlined.Terminal to "$prefix $shortCmd"
        }
        name.contains("read") || name.contains("view") -> {
            val path = extractJsonField(args, "Path") ?: extractJsonField(args, "path") ?: "file"
            val fileName = path.substringAfterLast('/').substringAfterLast('\\')
            Icons.Outlined.Description to "Đọc tệp $fileName"
        }
        name.contains("edit") || name.contains("write") || name.contains("replace") -> {
            val path = extractJsonField(args, "Path") ?: extractJsonField(args, "path") ?: "file"
            val fileName = path.substringAfterLast('/').substringAfterLast('\\')
            Icons.Outlined.Code to "Chỉnh sửa $fileName"
        }
        else -> Icons.Outlined.Code to tool.toolName
    }
}

private fun extractJsonField(json: String, field: String): String? {
    val regex = Regex("\"$field\"\\s*:\\s*\"([^\"]+)\"")
    return regex.find(json)?.groupValues?.getOrNull(1)
}

@Composable
private fun LiveWorkingIndicator() {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .alpha(alpha)
                .background(Color(0xFF8E8E93), CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Đang xử lý...",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color(0xFF8E8E93),
            modifier = Modifier.alpha(alpha)
        )
    }
}
