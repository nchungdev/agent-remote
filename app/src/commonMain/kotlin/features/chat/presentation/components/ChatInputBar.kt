package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Mic
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.AgentModel
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.EngineProject
import app.duinch.agentremote.core.domain.model.PermissionMode
import app.duinch.agentremote.features.chat.presentation.contract.AttachedFileItem
import app.duinch.agentremote.features.chat.presentation.contract.QueuedMessage

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
    selectedPermission: PermissionMode? = null,
    availablePermissions: List<PermissionMode> = emptyList(),
    onPermissionSelected: (PermissionMode) -> Unit = {},
    attachedFiles: List<AttachedFileItem> = emptyList(),
    isUploadingAttachment: Boolean = false,
    onRemoveAttachment: (String) -> Unit = {},
    availableProjects: List<EngineProject> = emptyList(),
    onProjectMentionSelected: (EngineProject) -> Unit = {},
    queuedMessages: List<QueuedMessage> = emptyList(),
    tokenUsage: app.duinch.agentremote.core.domain.model.ConversationTokenUsage? = null,
    onSendQueuedNow: (String) -> Unit = {},
    onEditQueued: (String) -> Unit = {},
    onRemoveQueued: (String) -> Unit = {},
    onAttachClick: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val modelsToDisplay = availableModels.ifEmpty { selectedAgent.defaultModels() }
    val activeModel = selectedModel ?: modelsToDisplay.firstOrNull()

    val permissionsToDisplay = availablePermissions.ifEmpty { selectedAgent.permissionModes() }
    val activePermission = selectedPermission ?: selectedAgent.defaultPermissionMode()

    val trimmed = inputText.trimStart()
    val slashQuery = if (trimmed.startsWith("/") && !trimmed.contains(' ')) {
        trimmed.lowercase()
    } else null
    val matchingSlashActions = remember(slashQuery) {
        if (slashQuery == null) emptyList()
        else DEFAULT_SLASH_ACTIONS.filter { it.command.startsWith(slashQuery) }
    }

    val lastToken = inputText.substringAfterLast(' ')
    val mentionQuery = if (lastToken.startsWith("@")) {
        lastToken.removePrefix("@").lowercase()
    } else null
    val matchingProjects = remember(mentionQuery, availableProjects) {
        if (mentionQuery == null) emptyList()
        else availableProjects.filter { proj ->
            proj.name.lowercase().contains(mentionQuery) ||
                proj.folders.any { it.lowercase().contains(mentionQuery) }
        }.take(5)
    }

    var showTokenDialog by remember { mutableStateOf(false) }

    if (showTokenDialog && tokenUsage != null) {
        TokenUsageDetailDialog(
            modelName = activeModel?.displayName ?: selectedAgent.displayName,
            tokenUsage = tokenUsage,
            onDismiss = { showTokenDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        QueuedMessagesCard(
            queuedMessages = queuedMessages,
            onSendNow = onSendQueuedNow,
            onEdit = onEditQueued,
            onDelete = onRemoveQueued,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ChatInputAutocompletePopup(
            inputText = inputText,
            matchingSlashActions = matchingSlashActions,
            matchingProjects = matchingProjects,
            onInputChanged = onInputChanged,
            onProjectMentionSelected = onProjectMentionSelected
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF16171B),
            border = BorderStroke(1.dp, Color(0xFF2A2B32))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 10.dp, top = 11.dp, bottom = 8.dp)
            ) {
                AttachedFilesStrip(
                    attachedFiles = attachedFiles,
                    isUploadingAttachment = isUploadingAttachment,
                    onRemoveAttachment = onRemoveAttachment
                )

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isCompact = maxWidth < 430.dp
                    val inputFontSize = if (isCompact) 14.sp else 15.sp
                    val chipFontSize = if (isCompact) 12.sp else 13.sp

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            BasicTextField(
                                value = inputText,
                                onValueChange = onInputChanged,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color(0xFFF3F4F6),
                                    fontSize = inputFontSize,
                                    lineHeight = 21.sp
                                ),
                                cursorBrush = SolidColor(Color(0xFFD4785A)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                                maxLines = 6,
                                decorationBox = { innerTextField ->
                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = "Ask anything, @ to mention, / for actions",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = Color(0xFF7D7F87),
                                                fontSize = inputFontSize
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left group: '+' button & Model Selector
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .requiredSize(28.dp)
                                        .clip(CircleShape)
                                        .clickable { onAttachClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = "Đính kèm tệp",
                                        tint = Color(0xFF9EA0A8),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(2.dp))

                                ModelSelectorDropdown(
                                    selectedAgent = selectedAgent,
                                    activeModel = activeModel,
                                    modelsToDisplay = modelsToDisplay,
                                    chipFontSize = chipFontSize,
                                    onModelSelected = onModelSelected,
                                    onViewUsage = { showTokenDialog = true },
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                if (tokenUsage != null) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    CompactTokenGaugeBadge(
                                        tokenUsage = tokenUsage,
                                        onClick = { showTokenDialog = true }
                                    )
                                }
                            }

                            // Right group: Permission Selector + Voice Mic + Send / Stop
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PermissionSelectorDropdown(
                                    activePermission = activePermission,
                                    permissionsToDisplay = permissionsToDisplay,
                                    chipFontSize = chipFontSize,
                                    isCompact = isCompact,
                                    onPermissionSelected = onPermissionSelected
                                )

                                Spacer(modifier = Modifier.width(2.dp))

                                Box(
                                    modifier = Modifier
                                        .requiredSize(28.dp)
                                        .clip(CircleShape)
                                        .clickable { onVoiceClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Mic,
                                        contentDescription = "Nhập giọng nói",
                                        tint = Color(0xFF9EA0A8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                if (isBusy) {
                                    Box(
                                        modifier = Modifier
                                            .requiredSize(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF282A30), CircleShape)
                                            .clickable { onCancel() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.5.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color(0xFFE55A5A))
                                        )
                                    }
                                    if (canSend) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .requiredSize(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF3B82F6), CircleShape)
                                                .clickable { onSend() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ArrowUpward,
                                                contentDescription = "Thêm vào hàng đợi",
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .requiredSize(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                color = if (canSend) Color(0xFFD4785A) else Color(0xFF282A30),
                                                shape = CircleShape
                                            )
                                            .clickable(enabled = canSend) { onSend() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ArrowUpward,
                                            contentDescription = "Gửi lệnh",
                                            tint = if (canSend) Color.White else Color(0xFF6E7078),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
