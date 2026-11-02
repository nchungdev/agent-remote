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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Terminal
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
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // 1. Thinking / Thought blocks
        if (!thinking.isNullOrBlank()) {
            TraceThinkingItem(thought = thinking, isStreaming = isStreaming)
        }

        // 2. Tool call steps (reading file, running command, editing file)
        toolCalls.forEach { tool ->
            TraceToolItem(toolCall = tool)
        }

        // 3. Live active working indicator
        if (isStreaming) {
            LiveWorkingIndicator()
        }
    }
}

@Composable
private fun TraceThinkingItem(
    thought: String,
    isStreaming: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }

    val charCount = thought.length
    val label = if (isStreaming && charCount < 100) "Thinking..." else "Thought (${charCount} chars)"

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFFA1A1AA)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF71717A),
                modifier = Modifier.size(14.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF18181B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text = thought,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = Color(0xFFCBD5E1),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun TraceToolItem(toolCall: ToolCall) {
    var isExpanded by remember { mutableStateOf(false) }

    val (icon, title, iconTint) = parseToolDisplay(toolCall)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = Color(0xFFE4E4E7)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF71717A),
                modifier = Modifier.size(14.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF18181B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (toolCall.argsJson.isNotBlank()) {
                        Text(
                            text = "Parameters:\n${toolCall.argsJson}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = Color(0xFF94A3B8)
                        )
                    }
                    if (!toolCall.result.isNullOrBlank()) {
                        Text(
                            text = "Output:\n${toolCall.result}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = if (toolCall.status == ToolStatus.FAILED) Color(0xFFEF4444) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun parseToolDisplay(tool: ToolCall): Triple<ImageVector, String, Color> {
    val name = tool.toolName.lowercase()
    val args = tool.argsJson

    return when {
        name.contains("command") || name.contains("terminal") || name.contains("bash") -> {
            val cmd = extractJsonField(args, "CommandLine") ?: extractJsonField(args, "command") ?: "command"
            val shortCmd = if (cmd.length > 40) cmd.take(37) + "..." else cmd
            val label = when (tool.status) {
                ToolStatus.RUNNING -> "Running $shortCmd..."
                ToolStatus.FAILED -> "Ran $shortCmd · Exited with error"
                else -> "Ran $shortCmd"
            }
            Triple(Icons.Default.Terminal, label, Color(0xFFA855F7))
        }

        name.contains("read") || name.contains("view") -> {
            val path = extractJsonField(args, "Path") ?: extractJsonField(args, "path") ?: "file"
            val fileName = path.substringAfterLast('/').substringAfterLast('\\')
            Triple(Icons.Default.Description, "Reading $fileName", Color(0xFF60A5FA))
        }

        name.contains("edit") || name.contains("write") || name.contains("replace") -> {
            val path = extractJsonField(args, "Path") ?: extractJsonField(args, "path") ?: "file"
            val fileName = path.substringAfterLast('/').substringAfterLast('\\')
            Triple(Icons.Default.Code, "Editing $fileName", Color(0xFF34D399))
        }

        else -> {
            Triple(Icons.Default.Code, "${tool.toolName}()", Color(0xFF94A3B8))
        }
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
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .alpha(alpha)
                .background(Color(0xFF3B82F6), CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Working",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color(0xFF94A3B8),
            modifier = Modifier.alpha(alpha)
        )
    }
}
