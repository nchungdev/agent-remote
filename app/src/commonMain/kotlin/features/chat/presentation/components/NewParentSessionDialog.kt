package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.AgentType

@Composable
fun NewParentSessionDialog(
    initialWorkspace: String?,
    initialAgent: AgentType,
    onDismiss: () -> Unit,
    onCreate: (title: String, useWorktree: Boolean, baseBranch: String, agent: AgentType) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("Phiên làm việc mới") }
    var useWorktree by remember { mutableStateOf(false) }
    var baseBranch by remember { mutableStateOf("main") }
    var selectedAgent by remember { mutableStateOf(initialAgent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Tạo phiên làm việc mới (Parent Session)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFF3F4F6)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    text = "Tên phiên:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD4785A),
                        unfocusedBorderColor = Color(0xFF383A46),
                        focusedTextColor = Color(0xFFF3F4F6),
                        unfocusedTextColor = Color(0xFFE5E7EB)
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Không gian làm việc (Checkout Mode):",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF)
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Option 1: Shared checkout
                WorkspaceOptionRow(
                    selected = !useWorktree,
                    title = "Dùng checkout hiện tại",
                    description = "Dùng chung working directory. Phù hợp cho task thông thường.",
                    onClick = { useWorktree = false }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 2: Worktree isolation
                WorkspaceOptionRow(
                    selected = useWorktree,
                    title = "Tạo worktree riêng (T4.7a)",
                    description = "Tạo bản sao worktree Git độc lập để cách ly file đang sửa.",
                    onClick = { useWorktree = true }
                )

                if (useWorktree) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Branch gốc:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9CA3AF)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = baseBranch,
                        onValueChange = { baseBranch = it },
                        singleLine = true,
                        placeholder = { Text("main", color = Color(0xFF6B7280)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFD4785A),
                            unfocusedBorderColor = Color(0xFF383A46),
                            focusedTextColor = Color(0xFFF3F4F6),
                            unfocusedTextColor = Color(0xFFE5E7EB)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Agent khởi tạo:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AgentType.ANTIGRAVITY, AgentType.CODEX, AgentType.CLAUDE_CODE).forEach { agent ->
                        val isSelected = agent == selectedAgent
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) Color(0xFF3B251D) else Color(0xFF23242A),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFD4785A) else Color(0xFF383A46)
                            ),
                            modifier = Modifier.clickable { selectedAgent = agent }
                        ) {
                            Text(
                                text = agent.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSelected) Color(0xFFD4785A) else Color(0xFFD1D5DB),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.trim().ifBlank { "Phiên làm việc mới" }
                    val finalBranch = baseBranch.trim().ifBlank { "main" }
                    onCreate(finalTitle, useWorktree, finalBranch, selectedAgent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4785A))
            ) {
                Text("Tạo phiên & Bắt đầu", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = Color(0xFF9CA3AF))
            }
        },
        containerColor = Color(0xFF1E1F25),
        modifier = modifier
    )
}

@Composable
private fun WorkspaceOptionRow(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) Color(0xFF262833) else Color(0xFF1B1C22),
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFFD4785A) else Color(0xFF2E303B)
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) Color(0xFFD4785A) else Color(0xFF6B7280),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) Color(0xFFF3F4F6) else Color(0xFFD1D5DB)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color(0xFF9CA3AF)
                )
            }
        }
    }
}
