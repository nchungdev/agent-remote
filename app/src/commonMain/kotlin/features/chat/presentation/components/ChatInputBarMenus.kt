package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.AgentModel
import app.duinch.agentremote.core.domain.model.EngineProject
import app.duinch.agentremote.core.domain.model.PermissionIconType
import app.duinch.agentremote.core.domain.model.PermissionMode
import app.duinch.agentremote.features.chat.presentation.contract.AttachedFileItem

internal data class QuickSlashAction(
    val command: String,
    val description: String,
    val replacementPrompt: String
)

internal val DEFAULT_SLASH_ACTIONS = listOf(
    QuickSlashAction("/plan", "Lập kế hoạch chi tiết trước khi viết code", "/plan "),
    QuickSlashAction("/review", "Kiểm tra git diff và rà soát lỗi bảo mật / kiến trúc", "Kiểm tra git diff hiện tại và review chi tiết các thay đổi."),
    QuickSlashAction("/test", "Chạy toàn bộ unit test và báo cáo kết quả", "Chạy bộ unit test và báo cáo kết quả."),
    QuickSlashAction("/status", "Kiểm tra trạng thái Git repository", "Kiểm tra git status & các thay đổi gần đây."),
    QuickSlashAction("/compact", "Tóm tắt ngữ cảnh phiên hội thoại hiện tại", "Tóm tắt ngắn gọn các bước đã thực hiện trong phiên này."),
    QuickSlashAction("/clear", "Làm mới khung hội thoại để bắt đầu tác vụ mới", "/clear")
)

@Composable
internal fun ChatInputAutocompletePopup(
    inputText: String,
    matchingSlashActions: List<QuickSlashAction>,
    matchingProjects: List<EngineProject>,
    onInputChanged: (String) -> Unit,
    onProjectMentionSelected: (EngineProject) -> Unit
) {
    if (matchingSlashActions.isEmpty() && matchingProjects.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF18191E),
        border = BorderStroke(1.dp, Color(0xFF2C2D35)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            matchingSlashActions.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onInputChanged(action.replacementPrompt) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        text = action.command,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = Color(0xFFF3F4F6)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = action.description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color(0xFF8A8D96),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            matchingProjects.forEach { proj ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val prefix = inputText.substringBeforeLast("@")
                            onInputChanged("$prefix@${proj.name} ")
                            onProjectMentionSelected(proj)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = Color(0xFF9EA0A8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "@${proj.name}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = Color(0xFFF3F4F6)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = proj.folders.firstOrNull().orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp
                        ),
                        color = Color(0xFF8A8D96),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun AttachedFilesStrip(
    attachedFiles: List<AttachedFileItem>,
    isUploadingAttachment: Boolean,
    onRemoveAttachment: (String) -> Unit
) {
    AttachedFilesPreviewStrip(
        attachedFiles = attachedFiles,
        isUploadingAttachment = isUploadingAttachment,
        onRemoveAttachment = onRemoveAttachment
    )
}

internal fun permissionOutlinedVector(type: PermissionIconType): ImageVector = when (type) {
    PermissionIconType.EYE -> Icons.Outlined.Visibility
    PermissionIconType.LOCK -> Icons.Outlined.Lock
    PermissionIconType.SHIELD -> Icons.Outlined.Shield
    PermissionIconType.TURBO -> Icons.Outlined.GppMaybe
}

internal fun splitModelName(rawName: String): Pair<String, String?> {
    val parenStart = rawName.indexOf('(')
    val parenEnd = rawName.lastIndexOf(')')
    if (parenStart > 0 && parenEnd > parenStart) {
        val base = rawName.substring(0, parenStart).trim()
        val variant = rawName.substring(parenStart + 1, parenEnd).trim()
        return base to variant.takeIf { it.isNotEmpty() }
    }
    for (suffix in listOf(" Medium", " High", " Low", " Thinking", " Flash")) {
        if (rawName.endsWith(suffix, ignoreCase = true)) {
            val base = rawName.substring(0, rawName.length - suffix.length).trim()
            return base to suffix.trim()
        }
    }
    return rawName to null
}

@Composable
internal fun PermissionMenuItem(
    mode: PermissionMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                imageVector = permissionOutlinedVector(mode.iconType),
                contentDescription = null,
                tint = if (isSelected) Color(0xFFF3F4F6) else Color(0xFF9EA0A8),
                modifier = Modifier.size(18.dp)
            )
        },
        text = {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.5.sp
                    ),
                    color = if (isSelected) Color(0xFFF3F4F6) else Color(0xFFD4D6DC)
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = Color(0xFF8A8D96),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingIcon = {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = Color(0xFFF3F4F6),
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        onClick = onClick
    )
}

@Composable
internal fun ModelMenuItem(
    model: AgentModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (baseName, variantName) = splitModelName(model.displayName)
    val low = model.id.lowercase()
    val isQuotaLimited = low.contains("claude") || low.contains("gpt")
    val isNew = low.contains("argon") || low.contains("opus") || low.contains("sonnet")
    val isNotice = low.contains("gpt")

    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = baseName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 13.5.sp
                    ),
                    color = if (isSelected) Color(0xFFF3F4F6) else Color(0xFFD4D6DC)
                )
                if (variantName != null) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = variantName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color(0xFF8A8D96)
                    )
                }
                if (isQuotaLimited) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = "Hạn mức",
                        tint = Color(0xFFE5A93C),
                        modifier = Modifier.size(13.dp)
                    )
                }
                if (isNew) {
                    Spacer(modifier = Modifier.width(6.dp))
                    PillBadge("New")
                }
                if (isNotice) {
                    Spacer(modifier = Modifier.width(6.dp))
                    PillBadge("Notice ⓘ")
                }
            }
        },
        trailingIcon = {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Đang chọn",
                    tint = Color(0xFFF3F4F6),
                    modifier = Modifier.size(15.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF5A5B64),
                    modifier = Modifier.size(14.dp)
                )
            }
        },
        onClick = onClick
    )
}

@Composable
internal fun ViewUsageMenuItem(
    onClick: () -> Unit
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = null,
                tint = Color(0xFFCC785C),
                modifier = Modifier.size(16.dp)
            )
        },
        text = {
            Text(
                text = "View Usage",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp
                ),
                color = Color(0xFFD4D6DC)
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF6B6D76),
                modifier = Modifier.size(14.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun PillBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF282932),
        border = BorderStroke(0.5.dp, Color(0xFF383A46))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color(0xFFB0B3BE),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
