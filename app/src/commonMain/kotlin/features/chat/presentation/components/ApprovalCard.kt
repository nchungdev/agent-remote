package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.ApprovalRequest

@Composable
fun ApprovalCard(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A1A20),
        border = BorderStroke(1.dp, Color(0xFF323038))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row: Terminal icon + title + tool badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Terminal,
                    contentDescription = null,
                    tint = Color(0xFFD4785A),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Yêu cầu chạy công cụ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    ),
                    color = Color(0xFFE4E4E7),
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = Color(0xFF26262F)
                ) {
                    Text(
                        text = request.toolName.ifBlank { "bash" }.lowercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFFA1A1AA),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // Terminal command preview box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF111115),
                border = BorderStroke(0.5.dp, Color(0xFF25252E))
            ) {
                Text(
                    text = request.summary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    ),
                    color = Color(0xFFE4E4E7),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }

            // Action buttons pair
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Reject button: muted dark grey
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF26262F),
                    border = BorderStroke(1.dp, Color(0xFF343440)),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onReject() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Từ chối",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = Color(0xFFA1A1AA)
                        )
                    }
                }

                // Approve button: signature warm copper
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFD4785A),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onApprove() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Cho phép",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
