package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.AgentModel
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.EngineProject
import app.duinch.agentremote.core.domain.model.PermissionMode
import app.duinch.agentremote.features.chat.presentation.contract.AttachedFileItem

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
    onAttachClick: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showModelMenu by remember { mutableStateOf(false) }
    var showPermissionMenu by remember { mutableStateOf(false) }

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
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
                            // Left group: '+' and Model Selector
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Borderless compact '+' button (28dp, Outlined icon)
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

                                // 2. Borderless Two-Tone Model Selector
                                Box(modifier = Modifier.weight(1f, fill = false)) {
                                    val rawName = activeModel?.displayName ?: "Model"
                                    val (baseName, variantName) = splitModelName(rawName)

                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (showModelMenu) Color(0xFF23242A) else Color.Transparent)
                                            .clickable { showModelMenu = true }
                                            .padding(horizontal = 5.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = baseName,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                                fontSize = chipFontSize
                                            ),
                                            color = Color(0xFFC8CAD0),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (variantName != null) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = variantName,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Normal,
                                                    fontSize = chipFontSize
                                                ),
                                                color = Color(0xFF7D7F87),
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = if (showModelMenu) {
                                                Icons.Outlined.KeyboardArrowDown
                                            } else {
                                                Icons.Outlined.KeyboardArrowUp
                                            },
                                            contentDescription = "Chọn Model",
                                            tint = Color(0xFF7D7F87),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showModelMenu,
                                        onDismissRequest = { showModelMenu = false },
                                        modifier = Modifier
                                            .background(Color(0xFF18191E))
                                            .border(1.dp, Color(0xFF2C2D35), RoundedCornerShape(12.dp))
                                            .widthIn(min = 240.dp, max = 300.dp)
                                    ) {
                                        Text(
                                            text = "MODEL • ${selectedAgent.displayName.uppercase()}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.5.sp,
                                                letterSpacing = 0.7.sp
                                            ),
                                            color = Color(0xFF8A8D96),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                        HorizontalDivider(color = Color(0xFF26272F), thickness = 0.5.dp)

                                        modelsToDisplay.forEach { model ->
                                            val isSelected = model.id == activeModel?.id
                                            ModelMenuItem(
                                                model = model,
                                                isSelected = isSelected,
                                                onClick = {
                                                    onModelSelected(model)
                                                    showModelMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Right group: Permission Selector + Voice Mic + Circular 28dp Send/Stop
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 3. Borderless Per-Engine Permission Mode Selector
                                Box {
                                    if (isCompact) {
                                        Box(
                                            modifier = Modifier
                                                .requiredSize(28.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (showPermissionMenu) Color(0xFF23242A) else Color.Transparent,
                                                    CircleShape
                                                )
                                                .clickable { showPermissionMenu = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = permissionOutlinedVector(activePermission.iconType),
                                                contentDescription = activePermission.label,
                                                tint = Color(0xFF9EA0A8),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (showPermissionMenu) Color(0xFF23242A) else Color.Transparent
                                                )
                                                .clickable { showPermissionMenu = true }
                                                .padding(horizontal = 7.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = permissionOutlinedVector(activePermission.iconType),
                                                contentDescription = null,
                                                tint = Color(0xFF9EA0A8),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = activePermission.label,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = chipFontSize
                                                ),
                                                color = Color(0xFFC8CAD0),
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Icon(
                                                imageVector = if (showPermissionMenu) {
                                                    Icons.Outlined.KeyboardArrowDown
                                                } else {
                                                    Icons.Outlined.KeyboardArrowUp
                                                },
                                                contentDescription = "Chế độ quyền",
                                                tint = Color(0xFF7D7F87),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showPermissionMenu,
                                        onDismissRequest = { showPermissionMenu = false },
                                        modifier = Modifier
                                            .background(Color(0xFF18191E))
                                            .border(1.dp, Color(0xFF2C2D35), RoundedCornerShape(12.dp))
                                            .widthIn(min = 315.dp, max = 355.dp)
                                    ) {
                                        permissionsToDisplay.forEach { mode ->
                                            val isSelected = mode.id == activePermission.id
                                            PermissionMenuItem(
                                                mode = mode,
                                                isSelected = isSelected,
                                                onClick = {
                                                    onPermissionSelected(mode)
                                                    showPermissionMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(2.dp))

                                // 4. Borderless compact Voice Mic button (28dp, Outlined icon)
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

                                // 5. Compact 28dp circular Send / Stop button (strictly 28x28 circle)
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
