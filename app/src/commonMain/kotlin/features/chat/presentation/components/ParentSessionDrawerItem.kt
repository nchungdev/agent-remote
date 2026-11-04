package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.EngineConversation

@Composable
fun ParentSessionDrawerItem(
    conversation: EngineConversation,
    isSelected: Boolean,
    currentWriterAgent: AgentType,
    onSelectSession: () -> Unit,
    onSelectAgentChild: (AgentType) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(isSelected) }

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 1.dp, bottom = 1.dp)
                .clickable {
                    onSelectSession()
                    isExpanded = !isExpanded
                },
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) Color(0xFF2E2E34) else Color.Transparent,
            border = if (isSelected) BorderStroke(1.dp, Color(0xFF3F3F46)) else null
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Mở rộng session con",
                    tint = Color(0xFF71717A),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { isExpanded = !isExpanded }
                )
                Spacer(modifier = Modifier.width(5.dp))

                if (conversation.pinned) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = "Đã ghim",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp
                    ),
                    color = if (isSelected) Color.White else Color(0xFFD4D4D8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                if (conversation.running) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                    )
                } else {
                    val relTime = formatRelativeTime(conversation.updatedAtMs)
                    if (relTime.isNotBlank()) {
                        Text(
                            text = relTime,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFF71717A),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Sub-agents / Child sessions (T4.7)
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val boundAgents = listOf(
                    AgentType.ANTIGRAVITY to (currentWriterAgent == AgentType.ANTIGRAVITY),
                    AgentType.CODEX to (currentWriterAgent == AgentType.CODEX),
                    AgentType.CLAUDE_CODE to (currentWriterAgent == AgentType.CLAUDE_CODE)
                )

                boundAgents.forEach { (agent, isWriter) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onSelectAgentChild(agent) },
                        color = if (isWriter && isSelected) Color(0xFF262428) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (isWriter) Color(0xFFD4785A) else Color(0xFF6B7280))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = agent.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                color = if (isWriter) Color(0xFFE5E7EB) else Color(0xFF9CA3AF),
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = if (isWriter) Color(0xFF3B251D) else Color(0xFF1E1F25)
                            ) {
                                Text(
                                    text = if (isWriter) "Writer" else "Chỉ đọc",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = if (isWriter) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isWriter) Color(0xFFD4785A) else Color(0xFF6B7280),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
