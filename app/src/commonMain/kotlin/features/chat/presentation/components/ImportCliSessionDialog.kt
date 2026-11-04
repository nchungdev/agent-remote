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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.AgentType

@Composable
fun ImportCliSessionDialog(
    initialAgent: AgentType,
    onDismiss: () -> Unit,
    onImport: (agentType: AgentType, nativeSessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedAgent by remember { mutableStateOf(initialAgent) }
    var nativeId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Nhập phiên CLI đã có (T2.9 / T4.7)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFF3F4F6)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    text = "Chọn engine CLI nguồn:",
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Mã phiên gốc (Native Conversation / Session ID):",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = nativeId,
                    onValueChange = { nativeId = it },
                    singleLine = true,
                    placeholder = { Text("UUID hoặc ID phiên native...", color = Color(0xFF6B7280)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD4785A),
                        unfocusedBorderColor = Color(0xFF383A46),
                        focusedTextColor = Color(0xFFF3F4F6),
                        unfocusedTextColor = Color(0xFFE5E7EB)
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hệ thống sẽ đồng bộ lịch sử, gán checkout và tạo binding cho phiên cha.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color(0xFF9CA3AF)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val id = nativeId.trim()
                    if (id.isNotBlank()) {
                        onImport(selectedAgent, id)
                    }
                },
                enabled = nativeId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4785A))
            ) {
                Text("Nhập & Liên kết", color = Color.White)
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
