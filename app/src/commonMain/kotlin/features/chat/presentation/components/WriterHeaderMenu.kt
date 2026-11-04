package app.duinch.agentremote.features.chat.presentation.components

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.AgentState
import app.duinch.agentremote.core.domain.model.AgentType

@Composable
fun WriterHeaderMenu(
    currentAgent: AgentType,
    agentState: AgentState,
    activeBranch: String?,
    workspaceLabel: String?,
    switchWriterConfirmTarget: AgentType?,
    onSwitchWriter: (AgentType) -> Unit,
    onConfirmSwitch: (AgentType, Boolean) -> Unit,
    onDismissConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isBusy = agentState.isBusy
    val statusColor = if (isBusy) Color(0xFFF59E0B) else Color(0xFF10B981)

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF23242A),
            border = BorderStroke(0.5.dp, Color(0xFF383A46)),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Đang làm • ${currentAgent.badge}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = Color(0xFFD4785A)
                )
                if (!activeBranch.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = Color(0xFF18191E)
                    ) {
                        Text(
                            text = "⎇ $activeBranch",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Medium),
                            color = Color(0xFF9CA3AF),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Chuyển quyền ghi",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFF1E1F25))
                .widthIn(min = 280.dp, max = 340.dp)
                .padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = "Điều phối Agent trong phiên",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFF3F4F6)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Mỗi checkout có duy nhất 1 Writer. Agent khác chỉ đọc.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color(0xFF9CA3AF)
                )
                if (!workspaceLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Vị trí: $workspaceLabel (branch: ${activeBranch ?: "main"})",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color(0xFFD4785A)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF2E303B), thickness = 0.5.dp)

            val availableAgents = listOf(AgentType.ANTIGRAVITY, AgentType.CODEX, AgentType.CLAUDE_CODE)
            availableAgents.forEach { agent ->
                val isCurrent = agent == currentAgent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(!isCurrent) {
                            expanded = false
                            onSwitchWriter(agent)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = agent.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isCurrent) Color(0xFFD4785A) else Color(0xFFE5E7EB)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = if (isCurrent) Color(0xFF3B251D) else Color(0xFF282A33)
                            ) {
                                Text(
                                    text = agent.badge,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = if (isCurrent) Color(0xFFD4785A) else Color(0xFF9CA3AF),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isCurrent) "Đang giữ quyền ghi (Active Writer)" else "Chỉ đọc • Bấm để chuyển quyền",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = if (isCurrent) Color(0xFF10B981) else Color(0xFF6B7280)
                        )
                    }

                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Active",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (switchWriterConfirmTarget != null) {
        AlertDialog(
            onDismissRequest = onDismissConfirm,
            title = {
                Text(
                    text = "Chuyển quyền ghi khi đang chạy?",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFF3F4F6)
                )
            },
            text = {
                Text(
                    text = "Agent ${currentAgent.displayName} đang thực thi tác vụ. Bạn muốn dừng lượt hiện tại và chuyển quyền ghi sang ${switchWriterConfirmTarget.displayName} hay tiếp tục?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = Color(0xFFD1D5DB)
                )
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmSwitch(switchWriterConfirmTarget, true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4785A))
                ) {
                    Text("Dừng & Chuyển", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissConfirm) {
                    Text("Tiếp tục lượt này", color = Color(0xFF9CA3AF))
                }
            },
            containerColor = Color(0xFF1E1F25)
        )
    }
}
