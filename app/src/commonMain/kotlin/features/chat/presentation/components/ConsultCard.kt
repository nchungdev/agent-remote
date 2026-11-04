package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.ForwardToInbox
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.ChatMessage

@Composable
fun ConsultCard(
    message: ChatMessage,
    onSendToWriter: (String) -> Unit,
    onFollowUp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = message.isStreaming
    val content = message.content.removePrefix("🔍 Đang hỏi ý kiến ").trim()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1B1D24),
        border = BorderStroke(1.dp, Color(0xFF2E3345)),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ý kiến bên lề (Consult Branch • T4.6)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF3F4F6)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isRunning) Color(0xFF1E293B) else Color(0xFF14532D)
                ) {
                    Text(
                        text = if (isRunning) "Đang phân tích..." else "Hoàn tất",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold),
                        color = if (isRunning) Color(0xFF38BDF8) else Color(0xFF4ADE80),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (content.isNotBlank()) {
                MarkdownContent(
                    content = content,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!isRunning && content.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSendToWriter(content) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4785A)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.ForwardToInbox, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Gửi cho agent đang làm",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = { onFollowUp(content) },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF383A46)),
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Icon(Icons.Outlined.QuestionAnswer, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Hỏi tiếp",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color(0xFFD1D5DB)
                        )
                    }
                }
            }
        }
    }
}
