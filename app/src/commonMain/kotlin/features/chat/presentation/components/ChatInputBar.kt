package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.AgentModel
import app.duinch.agentremote.core.domain.model.AgentType

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    isBusy: Boolean,
    canSend: Boolean,
    selectedAgent: AgentType,
    selectedModel: AgentModel?,
    availableModels: List<AgentModel>,
    onModelSelected: (AgentModel) -> Unit,
    onAttachClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showModelMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF212124),
        border = BorderStroke(1.dp, Color(0xFF2E2E33))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Text input field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = Color(0xFFF4F4F5),
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(Color(0xFFCC785C)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    maxLines = 6,
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            val hintName = selectedModel?.displayName ?: selectedAgent.displayName
                            Text(
                                text = "Chỉ đạo $hintName...",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color(0xFF71717A),
                                    fontSize = 15.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row (Attach, Model picker, Send/Stop)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach button
                IconButton(
                    onClick = onAttachClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Đính kèm file",
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Model Selector Pill
                Box {
                    val displayName = selectedModel?.displayName
                        ?: availableModels.firstOrNull()?.displayName
                        ?: "Đang tải models..."

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF2A2A2F),
                        border = BorderStroke(1.dp, Color(0xFF38383F)),
                        modifier = Modifier.clickable {
                            if (availableModels.isNotEmpty()) showModelMenu = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                color = Color(0xFFE4E4E7),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Chọn Model",
                                tint = Color(0xFFA1A1AA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (availableModels.isNotEmpty()) {
                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false },
                            modifier = Modifier
                                .background(Color(0xFF212126))
                                .padding(vertical = 4.dp)
                        ) {
                            availableModels.forEach { model ->
                                val isSelected = model.id == selectedModel?.id
                                DropdownMenuItem(
                                    text = {
                                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = model.displayName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 14.sp
                                                    ),
                                                    color = if (isSelected) Color(0xFFCC785C) else Color(0xFFF4F4F5)
                                                )
                                                if (model.efforts.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (isSelected) Color(0xFFCC785C).copy(alpha = 0.2f) else Color(0xFF33333A)
                                                    ) {
                                                        Text(
                                                            text = model.efforts.joinToString("/"),
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 9.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.SemiBold
                                                            ),
                                                            color = if (isSelected) Color(0xFFCC785C) else Color(0xFFA1A1AA),
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (model.description.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = model.description,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 11.sp,
                                                        lineHeight = 14.sp
                                                    ),
                                                    color = Color(0xFF71717A),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Đang chọn",
                                                tint = Color(0xFFCC785C),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        onModelSelected(model)
                                        showModelMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Send / Stop button
                AnimatedVisibility(
                    visible = isBusy,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Hủy tác vụ",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !isBusy,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    IconButton(
                        onClick = onSend,
                        enabled = canSend,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) Color(0xFFCC785C) else Color(0xFF333338)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Gửi",
                            tint = if (canSend) Color.White else Color(0xFF71717A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
